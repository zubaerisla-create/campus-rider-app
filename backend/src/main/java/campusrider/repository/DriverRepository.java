package campusrider.repository;

import campusrider.model.DriverAccount;
import campusrider.storage.CsvStorage;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Driver accounts. Two HashMaps give O(1) lookup by id (for sessions and rides)
 * and by username (for login and duplicate checks); an ArrayList keeps file order
 * for writing drivers.csv. Every change is written through to the CSV file.
 */
@Repository
public class DriverRepository {
    private final CsvStorage storage;
    private final List<DriverAccount> all = new ArrayList<>();
    private final Map<String, DriverAccount> byId = new HashMap<>();
    private final Map<String, DriverAccount> byUsername = new HashMap<>();

    public DriverRepository(CsvStorage storage, RickshawRepository rickshaws) throws IOException {
        this.storage = storage;
        boolean skipped = false;
        for (DriverAccount d : storage.loadDrivers()) {
            if (rickshaws.findById(d.getRickshawId()).isEmpty()) {
                storage.addWarning("Skipped driver " + d.getUsername() + ": unknown rickshaw " + d.getRickshawId());
                skipped = true;
                continue;
            }
            index(d);
        }
        if (skipped) {
            storage.backup(CsvStorage.DRIVERS_FILE);
        }
    }

    private void index(DriverAccount p) {
        all.add(p);
        byId.put(p.getId(), p);
        byUsername.put(p.getUsername().toLowerCase(Locale.ROOT), p);
    }

    public synchronized Optional<DriverAccount> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized Optional<DriverAccount> findByUsername(String username) {
        return Optional.ofNullable(username == null ? null : byUsername.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized boolean usernameExists(String username) {
        return findByUsername(username).isPresent();
    }

    public synchronized Optional<DriverAccount> findByRickshawId(String rickshawId) {
        for (DriverAccount d : all) {
            if (d.getRickshawId().equals(rickshawId)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public synchronized List<DriverAccount> findAll() {
        return new ArrayList<>(all);
    }

    /** Next id in the D-101, D-102, ... sequence that is not used yet. */
    public synchronized String nextId() {
        int n = 101 + all.size();
        while (byId.containsKey("D-" + n)) {
            n++;
        }
        return "D-" + n;
    }

    public synchronized DriverAccount add(DriverAccount p) {
        if (byId.containsKey(p.getId()) || usernameExists(p.getUsername())) {
            throw new IllegalArgumentException("Duplicate driver " + p.getUsername());
        }
        index(p);
        flush();
        return p;
    }

    /** Persists changes made to an account object returned by this repository. */
    public synchronized void flush() {
        try {
            storage.saveDrivers(all);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save drivers.csv: " + e.getMessage(), e);
        }
    }
}
