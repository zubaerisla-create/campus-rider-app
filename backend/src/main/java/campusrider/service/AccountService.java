package campusrider.service;

import campusrider.dto.DriverProfileDto;
import campusrider.dto.DriverRegisterRequest;
import campusrider.dto.PassengerProfileDto;
import campusrider.dto.PassengerRegisterRequest;
import campusrider.dto.SessionUserDto;
import campusrider.dto.UpdateDriverProfileRequest;
import campusrider.dto.UpdatePassengerProfileRequest;
import campusrider.exception.BadRequestException;
import campusrider.exception.ConflictException;
import campusrider.exception.NotFoundException;
import campusrider.model.DriverAccount;
import campusrider.model.PassengerAccount;
import campusrider.model.PassengerType;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.DriverRepository;
import campusrider.repository.PassengerRepository;
import campusrider.repository.RickshawRepository;
import campusrider.repository.RideRepository;
import campusrider.security.AuthUser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Passenger and driver accounts: registration, credential checks and profiles. */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final PassengerRepository passengers;
    private final DriverRepository drivers;
    private final RickshawRepository rickshaws;
    private final RideRepository rides;
    private final RouteService routes;
    private final PasswordEncoder encoder;
    private final RideLock lock;
    /** Compared against when the username does not exist, so both cases take the same time. */
    private final String dummyHash;

    public AccountService(PassengerRepository passengers, DriverRepository drivers, RickshawRepository rickshaws,
                          RideRepository rides, RouteService routes, PasswordEncoder encoder, RideLock lock) {
        this.passengers = passengers;
        this.drivers = drivers;
        this.rickshaws = rickshaws;
        this.rides = rides;
        this.routes = routes;
        this.encoder = encoder;
        this.lock = lock;
        this.dummyHash = encoder.encode("campus-rider-timing-guard");
    }

    public static boolean isBcryptHash(String value) {
        return value != null && BCRYPT.matcher(value).matches();
    }

    /**
     * The desktop version stored passwords in plain text. Any stored value that is
     * not already a BCrypt hash is treated as a plain-text password and replaced by
     * its hash, so existing accounts (demo password 1234) keep working.
     */
    @PostConstruct
    synchronized void migratePlaintextPasswords() {
        int migrated = 0;
        for (PassengerAccount p : passengers.findAll()) {
            if (!isBcryptHash(p.getPasswordHash())) {
                p.setPasswordHash(encoder.encode(p.getPasswordHash()));
                migrated++;
            }
        }
        if (migrated > 0) {
            passengers.flush();
        }
        int migratedDrivers = 0;
        for (DriverAccount d : drivers.findAll()) {
            if (!isBcryptHash(d.getPasswordHash())) {
                d.setPasswordHash(encoder.encode(d.getPasswordHash()));
                migratedDrivers++;
            }
        }
        if (migratedDrivers > 0) {
            drivers.flush();
        }
        if (migrated + migratedDrivers > 0) {
            log.info("Migrated {} passenger and {} driver plain-text password(s) to BCrypt hashes.",
                    migrated, migratedDrivers);
        }
    }

    // ------------------------------------------------------------------ login

    /** Verifies credentials; empty when the username or password is wrong. */
    public Optional<AuthUser> authenticate(AuthUser.Role role, String username, String password) {
        String u = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String pw = password == null ? "" : password;
        if (role == AuthUser.Role.PASSENGER) {
            Optional<PassengerAccount> p = passengers.findByUsername(u);
            boolean ok = encoder.matches(pw, p.map(PassengerAccount::getPasswordHash).orElse(dummyHash));
            return ok && p.isPresent() ? Optional.of(new AuthUser(role, p.get().getId(), p.get().getUsername()))
                    : Optional.empty();
        }
        Optional<DriverAccount> d = drivers.findByUsername(u);
        boolean ok = encoder.matches(pw, d.map(DriverAccount::getPasswordHash).orElse(dummyHash));
        return ok && d.isPresent() ? Optional.of(new AuthUser(role, d.get().getId(), d.get().getUsername()))
                : Optional.empty();
    }

    public SessionUserDto sessionUser(AuthUser user) {
        if (user.role() == AuthUser.Role.PASSENGER) {
            PassengerAccount p = requirePassenger(user.id());
            return new SessionUserDto("PASSENGER", p.getId(), p.getUsername(), p.getFullName(), p.getPhone(),
                    p.getPassengerType().name(), null);
        }
        DriverAccount d = requireDriver(user.id());
        return new SessionUserDto("DRIVER", d.getId(), d.getUsername(), d.getDriverName(), d.getPhone(),
                null, d.getRickshawId());
    }

    // ----------------------------------------------------------- registration

    public synchronized AuthUser registerPassenger(PassengerRegisterRequest req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (passengers.usernameExists(username)) {
            throw new ConflictException("Username '" + username + "' is already taken.");
        }
        PassengerType type = req.passengerType() == null || req.passengerType().isBlank()
                ? PassengerType.STUDENT : PassengerType.valueOf(req.passengerType().trim().toUpperCase(Locale.ROOT));
        PassengerAccount account = new PassengerAccount(passengers.nextId(), username,
                encoder.encode(req.password()), clean(req.fullName()), type, req.phone().trim());
        passengers.add(account);
        return new AuthUser(AuthUser.Role.PASSENGER, account.getId(), username);
    }

    /** Creates the driver account and their rickshaw (offline until the driver goes online). */
    public synchronized AuthUser registerDriver(DriverRegisterRequest req) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        if (drivers.usernameExists(username)) {
            throw new ConflictException("Username '" + username + "' is already taken.");
        }
        String start = req.startLocationId().trim();
        if (!routes.isRoutable(start)) {
            throw new BadRequestException("Choose a valid starting stand on campus.");
        }
        return lock.call(() -> {
            Rickshaw rickshaw = new Rickshaw(rickshaws.nextId(), clean(req.driverName()), start, false);
            rickshaw.setPlateNumber(req.plateNumber().trim().toUpperCase(Locale.ROOT));
            rickshaw.setVehicleType(vehicleType(req.vehicleType()));
            rickshaws.add(rickshaw);
            DriverAccount account = new DriverAccount(drivers.nextId(), username, encoder.encode(req.password()),
                    clean(req.driverName()), rickshaw.getId(), req.phone().trim());
            drivers.add(account);
            return new AuthUser(AuthUser.Role.DRIVER, account.getId(), username);
        });
    }

    // ---------------------------------------------------------------- profiles

    public PassengerProfileDto passengerProfile(String passengerId) {
        PassengerAccount p = requirePassenger(passengerId);
        int completed = (int) rides.findByPassenger(passengerId).stream()
                .filter(r -> r.getStatus() == RideStatus.COMPLETED).count();
        return new PassengerProfileDto(p.getId(), p.getUsername(), p.getFullName(), p.getPhone(),
                p.getPassengerType().name(), completed);
    }

    public synchronized PassengerProfileDto updatePassenger(String passengerId, UpdatePassengerProfileRequest req) {
        PassengerAccount p = requirePassenger(passengerId);
        String newHash = checkPasswordChange(p.getPasswordHash(), req.currentPassword(), req.newPassword());
        p.setFullName(clean(req.fullName()));
        p.setPhone(req.phone().trim());
        if (newHash != null) {
            p.setPasswordHash(newHash);
        }
        passengers.flush();
        return passengerProfile(passengerId);
    }

    public DriverProfileDto driverProfile(String driverId) {
        DriverAccount d = requireDriver(driverId);
        Rickshaw r = rickshaws.findById(d.getRickshawId()).orElseThrow();
        int completed = (int) rides.findByRickshaw(r.getId()).stream()
                .filter(x -> x.getStatus() == RideStatus.COMPLETED).count();
        return new DriverProfileDto(d.getId(), d.getUsername(), d.getDriverName(), d.getPhone(), r.getId(),
                r.getPlateNumber(), r.getVehicleType(), completed);
    }

    public synchronized DriverProfileDto updateDriver(String driverId, UpdateDriverProfileRequest req) {
        DriverAccount d = requireDriver(driverId);
        String newHash = checkPasswordChange(d.getPasswordHash(), req.currentPassword(), req.newPassword());
        lock.run(() -> {
            Rickshaw r = rickshaws.findById(d.getRickshawId()).orElseThrow();
            d.setDriverName(clean(req.driverName()));
            d.setPhone(req.phone().trim());
            if (newHash != null) {
                d.setPasswordHash(newHash);
            }
            r.setDriverName(d.getDriverName());
            r.setPlateNumber(req.plateNumber().trim().toUpperCase(Locale.ROOT));
            r.setVehicleType(vehicleType(req.vehicleType()));
            drivers.flush();
            rickshaws.flush();
        });
        return driverProfile(driverId);
    }

    public String rickshawIdOf(String driverId) {
        return requireDriver(driverId).getRickshawId();
    }

    /** Returns the new hash, or null when no password change was requested. */
    private String checkPasswordChange(String currentHash, String current, String next) {
        if (next == null || next.isEmpty()) {
            return null;
        }
        if (next.length() < 6) {
            throw new BadRequestException("The new password must be at least 6 characters.");
        }
        if (current == null || !encoder.matches(current, currentHash)) {
            throw new BadRequestException("Your current password is not correct.");
        }
        return encoder.encode(next);
    }

    private PassengerAccount requirePassenger(String id) {
        return passengers.findById(id).orElseThrow(() -> new NotFoundException("Passenger account not found."));
    }

    private DriverAccount requireDriver(String id) {
        return drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver account not found."));
    }

    private static String vehicleType(String value) {
        return value == null || value.isBlank() ? "ELECTRIC" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
