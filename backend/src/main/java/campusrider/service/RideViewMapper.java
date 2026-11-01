package campusrider.service;

import campusrider.algorithms.GreedyRickshawMatcher;
import campusrider.algorithms.RideStateMachine;
import campusrider.algorithms.RouteCompatibility;
import campusrider.dto.CoPassengerDto;
import campusrider.dto.DriverInfoDto;
import campusrider.dto.LocationDto;
import campusrider.dto.PersonDto;
import campusrider.dto.RickshawOptionDto;
import campusrider.dto.RickshawPositionDto;
import campusrider.dto.RickshawStateDto;
import campusrider.dto.RideDto;
import campusrider.dto.StopDto;
import campusrider.model.DriverAccount;
import campusrider.model.Location;
import campusrider.model.PassengerAccount;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.DriverRepository;
import campusrider.repository.PassengerRepository;
import campusrider.repository.RickshawRepository;
import campusrider.repository.RideRepository;
import campusrider.tracking.RidePlanner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds API views (DTOs) from the domain model. Never exposes password hashes. */
@Component
public class RideViewMapper {
    /** Realistic campus rickshaw speed (about 12 km/h) used for displayed ETAs. */
    public static final double DISPLAY_SPEED_MPS = 3.3;

    private final RouteService routes;
    private final RickshawRepository rickshaws;
    private final PassengerRepository passengers;
    private final DriverRepository drivers;
    private final RideRepository rides;
    private final RidePlanner planner;

    public RideViewMapper(RouteService routes, RickshawRepository rickshaws, PassengerRepository passengers,
                          DriverRepository drivers, RideRepository rides, RidePlanner planner) {
        this.routes = routes;
        this.rickshaws = rickshaws;
        this.passengers = passengers;
        this.drivers = drivers;
        this.rides = rides;
        this.planner = planner;
    }

    public static int minutes(double meters) {
        if (meters <= 0 || Double.isInfinite(meters)) {
            return meters == 0 ? 0 : -1;
        }
        return (int) Math.max(1, Math.round(meters / DISPLAY_SPEED_MPS / 60.0));
    }

    public LocationDto location(String id) {
        Location l = routes.getLocation(id);
        return l == null ? null : LocationDto.of(l, routes.isRoutable(id));
    }

    public RickshawPositionDto position(Rickshaw r) {
        Location from = routes.getLocation(r.getLocationId());
        Location to = r.isMoving() ? routes.getLocation(r.getMovingToId()) : from;
        double t = r.isMoving() ? r.getSegmentProgress() : 0.0;
        double x = from == null ? 0.5 : from.getX() + (to.getX() - from.getX()) * t;
        double y = from == null ? 0.5 : from.getY() + (to.getY() - from.getY()) * t;
        return new RickshawPositionDto(r.getId(), r.getLocationId(), r.getMovingToId(), t, x, y, r.isOnline(),
                r.getAvailableSeats(), r.getMaxSeats(), r.isAutoDrive());
    }

    public RickshawStateDto rickshawState(Rickshaw r) {
        String target = r.getTargetDestinationId();
        return new RickshawStateDto(r.getId(), r.getPlateNumber(), r.getVehicleType(), r.isOnline(),
                location(r.getLocationId()), target.isBlank() ? null : location(target), r.getMaxSeats(),
                r.getBookedSeats(), r.getBlockedSeats(), r.getAvailableSeats(), position(r));
    }

    public RickshawOptionDto option(RouteCompatibility.CompatibilityResult c, int seats, int tripFare, int rank) {
        Rickshaw r = c.getRickshaw();
        int onBoard = rides.findActiveByRickshaw(r.getId()).size();
        double score = GreedyRickshawMatcher.score(c, seats);
        int fare = c.isShared() ? FareCalculator.estimate(c.getPassengerTripMeters(), seats, true) : tripFare;
        return new RickshawOptionDto(r.getId(), r.getDriverName(), r.getPlateNumber(), r.getVehicleType(),
                r.getLocationId(), routes.getLocationName(r.getLocationId()),
                finite(c.getDistanceToPickupMeters()), minutes(c.getDistanceToPickupMeters()),
                r.getAvailableSeats(), r.getMaxSeats(), onBoard, c.isShared(), c.isCompatible(),
                c.isPickupOnCurrentRoute(), finite(c.getDetourMeters()), Double.isInfinite(score) ? -1 : score,
                c.getSummary(), fare, rank);
    }

    /**
     * @param forDriver true when the viewer is the driver (shows passenger contact details);
     *                  passengers see the driver's details and only first names of co-passengers.
     */
    public RideDto ride(RideRequest ride, boolean forDriver) {
        Rickshaw assigned = ride.getRickshawId().isEmpty() ? null : rickshaws.findById(ride.getRickshawId()).orElse(null);
        Rickshaw offered = ride.getStatus() == RideStatus.PENDING && ride.hasOffer()
                ? rickshaws.findById(ride.getOfferedRickshawId()).orElse(null) : null;
        Rickshaw shown = ride.getStatus().isActive() ? assigned : offered;

        RidePlanner.RidePaths paths = planner.pathsFor(ride, ride.getStatus().isActive() ? assigned : offered);
        DriverInfoDto driver = null;
        if (assigned != null && (ride.getStatus().isActive() || ride.getStatus() == RideStatus.COMPLETED)) {
            DriverAccount d = drivers.findByRickshawId(assigned.getId()).orElse(null);
            driver = new DriverInfoDto(assigned.getDriverName(), d == null ? "" : d.getPhone(), assigned.getId(),
                    assigned.getPlateNumber(), assigned.getVehicleType());
        }
        PersonDto passenger = null;
        if (forDriver) {
            PassengerAccount p = passengers.findById(ride.getPassengerId()).orElse(null);
            passenger = new PersonDto(ride.getPassengerName(), p == null ? "" : p.getPhone());
        }

        List<CoPassengerDto> co = new ArrayList<>();
        if (assigned != null && ride.getStatus().isActive()) {
            for (RideRequest other : rides.findActiveByRickshaw(assigned.getId())) {
                if (other.getId() != ride.getId()) {
                    co.add(new CoPassengerDto(firstName(other.getPassengerName()), other.getSeatsRequested(),
                            routes.getLocationName(other.getPickupId()),
                            routes.getLocationName(other.getDestinationId()), other.getStatus().name()));
                }
            }
        }
        boolean parked = assigned != null && !assigned.isMoving();
        boolean arrived = parked && ride.getStatus() == RideStatus.ACCEPTED
                && assigned.getLocationId().equals(ride.getPickupId());
        boolean atDestination = parked && ride.getStatus() == RideStatus.STARTED
                && assigned.getLocationId().equals(ride.getDestinationId());
        Set<String> next = new java.util.TreeSet<>();
        for (RideStatus s : RideStateMachine.nextStates(ride.getStatus())) {
            next.add(s.name());
        }
        return new RideDto(ride.getId(), ride.getStatus().name(), ride.getStatus().getLabel(), ride.getStatusNote(),
                location(ride.getPickupId()), location(ride.getDestinationId()), ride.getDistanceMeters(),
                ride.getSeatsRequested(), ride.getFare(), ride.isEmergency(), ride.isShared() || !co.isEmpty(),
                ride.getDetourMeters(), ride.getRequestedAt(), ride.getUpdatedAt(), passenger, driver,
                offered == null ? "" : offered.getDriverName(), shown == null ? null : position(shown),
                paths.approachPath(), paths.tripPath(), finite(paths.metersToPickup()),
                finite(paths.metersToDropoff()), co, arrived, atDestination,
                ride.getStatus() == RideStatus.PENDING || ride.getStatus() == RideStatus.ACCEPTED, next);
    }

    public StopDto stop(RouteCompatibility.Stop stop) {
        RideRequest ride = rides.findById(stop.getRideId()).orElse(null);
        return new StopDto(stop.getRideId(), stop.getType().name(), stop.getLocationId(),
                routes.getLocationName(stop.getLocationId()), ride == null ? "" : ride.getPassengerName(),
                ride == null ? 0 : ride.getSeatsRequested());
    }

    private static String firstName(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        String[] parts = n.split("\\s+");
        // Skip a leading title such as "Prof." or "Dr."
        if (parts.length > 1 && parts[0].endsWith(".")) {
            return parts[1];
        }
        return parts.length == 0 ? "" : parts[0];
    }

    private static double finite(double v) {
        return Double.isInfinite(v) || Double.isNaN(v) ? -1 : Math.round(v * 10.0) / 10.0;
    }
}
