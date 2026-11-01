package campusrider.repository;

import campusrider.model.RidePassengerRecord;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.service.RouteService;
import campusrider.storage.CsvStorage;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All rides.
 *   rides            LinkedHashMap rideId -> ride (O(1) lookup, request order kept)
 *   ridesByPassenger HashMap passengerId -> ArrayList of ride ids (passenger-to-ride mapping
 *                    used for "current ride" and ride history)
 *
 * flush() writes rides.csv and the derived ride_passengers.csv seat-assignment
 * snapshot (one row per passenger that holds, or held, seats on a rickshaw).
 */
@Repository
public class RideRepository {
    private final CsvStorage storage;
    private final Map<Integer, RideRequest> rides = new LinkedHashMap<>();
    private final Map<String, List<Integer>> ridesByPassenger = new HashMap<>();
    private int nextId = 1;

    public RideRepository(CsvStorage storage, RouteService routes, RickshawRepository rickshaws) throws IOException {
        this.storage = storage;
        boolean skipped = false;
        for (RideRequest ride : storage.loadRides()) {
            String problem = null;
            if (!routes.isValidLocation(ride.getPickupId()) || !routes.isValidLocation(ride.getDestinationId())) {
                problem = "unknown location";
            } else if (ride.getStatus().isActive() && rickshaws.findById(ride.getRickshawId()).isEmpty()) {
                problem = "unknown rickshaw '" + ride.getRickshawId() + "'";
            }
            if (problem != null) {
                storage.addWarning("Skipped ride #" + ride.getId() + ": " + problem);
                skipped = true;
                continue;
            }
            index(ride);
        }
        if (skipped) {
            storage.backup(CsvStorage.RIDES_FILE);
        }
    }

    private void index(RideRequest ride) {
        rides.put(ride.getId(), ride);
        ridesByPassenger.computeIfAbsent(ride.getPassengerId(), k -> new ArrayList<>()).add(ride.getId());
        nextId = Math.max(nextId, ride.getId() + 1);
    }

    public synchronized int nextId() {
        return nextId;
    }

    public synchronized RideRequest add(RideRequest ride) {
        if (rides.containsKey(ride.getId())) {
            throw new IllegalArgumentException("Duplicate ride id " + ride.getId());
        }
        index(ride);
        return ride;
    }

    public synchronized Optional<RideRequest> findById(int id) {
        return Optional.ofNullable(rides.get(id));
    }

    public synchronized List<RideRequest> findAll() {
        return new ArrayList<>(rides.values());
    }

    public synchronized List<RideRequest> findByPassenger(String passengerId) {
        List<RideRequest> list = new ArrayList<>();
        for (Integer id : ridesByPassenger.getOrDefault(passengerId, List.of())) {
            list.add(rides.get(id));
        }
        return list;
    }

    /** Rides holding seats (ACCEPTED or STARTED) on a rickshaw, in request order. */
    public synchronized List<RideRequest> findActiveByRickshaw(String rickshawId) {
        List<RideRequest> list = new ArrayList<>();
        for (RideRequest r : rides.values()) {
            if (r.getStatus().isActive() && r.getRickshawId().equals(rickshawId)) {
                list.add(r);
            }
        }
        return list;
    }

    /** Every rickshaw id mapped to its active rides (HashMap built in one pass, O(n)). */
    public synchronized Map<String, List<RideRequest>> activeRidesByRickshaw() {
        Map<String, List<RideRequest>> map = new HashMap<>();
        for (RideRequest r : rides.values()) {
            if (r.getStatus().isActive()) {
                map.computeIfAbsent(r.getRickshawId(), k -> new ArrayList<>()).add(r);
            }
        }
        return map;
    }

    public synchronized List<RideRequest> findPendingOfferedTo(String rickshawId) {
        List<RideRequest> list = new ArrayList<>();
        for (RideRequest r : rides.values()) {
            if (r.getStatus() == RideStatus.PENDING && r.getOfferedRickshawId().equals(rickshawId)) {
                list.add(r);
            }
        }
        return list;
    }

    /** Rides a rickshaw has carried (any status after acceptance), for the driver's history. */
    public synchronized List<RideRequest> findByRickshaw(String rickshawId) {
        List<RideRequest> list = new ArrayList<>();
        for (RideRequest r : rides.values()) {
            if (r.getRickshawId().equals(rickshawId)) {
                list.add(r);
            }
        }
        return list;
    }

    public synchronized void flush() {
        try {
            List<RideRequest> all = new ArrayList<>(rides.values());
            storage.saveRides(all);
            List<RidePassengerRecord> seats = new ArrayList<>();
            for (RideRequest r : all) {
                if (!r.getRickshawId().isEmpty()) {
                    seats.add(new RidePassengerRecord(r.getId(), r.getPassengerId(), r.getPassengerName(),
                            r.getRickshawId(), r.getPickupId(), r.getDestinationId(), r.getSeatsRequested(),
                            r.getDetourMeters(), r.getStatus()));
                }
            }
            storage.saveRidePassengers(seats);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save rides.csv: " + e.getMessage(), e);
        }
    }
}
