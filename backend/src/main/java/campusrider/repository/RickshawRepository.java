package campusrider.repository;

import campusrider.model.Rickshaw;
import campusrider.service.RouteService;
import campusrider.storage.CsvStorage;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rickshaws, kept in a LinkedHashMap (O(1) lookup by id, stable file order).
 * Rows that point at an unknown campus location are skipped (and the file backed up).
 */
@Repository
public class RickshawRepository {
    private final CsvStorage storage;
    private final Map<String, Rickshaw> byId = new LinkedHashMap<>();

    public RickshawRepository(CsvStorage storage, RouteService routes) throws IOException {
        this.storage = storage;
        boolean skipped = false;
        for (Rickshaw r : storage.loadRickshaws()) {
            if (!routes.isValidLocation(r.getLocationId())) {
                storage.addWarning("Skipped rickshaw " + r.getId() + ": unknown location " + r.getLocationId());
                skipped = true;
                continue;
            }
            if (!routes.isValidLocation(r.getTargetDestinationId())) {
                r.setTargetDestinationId("");
            }
            byId.put(r.getId(), r);
        }
        if (skipped) {
            storage.backup(CsvStorage.RICKSHAWS_FILE);
        }
    }

    public synchronized Optional<Rickshaw> findById(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public synchronized List<Rickshaw> findAll() {
        return new ArrayList<>(byId.values());
    }

    /** Next free id in the JU-RICK-101, JU-RICK-102, ... sequence. */
    public synchronized String nextId() {
        int n = 101 + byId.size();
        while (byId.containsKey("JU-RICK-" + n)) {
            n++;
        }
        return "JU-RICK-" + n;
    }

    public synchronized Rickshaw add(Rickshaw rickshaw) {
        if (byId.containsKey(rickshaw.getId())) {
            throw new IllegalArgumentException("Duplicate rickshaw " + rickshaw.getId());
        }
        byId.put(rickshaw.getId(), rickshaw);
        flush();
        return rickshaw;
    }

    public synchronized void flush() {
        try {
            storage.saveRickshaws(new ArrayList<>(byId.values()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save rickshaws.csv: " + e.getMessage(), e);
        }
    }
}
