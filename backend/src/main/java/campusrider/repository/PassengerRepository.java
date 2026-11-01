package campusrider.repository;

import campusrider.model.PassengerAccount;
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
 * Passenger accounts. Two HashMaps give O(1) lookup by id (for sessions and rides)
 * and by username (for login and duplicate checks); an ArrayList keeps file order
 * for writing passengers.csv. Every change is written through to the CSV file.
 */
@Repository
public class PassengerRepository {
    private final CsvStorage storage;
    private final List<PassengerAccount> all = new ArrayList<>();
    private final Map<String, PassengerAccount> byId = new HashMap<>();
    private final Map<String, PassengerAccount> byUsername = new HashMap<>();

    public PassengerRepository(CsvStorage storage) throws IOException {
        this.storage = storage;
        for (PassengerAccount p : storage.loadPassengers()) {
            index(p);
        }
    }

    private void index(PassengerAccount p) {
        all.add(p);
        byId.put(p.getId(), p);
        byUsername.put(p.getUsername().toLowerCase(Locale.ROOT), p);
    }

    public synchronized Optional<PassengerAccount> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized Optional<PassengerAccount> findByUsername(String username) {
        return Optional.ofNullable(username == null ? null : byUsername.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    public synchronized boolean usernameExists(String username) {
        return findByUsername(username).isPresent();
    }

    public synchronized List<PassengerAccount> findAll() {
        return new ArrayList<>(all);
    }

    /** Next id in the P-101, P-102, ... sequence that is not used yet. */
    public synchronized String nextId() {
        int n = 101 + all.size();
        while (byId.containsKey("P-" + n)) {
            n++;
        }
        return "P-" + n;
    }

    public synchronized PassengerAccount add(PassengerAccount p) {
        if (byId.containsKey(p.getId()) || usernameExists(p.getUsername())) {
            throw new IllegalArgumentException("Duplicate passenger " + p.getUsername());
        }
        index(p);
        flush();
        return p;
    }

    /** Persists changes made to an account object returned by this repository. */
    public synchronized void flush() {
        try {
            storage.savePassengers(all);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save passengers.csv: " + e.getMessage(), e);
        }
    }
}
