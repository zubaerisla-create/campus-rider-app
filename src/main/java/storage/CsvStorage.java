package storage;

import model.Location;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import model.Road;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes the application's CSV files in the data folder.
 *
 *   locations.csv  id,name,category,rickshawStand,aliases   (aliases separated by ';')
 *   roads.csv      from,to,distanceMeters,roadName
 *   rickshaws.csv  id,driverName,locationId,status
 *   rides.csv      id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId
 *
 * Rules:
 * - A missing file is created from the sample data bundled in the jar
 *   (rides.csv starts with only the header).
 * - A bad row is skipped and reported in getWarnings(); the original file is
 *   copied to "<name>.bak" so the user's data is never lost.
 * - Saving writes a temporary file first and then renames it, so a crash
 *   in the middle of saving cannot leave a half-written file.
 */
public class CsvStorage {
    public static final String LOCATIONS_FILE = "locations.csv";
    public static final String ROADS_FILE = "roads.csv";
    public static final String RICKSHAWS_FILE = "rickshaws.csv";
    public static final String RIDES_FILE = "rides.csv";

    private static final String RICKSHAWS_HEADER = "id,driverName,locationId,status";
    private static final String RIDES_HEADER =
            "id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId";

    private final Path dataDir;
    private final List<String> warnings = new ArrayList<>();
    private boolean usingSampleCampus = false;

    public CsvStorage(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path getDataDir() {
        return dataDir;
    }

    /** Problems found while loading (skipped rows, missing files, ...). */
    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    // ---------------------------------------------------------------- loading

    public List<Location> loadLocations() throws IOException {
        List<Location> locations = parseLocations(readDataFile(LOCATIONS_FILE), LOCATIONS_FILE);
        if (locations.isEmpty()) {
            warnings.add(LOCATIONS_FILE + " has no valid locations. Using the built-in sample campus instead.");
            locations = parseLocations(readSample(LOCATIONS_FILE), "sample " + LOCATIONS_FILE);
            usingSampleCampus = true;
        }
        return locations;
    }

    /** Call after loadLocations(). Uses the sample roads if the sample locations were used, so the ids match. */
    public List<Road> loadRoads() throws IOException {
        if (usingSampleCampus) {
            return parseRoads(readSample(ROADS_FILE), "sample " + ROADS_FILE);
        }
        return parseRoads(readDataFile(ROADS_FILE), ROADS_FILE);
    }

    public List<Rickshaw> loadRickshaws() throws IOException {
        List<Rickshaw> rickshaws = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(readDataFile(RICKSHAWS_FILE))) {
            if (row.length != 4 || row[0].isEmpty() || row[1].isEmpty() || row[2].isEmpty()) {
                skipped++;
                continue;
            }
            rickshaws.add(new Rickshaw(row[0], row[1], row[2], !"BUSY".equalsIgnoreCase(row[3])));
        }
        reportSkipped(RICKSHAWS_FILE, skipped);
        return rickshaws;
    }

    public List<RideRequest> loadRides() throws IOException {
        List<RideRequest> rides = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(readDataFile(RIDES_FILE))) {
            try {
                if (row.length != 8 && row.length != 9) {
                    throw new IllegalArgumentException("wrong number of columns");
                }
                int id = Integer.parseInt(row[0]);
                double distance = Double.parseDouble(row[5]);
                int seats = row.length == 9 && !row[8].isEmpty() ? Integer.parseInt(row[8]) : 1;
                if (id <= 0 || row[1].isEmpty() || !(distance >= 0) || seats < 1 || seats > Rickshaw.DEFAULT_MAX_SEATS) {
                    throw new IllegalArgumentException("invalid value");
                }
                rides.add(new RideRequest(id, row[1], PassengerType.valueOf(row[2]), row[3], row[4],
                        distance, RideStatus.valueOf(row[6]), row[7], seats));
            } catch (IllegalArgumentException e) { // also covers NumberFormatException
                skipped++;
            }
        }
        reportSkipped(RIDES_FILE, skipped);
        return rides;
    }

    private List<Location> parseLocations(List<String> lines, String source) throws IOException {
        List<Location> locations = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(lines)) {
            if (row.length < 4 || row.length > 5 || row[0].isEmpty() || row[1].isEmpty()) {
                skipped++;
                continue;
            }
            List<String> aliases = new ArrayList<>();
            if (row.length == 5) {
                for (String alias : row[4].split(";")) {
                    if (!alias.isBlank()) {
                        aliases.add(alias.trim());
                    }
                }
            }
            locations.add(new Location(row[0], row[1], row[2], Boolean.parseBoolean(row[3]), aliases));
        }
        reportSkipped(source, skipped);
        return locations;
    }

    private List<Road> parseRoads(List<String> lines, String source) throws IOException {
        List<Road> roads = new ArrayList<>();
        int skipped = 0;
        for (String[] row : rows(lines)) {
            try {
                if (row.length != 4) {
                    throw new IllegalArgumentException("wrong number of columns");
                }
                roads.add(new Road(row[0], row[1], Double.parseDouble(row[2]), row[3]));
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
            warnings.add(fileName + ": skipped " + skipped + " invalid row(s).");
            backup(fileName);
        }
    }

    /** Copies data/<name> to data/<name>.bak (if the file exists). */
    public void backup(String fileName) throws IOException {
        Path file = dataDir.resolve(fileName);
        if (Files.exists(file)) {
            Path copy = dataDir.resolve(fileName + ".bak");
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            warnings.add("A copy of the original " + fileName + " was saved as " + copy.getFileName() + ".");
        }
    }

    /** Splits each data line into trimmed columns. Skips the header, blank lines and '#' comments. */
    private static List<String[]> rows(List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            for (int c = 0; c < parts.length; c++) {
                parts[c] = parts[c].trim();
            }
            rows.add(parts);
        }
        return rows;
    }

    /** Reads a file from the data folder, creating it first if it does not exist. */
    private List<String> readDataFile(String fileName) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            List<String> initial = fileName.equals(RIDES_FILE) ? List.of(RIDES_HEADER) : readSample(fileName);
            writeLines(file, initial);
            warnings.add(fileName + " was missing and has been created.");
        }
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /** Reads one of the sample files packaged in src/main/resources/sample. */
    private static List<String> readSample(String fileName) throws IOException {
        try (InputStream in = CsvStorage.class.getResourceAsStream("/sample/" + fileName)) {
            if (in == null) {
                throw new IOException("Sample file is missing from the application: " + fileName);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ArrayList<>(Arrays.asList(text.split("\\R")));
        }
    }

    // ----------------------------------------------------------------- saving

    public void saveRickshaws(List<Rickshaw> rickshaws) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(RICKSHAWS_HEADER);
        for (Rickshaw r : rickshaws) {
            lines.add(String.join(",", r.getId(), r.getDriverName(), r.getLocationId(),
                    r.isAvailable() ? "AVAILABLE" : "BUSY"));
        }
        writeLines(dataDir.resolve(RICKSHAWS_FILE), lines);
    }

    public void saveRides(List<RideRequest> rides) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(RIDES_HEADER);
        for (RideRequest r : rides) {
            if (r.getSeatsRequested() > 1) {
                lines.add(String.join(",", String.valueOf(r.getId()), r.getPassengerName(),
                        r.getPassengerType().name(), r.getPickupId(), r.getDestinationId(),
                        String.valueOf(r.getDistanceMeters()), r.getStatus().name(), r.getRickshawId(),
                        String.valueOf(r.getSeatsRequested())));
            } else {
                lines.add(String.join(",", String.valueOf(r.getId()), r.getPassengerName(),
                        r.getPassengerType().name(), r.getPickupId(), r.getDestinationId(),
                        String.valueOf(r.getDistanceMeters()), r.getStatus().name(), r.getRickshawId()));
            }
        }
        writeLines(dataDir.resolve(RIDES_FILE), lines);
    }

    /** Writes to "<file>.tmp" and then renames it over the real file. */
    private void writeLines(Path file, List<String> lines) throws IOException {
        Files.createDirectories(dataDir);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
