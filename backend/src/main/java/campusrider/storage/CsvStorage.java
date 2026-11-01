package campusrider.storage;

import campusrider.model.DriverAccount;
import campusrider.model.Location;
import campusrider.model.PassengerAccount;
import campusrider.model.PassengerType;
import campusrider.model.RidePassengerRecord;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.model.Road;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The single place that reads and writes the application's CSV files.
 *
 *   locations.csv       id,name,category,rickshawStand,aliases,x,y        (aliases separated by ';')
 *   roads.csv           from,to,distanceMeters,roadName
 *   rickshaws.csv       id,driverName,locationId,status,targetDestinationId,online,blockedSeats,plateNumber,vehicleType
 *   passengers.csv      id,username,passwordHash,fullName,passengerType,phone
 *   drivers.csv         id,username,passwordHash,driverName,rickshawId,phone
 *   rides.csv           id,passengerId,passengerName,passengerType,pickupId,destinationId,distanceMeters,
 *                       status,rickshawId,seats,emergency,offeredRickshawId,preferredRickshawId,offeredAt,
 *                       detourMeters,fare,shared,requestedAt,updatedAt,rejectedBy,statusNote
 *   ride_passengers.csv rideId,passengerId,passengerName,rickshawId,pickupId,destinationId,seatsBooked,detourMeters,status
 *
 * Rules:
 * - Columns are read by header name, so files written by the older desktop
 *   version (fewer columns, "password" instead of "passwordHash", MATCHED /
 *   IN_PROGRESS statuses) still load.
 * - Quoting and escaping follow RFC 4180 ({@link CsvCodec}).
 * - A missing file is created from the sample data bundled in the jar
 *   (rides.csv and ride_passengers.csv start with only the header).
 * - A bad or duplicate row is skipped and reported in getWarnings(); the
 *   original file is copied to "<name>.bak" so data is never silently lost.
 * - Saving writes "<name>.tmp", forces it to disk, then atomically renames it
 *   over the real file, so a crash mid-save cannot leave a half-written file.
 * - All reads and writes are synchronized on this object, and lockDataDirectory()
 *   takes an OS file lock so two servers cannot write the same folder at once.
 * - CSV files are never served to the browser; only the REST API reads them.
 */
public class CsvStorage implements Closeable {
    public static final String LOCATIONS_FILE = "locations.csv";
    public static final String ROADS_FILE = "roads.csv";
    public static final String RICKSHAWS_FILE = "rickshaws.csv";
    public static final String RIDES_FILE = "rides.csv";
    public static final String PASSENGERS_FILE = "passengers.csv";
    public static final String DRIVERS_FILE = "drivers.csv";
    public static final String RIDE_PASSENGERS_FILE = "ride_passengers.csv";

    static final List<String> LOCATIONS_HEADER =
            List.of("id", "name", "category", "rickshawStand", "aliases", "x", "y");
    static final List<String> RICKSHAWS_HEADER = List.of("id", "driverName", "locationId", "status",
            "targetDestinationId", "online", "blockedSeats", "plateNumber", "vehicleType");
    static final List<String> PASSENGERS_HEADER =
            List.of("id", "username", "passwordHash", "fullName", "passengerType", "phone");
    static final List<String> DRIVERS_HEADER =
            List.of("id", "username", "passwordHash", "driverName", "rickshawId", "phone");
    static final List<String> RIDES_HEADER = List.of("id", "passengerId", "passengerName", "passengerType",
            "pickupId", "destinationId", "distanceMeters", "status", "rickshawId", "seats", "emergency",
            "offeredRickshawId", "preferredRickshawId", "offeredAt", "detourMeters", "fare", "shared",
            "requestedAt", "updatedAt", "rejectedBy", "statusNote");
    static final List<String> RIDE_PASSENGERS_HEADER = List.of("rideId", "passengerId", "passengerName",
            "rickshawId", "pickupId", "destinationId", "seatsBooked", "detourMeters", "status");

    /** Legacy desktop rides.csv: these columns had no header name. */
    private static final List<String> LEGACY_RIDE_EXTRA_COLUMNS = List.of("seats", "offeredRickshawId");

    private final Path dataDir;
    private final List<String> warnings = new ArrayList<>();
    private boolean usingSampleCampus = false;
    private FileChannel lockChannel;
    private FileLock lock;

    public CsvStorage(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
    }

    public Path getDataDir() {
        return dataDir;
    }

    /** Problems found while loading (skipped rows, missing files, ...). */
    public synchronized List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public synchronized void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Takes an exclusive OS-level lock on data/.campusrider.lock. A second server
     * pointed at the same folder fails fast instead of overwriting the first one's data.
     */
    public synchronized void lockDataDirectory() throws IOException {
        Files.createDirectories(dataDir);
        lockChannel = FileChannel.open(dataDir.resolve(".campusrider.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        }
        if (lock == null) {
            lockChannel.close();
            throw new IllegalStateException("Another Campus Rider server is already using " + dataDir
                    + ". Stop it first, or set campusrider.data-dir to a different folder.");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (lock != null) {
            lock.release();
            lock = null;
        }
        if (lockChannel != null) {
            lockChannel.close();
            lockChannel = null;
        }
    }

    // ---------------------------------------------------------------- loading

    public synchronized List<Location> loadLocations() throws IOException {
        Table table = readTable(LOCATIONS_FILE);
        List<Location> locations = parseLocations(table, LOCATIONS_FILE);
        if (locations.isEmpty()) {
            warnings.add(LOCATIONS_FILE + " has no valid locations. Using the built-in sample campus instead.");
            locations = parseLocations(Table.of(readSample(LOCATIONS_FILE)), "sample " + LOCATIONS_FILE);
            usingSampleCampus = true;
            return locations;
        }
        // Older files have no map coordinates: copy them from the bundled sample by id.
        if (!table.has("x") || !table.has("y")) {
            Map<String, Location> sample = new HashMap<>();
            for (Location l : parseLocations(Table.of(readSample(LOCATIONS_FILE)), "sample")) {
                sample.put(l.getId(), l);
            }
            List<Location> upgraded = new ArrayList<>();
            for (Location l : locations) {
                Location s = sample.get(l.getId());
                upgraded.add(s != null && s.hasCoordinates() ? l.withCoordinates(s.getX(), s.getY()) : l);
            }
            locations = upgraded;
            if (table.skipped == 0) {
                saveLocations(locations);
                warnings.add(LOCATIONS_FILE + ": added map coordinates (x,y) from the sample campus.");
            }
        }
        return locations;
    }

    /** Call after loadLocations(). Uses the sample roads if the sample locations were used, so the ids match. */
    public synchronized List<Road> loadRoads() throws IOException {
        if (usingSampleCampus) {
            return parseRoads(Table.of(readSample(ROADS_FILE)), "sample " + ROADS_FILE);
        }
        return parseRoads(readTable(ROADS_FILE), ROADS_FILE);
    }

    public synchronized List<Rickshaw> loadRickshaws() throws IOException {
        Table table = readTable(RICKSHAWS_FILE);
        List<Rickshaw> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            String id = row.get("id");
            if (id.isEmpty() || row.get("driverName").isEmpty() || row.get("locationId").isEmpty() || !ids.add(id)) {
                skipped++;
                continue;
            }
            String status = row.get("status").toUpperCase(Locale.ROOT);
            // Older files had no "online" column: every rickshaw listed there was in service.
            boolean online = row.has("online") ? Boolean.parseBoolean(row.get("online")) : !"OFFLINE".equals(status);
            Rickshaw r = new Rickshaw(id, row.get("driverName"), row.get("locationId"), online);
            r.setTargetDestinationId(row.get("targetDestinationId"));
            r.setBlockedSeats(parseInt(row.get("blockedSeats"), 0));
            r.setPlateNumber(row.get("plateNumber"));
            r.setVehicleType(row.get("vehicleType"));
            list.add(r);
        }
        reportSkipped(RICKSHAWS_FILE, skipped);
        return list;
    }

    public synchronized List<PassengerAccount> loadPassengers() throws IOException {
        Table table = readTable(PASSENGERS_FILE);
        List<PassengerAccount> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            try {
                String id = row.get("id");
                String username = row.get("username").toLowerCase(Locale.ROOT);
                String password = row.get("passwordHash", "password");
                if (id.isEmpty() || username.isEmpty() || password.isEmpty() || row.get("fullName").isEmpty()) {
                    throw new IllegalArgumentException("missing column");
                }
                if (!ids.add(id) || !usernames.add(username)) {
                    throw new IllegalArgumentException("duplicate");
                }
                PassengerType type = PassengerType.valueOf(row.get("passengerType").toUpperCase(Locale.ROOT));
                list.add(new PassengerAccount(id, username, password, row.get("fullName"), type, row.get("phone")));
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        reportSkipped(PASSENGERS_FILE, skipped);
        return list;
    }

    public synchronized List<DriverAccount> loadDrivers() throws IOException {
        Table table = readTable(DRIVERS_FILE);
        List<DriverAccount> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            String id = row.get("id");
            String username = row.get("username").toLowerCase(Locale.ROOT);
            String password = row.get("passwordHash", "password");
            if (id.isEmpty() || username.isEmpty() || password.isEmpty() || row.get("driverName").isEmpty()
                    || row.get("rickshawId").isEmpty() || !ids.add(id) || !usernames.add(username)) {
                skipped++;
                continue;
            }
            list.add(new DriverAccount(id, username, password, row.get("driverName"),
                    row.get("rickshawId"), row.get("phone")));
        }
        reportSkipped(DRIVERS_FILE, skipped);
        return list;
    }

    public synchronized List<RideRequest> loadRides() throws IOException {
        Table table = readTable(RIDES_FILE);
        List<RideRequest> rides = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            try {
                int id = Integer.parseInt(row.get("id"));
                double distance = Double.parseDouble(row.get("distanceMeters"));
                int seats = parseInt(row.get("seats"), 1);
                if (id <= 0 || row.get("passengerName").isEmpty() || !(distance >= 0)
                        || seats < 1 || seats > Rickshaw.DEFAULT_MAX_SEATS || !ids.add(id)) {
                    throw new IllegalArgumentException("invalid value");
                }
                long requestedAt = parseLong(row.get("requestedAt"), 0L);
                RideRequest ride = new RideRequest(id, row.get("passengerId"), row.get("passengerName"),
                        PassengerType.valueOf(row.get("passengerType").toUpperCase(Locale.ROOT)),
                        row.get("pickupId"), row.get("destinationId"), distance, seats,
                        Boolean.parseBoolean(row.get("emergency")), RideStatus.fromCsv(row.get("status")),
                        requestedAt);
                ride.setRickshawId(row.get("rickshawId"));
                ride.setOfferedRickshawId(row.get("offeredRickshawId"));
                ride.setPreferredRickshawId(row.get("preferredRickshawId"));
                ride.setOfferedAt(parseLong(row.get("offeredAt"), 0L));
                ride.setDetourMeters(parseDouble(row.get("detourMeters"), 0.0));
                ride.setFare(parseInt(row.get("fare"), 0));
                ride.setShared(Boolean.parseBoolean(row.get("shared")));
                ride.setUpdatedAt(parseLong(row.get("updatedAt"), requestedAt));
                ride.setStatusNote(row.get("statusNote"));
                for (String r : row.get("rejectedBy").split(";")) {
                    ride.addRejectedBy(r.trim());
                }
                rides.add(ride);
            } catch (IllegalArgumentException e) { // also covers NumberFormatException
                skipped++;
            }
        }
        reportSkipped(RIDES_FILE, skipped);
        return rides;
    }

    private List<Location> parseLocations(Table table, String source) throws IOException {
        List<Location> locations = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            String id = row.get("id");
            if (id.isEmpty() || row.get("name").isEmpty() || !ids.add(id)) {
                skipped++;
                continue;
            }
            List<String> aliases = new ArrayList<>();
            for (String alias : row.get("aliases").split(";")) {
                if (!alias.isBlank()) {
                    aliases.add(alias.trim());
                }
            }
            double x = parseDouble(row.get("x"), Double.NaN);
            double y = parseDouble(row.get("y"), Double.NaN);
            locations.add(new Location(id, row.get("name"), row.get("category"),
                    Boolean.parseBoolean(row.get("rickshawStand")), aliases, x, y));
        }
        reportSkipped(source, skipped);
        return locations;
    }

    private List<Road> parseRoads(Table table, String source) throws IOException {
        List<Road> roads = new ArrayList<>();
        int skipped = table.skipped;
        for (Record row : table.records) {
            try {
                if (row.get("from").isEmpty() || row.get("to").isEmpty()) {
                    throw new IllegalArgumentException("missing column");
                }
                roads.add(new Road(row.get("from"), row.get("to"),
                        Double.parseDouble(row.get("distanceMeters")), row.get("roadName")));
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }
        reportSkipped(source, skipped);
        return roads;
    }

    /** Called when rows were skipped: keep a copy of the original file before it can be overwritten. */
    private void reportSkipped(String fileName, int skipped) throws IOException {
        if (skipped > 0) {
            warnings.add(fileName + ": skipped " + skipped + " invalid or duplicate row(s).");
            backup(fileName);
        }
    }

    /** Copies data/<name> to data/<name>.bak (if the file exists). */
    public synchronized void backup(String fileName) throws IOException {
        Path file = dataDir.resolve(fileName);
        if (Files.exists(file)) {
            Path copy = dataDir.resolve(fileName + ".bak");
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            warnings.add("A copy of the original " + fileName + " was saved as " + copy.getFileName() + ".");
        }
    }

    /** Reads a file from the data folder, creating it first if it does not exist. */
    private Table readTable(String fileName) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            String initial;
            if (fileName.equals(RIDES_FILE)) {
                initial = CsvCodec.formatRow(RIDES_HEADER) + "\n";
            } else if (fileName.equals(RIDE_PASSENGERS_FILE)) {
                initial = CsvCodec.formatRow(RIDE_PASSENGERS_HEADER) + "\n";
            } else {
                initial = readSample(fileName);
            }
            writeAtomically(file, initial);
            warnings.add(fileName + " was missing and has been created.");
        }
        Table table = Table.of(Files.readString(file, StandardCharsets.UTF_8));
        if (fileName.equals(RIDES_FILE) && !table.has("seats")) {
            table = table.withExtraColumns(LEGACY_RIDE_EXTRA_COLUMNS);
        }
        return table;
    }

    /** Reads one of the sample files packaged in src/main/resources/sample. */
    private static String readSample(String fileName) throws IOException {
        try (InputStream in = CsvStorage.class.getResourceAsStream("/sample/" + fileName)) {
            if (in == null) {
                throw new IOException("Sample file is missing from the application: " + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ----------------------------------------------------------------- saving

    public synchronized void saveLocations(List<Location> locations) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Location l : locations) {
            rows.add(List.of(l.getId(), l.getName(), l.getCategory(), String.valueOf(l.isRickshawStand()),
                    String.join(";", l.getAliases()),
                    l.hasCoordinates() ? String.valueOf(l.getX()) : "",
                    l.hasCoordinates() ? String.valueOf(l.getY()) : ""));
        }
        write(LOCATIONS_FILE, LOCATIONS_HEADER, rows);
    }

    public synchronized void saveRickshaws(List<Rickshaw> rickshaws) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Rickshaw r : rickshaws) {
            String status = !r.isOnline() ? "OFFLINE" : r.getBookedSeats() > 0 ? "BUSY" : "AVAILABLE";
            rows.add(List.of(r.getId(), r.getDriverName(), r.getLocationId(), status,
                    r.getTargetDestinationId(), String.valueOf(r.isOnline()), String.valueOf(r.getBlockedSeats()),
                    r.getPlateNumber(), r.getVehicleType()));
        }
        write(RICKSHAWS_FILE, RICKSHAWS_HEADER, rows);
    }

    public synchronized void savePassengers(List<PassengerAccount> passengers) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (PassengerAccount p : passengers) {
            rows.add(List.of(p.getId(), p.getUsername(), p.getPasswordHash(), p.getFullName(),
                    p.getPassengerType().name(), p.getPhone()));
        }
        write(PASSENGERS_FILE, PASSENGERS_HEADER, rows);
    }

    public synchronized void saveDrivers(List<DriverAccount> drivers) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (DriverAccount d : drivers) {
            rows.add(List.of(d.getId(), d.getUsername(), d.getPasswordHash(), d.getDriverName(),
                    d.getRickshawId(), d.getPhone()));
        }
        write(DRIVERS_FILE, DRIVERS_HEADER, rows);
    }

    public synchronized void saveRides(List<RideRequest> rides) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (RideRequest r : rides) {
            rows.add(List.of(String.valueOf(r.getId()), r.getPassengerId(), r.getPassengerName(),
                    r.getPassengerType().name(), r.getPickupId(), r.getDestinationId(),
                    String.valueOf(r.getDistanceMeters()), r.getStatus().name(), r.getRickshawId(),
                    String.valueOf(r.getSeatsRequested()), String.valueOf(r.isEmergency()),
                    r.getOfferedRickshawId(), r.getPreferredRickshawId(), String.valueOf(r.getOfferedAt()),
                    String.valueOf(r.getDetourMeters()), String.valueOf(r.getFare()), String.valueOf(r.isShared()),
                    String.valueOf(r.getRequestedAt()), String.valueOf(r.getUpdatedAt()),
                    String.join(";", r.getRejectedByRickshaws()), r.getStatusNote()));
        }
        write(RIDES_FILE, RIDES_HEADER, rows);
    }

    public synchronized void saveRidePassengers(List<RidePassengerRecord> records) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (RidePassengerRecord rec : records) {
            rows.add(List.of(String.valueOf(rec.getRideId()), rec.getPassengerId(), rec.getPassengerName(),
                    rec.getRickshawId(), rec.getPickupId(), rec.getDestinationId(),
                    String.valueOf(rec.getSeatsBooked()), String.valueOf(rec.getDetourMeters()),
                    rec.getStatus().name()));
        }
        write(RIDE_PASSENGERS_FILE, RIDE_PASSENGERS_HEADER, rows);
    }

    private void write(String fileName, List<String> header, List<List<String>> rows) throws IOException {
        StringBuilder text = new StringBuilder(CsvCodec.formatRow(header)).append('\n');
        for (List<String> row : rows) {
            text.append(CsvCodec.formatRow(row)).append('\n');
        }
        writeAtomically(dataDir.resolve(fileName), text.toString());
    }

    /** Writes "<file>.tmp", forces it to disk, then renames it over the real file. */
    private void writeAtomically(Path file, String content) throws IOException {
        Files.createDirectories(dataDir);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return s == null || s.isBlank() ? fallback : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------- table model

    /** A parsed CSV file: header names mapped to column indexes, plus data rows. */
    private static final class Table {
        final Map<String, Integer> columns;
        final List<Record> records;
        final int skipped;

        private Table(Map<String, Integer> columns, List<Record> records, int skipped) {
            this.columns = columns;
            this.records = records;
            this.skipped = skipped;
        }

        static Table of(String text) {
            List<List<String>> rows = CsvCodec.parse(text);
            Map<String, Integer> columns = new HashMap<>();
            List<Record> records = new ArrayList<>();
            int skipped = 0;
            if (rows.isEmpty()) {
                return new Table(columns, records, 0);
            }
            List<String> header = rows.get(0);
            for (int i = 0; i < header.size(); i++) {
                columns.put(header.get(i).trim(), i);
            }
            for (int r = 1; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                if (!row.isEmpty() && row.get(0).startsWith("#")) {
                    continue; // comment line
                }
                // A row with more fields than the header is only accepted for known legacy layouts
                // (see withExtraColumns); any row with fewer fields than required columns reads as "".
                records.add(new Record(columns, row));
            }
            return new Table(columns, records, skipped);
        }

        boolean has(String column) {
            return columns.containsKey(column);
        }

        /** Gives names to columns that legacy files wrote without a header entry. */
        Table withExtraColumns(List<String> extra) {
            Map<String, Integer> extended = new HashMap<>(columns);
            int next = columns.size();
            for (String name : extra) {
                extended.putIfAbsent(name, next++);
            }
            List<Record> rebuilt = new ArrayList<>();
            int bad = skipped;
            for (Record record : records) {
                if (record.values.size() > extended.size()) {
                    bad++;
                    continue;
                }
                rebuilt.add(new Record(extended, record.values));
            }
            return new Table(extended, rebuilt, bad);
        }
    }

    private static final class Record {
        final Map<String, Integer> columns;
        final List<String> values;

        Record(Map<String, Integer> columns, List<String> values) {
            this.columns = columns;
            this.values = values;
        }

        boolean has(String name) {
            Integer i = columns.get(name);
            return i != null && i < values.size();
        }

        /** The first of the given column names that exists, or "" when none does. */
        String get(String... names) {
            for (String name : Arrays.asList(names)) {
                Integer i = columns.get(name);
                if (i != null && i < values.size()) {
                    return values.get(i);
                }
            }
            return "";
        }
    }
}
