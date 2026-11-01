package service;

import dsa.QueuePolicy;
import dsa.RideQueue;
import dsa.RouteResult;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import storage.CsvStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages ride requests and rickshaws: create, driver accept/reject, auto-match,
 * multi-passenger seat sharing, live movement tracking, start, complete, cancel.
 *
 * Every successful change is saved to rides.csv and rickshaws.csv right away.
 * Every method returns a message for the user, or throws an exception
 * (IllegalArgumentException / IllegalStateException) with the reason it failed.
 */
public class RideService {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CsvStorage storage;
    private final RouteService routeService;
    private final RideMatchingService matchingService;

    private final Map<Integer, RideRequest> rides = new LinkedHashMap<>();   // all rides, by id
    private final Map<String, Rickshaw> rickshaws = new LinkedHashMap<>();   // all rickshaws, by id
    private final RideQueue pendingQueue = new RideQueue(QueuePolicy.FIFO);
    private final List<String> notifications = new ArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private int nextRideId = 1;

    public RideService(CsvStorage storage, RouteService routeService) throws IOException {
        this.storage = storage;
        this.routeService = routeService;
        this.matchingService = new RideMatchingService(routeService.getGraph());
        loadRickshaws();
        loadRides();
    }

    // ------------------------------------------------------------- loading

    private void loadRickshaws() throws IOException {
        boolean skipped = false;
        for (Rickshaw r : storage.loadRickshaws()) {
            if (!routeService.isValidLocation(r.getLocationId()) || rickshaws.containsKey(r.getId())) {
                storage.addWarning("Skipped rickshaw " + r.getId() + ": unknown location or duplicate id.");
                skipped = true;
                continue;
            }
            r.setAvailable(true); // real availability & occupied seats are decided from the rides below
            rickshaws.put(r.getId(), r);
        }
        if (skipped) {
            storage.backup(CsvStorage.RICKSHAWS_FILE);
        }
    }

    /**
     * Loads rides and rebuilds the pending queue. A rickshaw's occupied seats
     * and availability are rebuilt from its MATCHED or IN_PROGRESS rides.
     */
    private void loadRides() throws IOException {
        boolean skipped = false;
        for (RideRequest ride : storage.loadRides()) {
            String problem = checkLoadedRide(ride);
            if (problem != null) {
                storage.addWarning("Skipped ride #" + ride.getId() + ": " + problem);
                skipped = true;
                continue;
            }
            if (ride.getStatus() == RideStatus.PENDING) {
                ride.setRickshawId("");
                pendingQueue.add(ride);
            }
            if (ride.getStatus().isActive()) {
                Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
                rickshaw.setOccupiedSeats(rickshaw.getOccupiedSeats() + ride.getSeatsRequested());
            }
            rides.put(ride.getId(), ride);
            nextRideId = Math.max(nextRideId, ride.getId() + 1);
        }
        if (skipped) {
            storage.backup(CsvStorage.RIDES_FILE);
        }
    }

    private String checkLoadedRide(RideRequest ride) {
        if (rides.containsKey(ride.getId())) {
            return "duplicate id";
        }
        if (!routeService.isValidLocation(ride.getPickupId()) || !routeService.isValidLocation(ride.getDestinationId())) {
            return "unknown location";
        }
        if (ride.getStatus().isActive()) {
            Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
            if (rickshaw == null) {
                return "unknown rickshaw '" + ride.getRickshawId() + "'";
            }
            if (!rickshaw.hasSeatsFor(ride.getSeatsRequested())) {
                return "rickshaw " + rickshaw.getId() + " already has no available seats";
            }
        }
        return null;
    }

    // ---------------------------------------------------------- listeners & notifications

    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private void pushNotification(String text) {
        String timestamped = "[" + LocalTime.now().format(TIME_FMT) + "] " + text;
        notifications.add(0, timestamped);
        if (notifications.size() > 100) {
            notifications.remove(notifications.size() - 1);
        }
    }

    public List<String> getNotifications() {
        return Collections.unmodifiableList(new ArrayList<>(notifications));
    }

    // ---------------------------------------------------------- read access

    public List<RideRequest> getRides() {
        return Collections.unmodifiableList(new ArrayList<>(rides.values()));
    }

    public List<Rickshaw> getRickshaws() {
        return Collections.unmodifiableList(new ArrayList<>(rickshaws.values()));
    }

    public RideRequest getRide(int id) {
        return rides.get(id);
    }

    public Rickshaw getRickshaw(String id) {
        return rickshaws.get(id);
    }

    public List<RideRequest> getPendingInOrder() {
        return pendingQueue.inProcessingOrder();
    }

    public QueuePolicy getQueuePolicy() {
        return pendingQueue.getPolicy();
    }

    public void setQueuePolicy(QueuePolicy policy) {
        pendingQueue.setPolicy(policy);
        notifyListeners();
    }

    public RouteService getRouteService() {
        return routeService;
    }

    public RideMatchingService getMatchingService() {
        return matchingService;
    }

    /** Returns all active (MATCHED or IN_PROGRESS) rides currently assigned to a rickshaw. */
    public List<RideRequest> getActiveRidesForRickshaw(String rickshawId) {
        List<RideRequest> active = new ArrayList<>();
        for (RideRequest r : rides.values()) {
            if (r.getStatus().isActive() && r.getRickshawId().equals(rickshawId)) {
                active.add(r);
            }
        }
        return active;
    }

    /** Recalculates a rickshaw's occupied seats from its active rides. */
    private void syncRickshawSeats(Rickshaw rickshaw) {
        if (rickshaw == null) {
            return;
        }
        int seats = 0;
        for (RideRequest r : getActiveRidesForRickshaw(rickshaw.getId())) {
            seats += r.getSeatsRequested();
        }
        rickshaw.setOccupiedSeats(seats);
        if (seats == 0) {
            rickshaw.setLiveMovement(rickshaw.getLocationId(), rickshaw.getLocationId(), 0.0);
        }
    }

    // ------------------------------------------------------------- actions

    /** Creates a PENDING request (1 seat) and adds it to the queue. */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId, String destinationId) {
        return createRequest(passengerName, type, pickupId, destinationId, 1);
    }

    /** Creates a PENDING request with the specified seat count (1 to 3) and adds it to the queue. */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId,
                                     String destinationId, int seatsRequested) {
        // Commas would break the CSV format, so they are replaced with spaces.
        String name = passengerName == null ? "" : passengerName.replace(',', ' ').trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Please enter the passenger name.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Please choose a passenger type.");
        }
        if (!routeService.isValidLocation(pickupId) || !routeService.isValidLocation(destinationId)) {
            throw new IllegalArgumentException("Please choose a valid pickup and destination.");
        }
        if (pickupId.equals(destinationId)) {
            throw new IllegalArgumentException("Pickup and destination must be different.");
        }
        if (seatsRequested < 1 || seatsRequested > Rickshaw.DEFAULT_MAX_SEATS) {
            throw new IllegalArgumentException("Seats requested must be between 1 and " + Rickshaw.DEFAULT_MAX_SEATS + ".");
        }
        RouteResult route = routeService.findRoute(pickupId, destinationId);
        if (!route.isFound()) {
            throw new IllegalArgumentException(route.getMessage());
        }

        RideRequest ride = new RideRequest(nextRideId, name, type, pickupId, destinationId,
                route.getTotalDistanceMeters(), RideStatus.PENDING, "", seatsRequested);
        nextRideId++;
        rides.put(ride.getId(), ride);
        pendingQueue.add(ride);
        save();
        pushNotification(String.format(Locale.ROOT,
                "New Request #%d: %s (%s, %d seat%s) from %s to %s (%.0f m)",
                ride.getId(), ride.getPassengerName(), ride.getPassengerType(),
                ride.getSeatsRequested(), ride.getSeatsRequested() > 1 ? "s" : "",
                routeService.getLocationName(pickupId), routeService.getLocationName(destinationId),
                ride.getDistanceMeters()));
        notifyListeners();
        return ride;
    }

    /**
     * Takes the next request from the queue (by the active policy) and gives it
     * the nearest available rickshaw. If none is available the request stays
     * at the front of the queue.
     */
    public String matchNextRequest() {
        RideRequest next = pendingQueue.peek();
        if (next == null) {
            throw new IllegalStateException("There are no pending requests to match.");
        }
        RideMatchingService.Match match = matchingService.findBestRickshaw(next, getRickshaws());
        if (match == null) {
            throw new IllegalStateException("No available rickshaw for request #" + next.getId()
                    + ". It stays first in the queue.");
        }

        pendingQueue.poll(); // removed from the queue, so it can never be matched twice
        Rickshaw rickshaw = match.getRickshaw();
        next.moveTo(RideStatus.MATCHED);
        next.setRickshawId(rickshaw.getId());
        syncRickshawSeats(rickshaw);
        save();
        String msg = String.format(Locale.ROOT, "Request #%d (%s) matched with %s (%s), %.1f m from the pickup.",
                next.getId(), next.getPassengerName(), rickshaw.getId(), rickshaw.getDriverName(),
                match.getDistanceToPickupMeters());
        pushNotification(msg);
        notifyListeners();
        return msg;
    }

    /**
     * Driver App action: a specific driver accepts a PENDING ride request.
     * Supports shared rides up to the rickshaw's maximum seat capacity (3 seats).
     */
    public String driverAcceptRide(String rickshawId, int rideId) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            throw new IllegalArgumentException("Unknown rickshaw: " + rickshawId);
        }
        RideRequest ride = requireRide(rideId);
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new IllegalStateException("Ride #" + rideId + " is no longer pending (current status: " + ride.getStatus() + ").");
        }
        if (!rickshaw.hasSeatsFor(ride.getSeatsRequested())) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "%s only has %d/%d free seat(s), cannot accept Ride #%d (%d seat(s) needed).",
                    rickshaw.getId(), rickshaw.getAvailableSeats(), rickshaw.getMaxSeats(),
                    rideId, ride.getSeatsRequested()));
        }
        RouteResult toPickup = matchingService.routeFromRickshaw(rickshaw, ride.getPickupId());
        if (!toPickup.isFound()) {
            throw new IllegalStateException("No road route from " + rickshaw.getLocationId() + " to pickup.");
        }

        pendingQueue.remove(ride);
        ride.moveTo(RideStatus.MATCHED);
        ride.setRickshawId(rickshaw.getId());
        syncRickshawSeats(rickshaw);
        save();

        String sharedNote = getActiveRidesForRickshaw(rickshawId).size() > 1
                ? " [Shared Ride: " + rickshaw.getOccupiedSeats() + "/" + rickshaw.getMaxSeats() + " seats occupied]"
                : "";
        String msg = String.format(Locale.ROOT,
                "Driver %s (%s) accepted Ride #%d for %s! Distance to pickup (%s): %.1f m.%s",
                rickshaw.getDriverName(), rickshaw.getId(), ride.getId(), ride.getPassengerName(),
                routeService.getLocationName(ride.getPickupId()), toPickup.getTotalDistanceMeters(), sharedNote);
        pushNotification(msg);
        notifyListeners();
        return msg;
    }

    /**
     * Driver App action: a specific driver rejects/passes on a PENDING ride request.
     * The request stays PENDING in the queue so other drivers can accept it.
     */
    public String driverRejectRide(String rickshawId, int rideId) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            throw new IllegalArgumentException("Unknown rickshaw: " + rickshawId);
        }
        RideRequest ride = requireRide(rideId);
        if (ride.getStatus() != RideStatus.PENDING) {
            throw new IllegalStateException("Ride #" + rideId + " is not pending.");
        }
        ride.addRejectedBy(rickshawId);
        String msg = String.format(Locale.ROOT,
                "Driver %s (%s) declined Ride #%d (%s). Waiting for another rickshaw.",
                rickshaw.getDriverName(), rickshaw.getId(), ride.getId(), ride.getPassengerName());
        pushNotification(msg);
        notifyListeners();
        return msg;
    }

    public String startRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        ride.moveTo(RideStatus.IN_PROGRESS);
        Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
        if (rickshaw != null) {
            rickshaw.setLocationId(ride.getPickupId());
        }
        save();
        String msg = "Ride #" + rideId + " is now in progress.";
        pushNotification("Ride #" + rideId + " (" + ride.getPassengerName() + ") started from "
                + routeService.getLocationName(ride.getPickupId()) + " -> "
                + routeService.getLocationName(ride.getDestinationId()) + ".");
        notifyListeners();
        return msg;
    }

    /** Completes the ride; the rickshaw frees the ride's seats and updates its location to the destination. */
    public String completeRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        ride.moveTo(RideStatus.COMPLETED);
        Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
        rickshaw.setLocationId(ride.getDestinationId());
        syncRickshawSeats(rickshaw);
        save();
        String msg = "Ride #" + rideId + " completed. " + rickshaw.getId() + " is available at "
                + routeService.getLocationName(ride.getDestinationId()) + ".";
        pushNotification("Ride #" + rideId + " (" + ride.getPassengerName() + ") arrived at "
                + routeService.getLocationName(ride.getDestinationId()) + " (COMPLETED).");
        notifyListeners();
        return msg;
    }

    /** Cancels a PENDING or MATCHED ride and frees its seats if it had a rickshaw. */
    public String cancelRide(int rideId) {
        RideRequest ride = requireRide(rideId);
        RideStatus before = ride.getStatus();
        ride.moveTo(RideStatus.CANCELLED);
        if (before == RideStatus.PENDING) {
            pendingQueue.remove(ride);
        } else {
            Rickshaw rickshaw = rickshaws.get(ride.getRickshawId());
            syncRickshawSeats(rickshaw);
        }
        save();
        String msg = "Ride #" + rideId + " cancelled.";
        pushNotification("Ride #" + rideId + " (" + ride.getPassengerName() + ") was cancelled.");
        notifyListeners();
        return msg;
    }

    // ------------------------------------------------------------- live tracking & navigation

    /**
     * Builds the active navigation path (sequence of campus location IDs) for a rickshaw.
     * - For MATCHED rides, navigates from the rickshaw's current location to the pickup, then destination.
     * - For IN_PROGRESS rides, navigates from the rickshaw's current location to the destination.
     */
    public List<String> getPlannedPathForRickshaw(String rickshawId) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            return List.of();
        }
        List<RideRequest> active = getActiveRidesForRickshaw(rickshawId);
        if (active.isEmpty()) {
            return List.of();
        }

        List<String> waypoints = new ArrayList<>();
        // First visit pickups of any MATCHED rides
        for (RideRequest r : active) {
            if (r.getStatus() == RideStatus.MATCHED && !waypoints.contains(r.getPickupId())) {
                waypoints.add(r.getPickupId());
            }
        }
        // Then visit destinations of all active rides
        for (RideRequest r : active) {
            if (!waypoints.contains(r.getDestinationId())) {
                waypoints.add(r.getDestinationId());
            }
        }

        List<String> fullPath = new ArrayList<>();
        String current = rickshaw.getLocationId();
        fullPath.add(current);
        for (String target : waypoints) {
            if (current.equals(target)) {
                continue;
            }
            RouteResult leg = routeService.findRoute(current, target);
            if (leg.isFound() && leg.getPath().size() > 1) {
                for (int i = 1; i < leg.getPath().size(); i++) {
                    fullPath.add(leg.getPath().get(i));
                }
                current = target;
            }
        }
        return fullPath;
    }

    /**
     * Advances a rickshaw by one road segment or action along its active ride path.
     * Used by the Driver's "Move Next Stop" button and the Live GPS Simulation timer.
     */
    public String stepRickshawMovement(String rickshawId) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            throw new IllegalArgumentException("Unknown rickshaw: " + rickshawId);
        }
        List<RideRequest> active = getActiveRidesForRickshaw(rickshawId);
        if (active.isEmpty()) {
            throw new IllegalStateException(rickshaw.getId() + " has no active rides to navigate.");
        }

        // 1. Check if rickshaw is already at a MATCHED ride's pickup -> start that ride!
        for (RideRequest r : active) {
            if (r.getStatus() == RideStatus.MATCHED && rickshaw.getLocationId().equals(r.getPickupId())) {
                r.moveTo(RideStatus.IN_PROGRESS);
                save();
                String msg = String.format(Locale.ROOT,
                        "%s picked up %s (Ride #%d) at %s! Ride is now IN_PROGRESS.",
                        rickshaw.getId(), r.getPassengerName(), r.getId(),
                        routeService.getLocationName(r.getPickupId()));
                pushNotification(msg);
                notifyListeners();
                return msg;
            }
        }

        // 2. Check if rickshaw is already at an IN_PROGRESS ride's destination -> complete that ride!
        for (RideRequest r : active) {
            if (r.getStatus() == RideStatus.IN_PROGRESS && rickshaw.getLocationId().equals(r.getDestinationId())) {
                return completeRide(r.getId());
            }
        }

        // 3. Otherwise move to the next node along the planned Dijkstra path
        List<String> path = getPlannedPathForRickshaw(rickshawId);
        if (path.size() < 2) {
            throw new IllegalStateException("Rickshaw is already at target node.");
        }
        String nextNode = path.get(1);
        rickshaw.setLocationId(nextNode);

        // Auto-transition any MATCHED ride whose pickup we just reached to IN_PROGRESS
        List<String> events = new ArrayList<>();
        events.add(rickshaw.getId() + " moved to " + routeService.getLocationName(nextNode) + ".");
        for (RideRequest r : active) {
            if (r.getStatus() == RideStatus.MATCHED && nextNode.equals(r.getPickupId())) {
                r.moveTo(RideStatus.IN_PROGRESS);
                events.add("Picked up " + r.getPassengerName() + " (Ride #" + r.getId() + " started)!");
            } else if (r.getStatus() == RideStatus.IN_PROGRESS && nextNode.equals(r.getDestinationId())) {
                r.moveTo(RideStatus.COMPLETED);
                events.add("Dropped off " + r.getPassengerName() + " (Ride #" + r.getId() + " completed)!");
            }
        }
        syncRickshawSeats(rickshaw);
        save();
        String msg = String.join(" ", events);
        pushNotification(msg);
        notifyListeners();
        return msg;
    }

    /**
     * Smoothly advances animation progress for a rickshaw along its next road segment.
     * When segmentProgress reaches 1.0, calls stepRickshawMovement(rickshawId).
     */
    public boolean tickSmoothAnimation(String rickshawId, double delta) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            return false;
        }
        List<RideRequest> active = getActiveRidesForRickshaw(rickshawId);
        if (active.isEmpty()) {
            rickshaw.setLiveMovement(rickshaw.getLocationId(), rickshaw.getLocationId(), 0.0);
            return false;
        }

        List<String> path = getPlannedPathForRickshaw(rickshawId);
        if (path.size() < 2) {
            // At pickup or destination already
            stepRickshawMovement(rickshawId);
            return true;
        }

        String fromId = path.get(0);
        String toId = path.get(1);
        double nextProgress = (rickshaw.isMoving() && fromId.equals(rickshaw.getMovingFromId())
                && toId.equals(rickshaw.getMovingToId()))
                ? rickshaw.getSegmentProgress() + delta
                : delta;

        if (nextProgress >= 1.0) {
            rickshaw.setLiveMovement(toId, toId, 0.0);
            stepRickshawMovement(rickshawId);
        } else {
            rickshaw.setLiveMovement(fromId, toId, nextProgress);
            notifyListeners();
        }
        return true;
    }

    private RideRequest requireRide(int rideId) {
        RideRequest ride = rides.get(rideId);
        if (ride == null) {
            throw new IllegalArgumentException("Ride #" + rideId + " does not exist.");
        }
        return ride;
    }

    private void save() {
        try {
            storage.saveRides(getRides());
            storage.saveRickshaws(getRickshaws());
        } catch (IOException e) {
            throw new UncheckedIOException("The change was made but could not be saved to "
                    + storage.getDataDir() + ": " + e.getMessage(), e);
        }
    }

    /** Count of rickshaws that are completely free right now. */
    public int countAvailableRickshaws() {
        int count = 0;
        for (Rickshaw r : rickshaws.values()) {
            if (r.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    /** Count of rickshaws that have at least 1 seat available (including shared rides). */
    public int countRickshawsWithFreeSeats() {
        int count = 0;
        for (Rickshaw r : rickshaws.values()) {
            if (r.getAvailableSeats() > 0) {
                count++;
            }
        }
        return count;
    }
}
