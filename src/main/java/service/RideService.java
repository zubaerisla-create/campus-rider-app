package service;

import dsa.QueuePolicy;
import dsa.RickshawSorter;
import dsa.RideQueue;
import dsa.RouteCompatibility;
import dsa.RouteResult;
import model.DriverAccount;
import model.PassengerAccount;
import model.PassengerType;
import model.RidePassengerRecord;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Passenger & Driver accounts, ride requests, multi-passenger seat sharing,
 * route compatibility matching, driver accept/reject with automatic fallback to the
 * next compatible rickshaw, and simulated live GPS tracking.
 *
 * Every change is persisted immediately to CSV files in data/:
 *   passengers.csv, drivers.csv, rickshaws.csv, rides.csv, ride_passengers.csv
 */
public class RideService {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CsvStorage storage;
    private final RouteService routeService;
    private final RideMatchingService matchingService;

    // HashMap / LinkedHashMap & ArrayList data structures for fast O(1) lookup
    private final Map<Integer, RideRequest> rides = new LinkedHashMap<>();
    private final Map<String, Rickshaw> rickshaws = new LinkedHashMap<>();
    private final Map<String, PassengerAccount> passengersByUsername = new LinkedHashMap<>();
    private final Map<String, DriverAccount> driversByUsername = new LinkedHashMap<>();
    private final RideQueue pendingQueue = new RideQueue(QueuePolicy.FIFO);
    private final List<String> notifications = new ArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private PassengerAccount loggedInPassenger;
    private DriverAccount loggedInDriver;
    private int nextRideId = 1;

    public RideService(CsvStorage storage, RouteService routeService) throws IOException {
        this.storage = storage;
        this.routeService = routeService;
        this.matchingService = new RideMatchingService(routeService.getGraph());
        loadRickshaws();
        loadAccounts();
        loadRides();
        saveRidePassengersSnapshot();
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

    private void loadAccounts() throws IOException {
        for (PassengerAccount p : storage.loadPassengers()) {
            passengersByUsername.put(p.getUsername().toLowerCase(Locale.ROOT), p);
            if (loggedInPassenger == null) {
                loggedInPassenger = p;
            }
        }
        for (DriverAccount d : storage.loadDrivers()) {
            if (rickshaws.containsKey(d.getRickshawId())) {
                driversByUsername.put(d.getUsername().toLowerCase(Locale.ROOT), d);
                if (loggedInDriver == null) {
                    loggedInDriver = d;
                }
            }
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
                assignBestOfferIfNeeded(ride);
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

    // ---------------------------------------------------------- Passenger & Driver Accounts

    public List<PassengerAccount> getPassengers() {
        return Collections.unmodifiableList(new ArrayList<>(passengersByUsername.values()));
    }

    public List<DriverAccount> getDrivers() {
        return Collections.unmodifiableList(new ArrayList<>(driversByUsername.values()));
    }

    public PassengerAccount getLoggedInPassenger() {
        return loggedInPassenger;
    }

    public DriverAccount getLoggedInDriver() {
        return loggedInDriver;
    }

    public PassengerAccount loginPassenger(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Please enter your passenger username.");
        }
        PassengerAccount account = passengersByUsername.get(username.trim().toLowerCase(Locale.ROOT));
        if (account == null || !account.getPassword().equals(password == null ? "" : password.trim())) {
            throw new IllegalArgumentException("Invalid passenger username or password.");
        }
        this.loggedInPassenger = account;
        pushNotification("Passenger logged in: " + account.getFullName() + " (@" + account.getUsername() + ")");
        notifyListeners();
        return account;
    }

    public PassengerAccount registerPassenger(String username, String password,
                                              String fullName, PassengerType type, String phone) {
        String u = username == null ? "" : username.replace(',', ' ').trim().toLowerCase(Locale.ROOT);
        String p = password == null ? "" : password.replace(',', ' ').trim();
        String name = fullName == null ? "" : fullName.replace(',', ' ').trim();
        String ph = phone == null ? "" : phone.replace(',', ' ').trim();

        if (u.isEmpty() || p.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Username, password, and full name are required.");
        }
        if (passengersByUsername.containsKey(u)) {
            throw new IllegalArgumentException("Username '" + u + "' is already registered.");
        }
        String newId = "P-" + (101 + passengersByUsername.size());
        PassengerAccount created = new PassengerAccount(
                newId, u, p, name, type == null ? PassengerType.STUDENT : type, ph);
        passengersByUsername.put(u, created);
        this.loggedInPassenger = created;
        try {
            storage.savePassengers(getPassengers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        pushNotification("Registered new Passenger account: " + created.getFullName() + " (@" + u + ")");
        notifyListeners();
        return created;
    }

    public DriverAccount loginDriver(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Please enter your driver username.");
        }
        DriverAccount account = driversByUsername.get(username.trim().toLowerCase(Locale.ROOT));
        if (account == null || !account.getPassword().equals(password == null ? "" : password.trim())) {
            throw new IllegalArgumentException("Invalid driver username or password.");
        }
        this.loggedInDriver = account;
        pushNotification("Driver logged in: " + account.getDriverName() + " (" + account.getRickshawId() + ")");
        notifyListeners();
        return account;
    }

    public DriverAccount registerDriver(String username, String password,
                                        String driverName, String initialLocationId, String phone) {
        String u = username == null ? "" : username.replace(',', ' ').trim().toLowerCase(Locale.ROOT);
        String p = password == null ? "" : password.replace(',', ' ').trim();
        String name = driverName == null ? "" : driverName.replace(',', ' ').trim();
        String ph = phone == null ? "" : phone.replace(',', ' ').trim();

        if (u.isEmpty() || p.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("Driver username, password, and name are required.");
        }
        if (driversByUsername.containsKey(u)) {
            throw new IllegalArgumentException("Driver username '" + u + "' is already registered.");
        }
        if (!routeService.isValidLocation(initialLocationId)) {
            throw new IllegalArgumentException("Please choose a valid starting campus location.");
        }

        int nextNum = 101 + rickshaws.size();
        String rickshawId = "JU-RICK-" + nextNum;
        while (rickshaws.containsKey(rickshawId)) {
            nextNum++;
            rickshawId = "JU-RICK-" + nextNum;
        }
        Rickshaw newRickshaw = new Rickshaw(rickshawId, name, initialLocationId, true);
        rickshaws.put(rickshawId, newRickshaw);

        String driverId = "D-" + nextNum;
        DriverAccount created = new DriverAccount(driverId, u, p, name, rickshawId, ph);
        driversByUsername.put(u, created);
        this.loggedInDriver = created;

        try {
            storage.saveRickshaws(getRickshaws());
            storage.saveDrivers(getDrivers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        pushNotification("Registered new Driver " + name + " with Rickshaw " + rickshawId
                + " at " + routeService.getLocationName(initialLocationId));
        notifyListeners();
        return created;
    }

    public DriverAccount getDriverForRickshaw(String rickshawId) {
        for (DriverAccount d : driversByUsername.values()) {
            if (d.getRickshawId().equals(rickshawId)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Updates a driver's Rickshaw Current Location, Target Destination, and Available Seats.
     */
    public String updateDriverRickshawState(String rickshawId,
                                            String currentLocationId,
                                            String targetDestinationId,
                                            int availableSeats) {
        Rickshaw rickshaw = rickshaws.get(rickshawId);
        if (rickshaw == null) {
            throw new IllegalArgumentException("Unknown rickshaw: " + rickshawId);
        }
        if (!routeService.isValidLocation(currentLocationId)) {
            throw new IllegalArgumentException("Invalid current location.");
        }
        if (targetDestinationId != null && !targetDestinationId.isBlank()
                && !routeService.isValidLocation(targetDestinationId)) {
            throw new IllegalArgumentException("Invalid target destination.");
        }
        if (availableSeats < 0 || availableSeats > rickshaw.getMaxSeats()) {
            throw new IllegalArgumentException("Available seats must be between 0 and " + rickshaw.getMaxSeats() + ".");
        }

        rickshaw.setLocationId(currentLocationId);
        rickshaw.setTargetDestinationId(targetDestinationId == null ? "" : targetDestinationId);
        rickshaw.setAvailableSeats(availableSeats);
        save();

        String destText = rickshaw.getTargetDestinationId().isEmpty()
                ? "Any Campus Location"
                : routeService.getLocationName(rickshaw.getTargetDestinationId());
        String msg = String.format(Locale.ROOT,
                "%s updated: Location = %s | Target = %s | Free Seats = %d/%d",
                rickshaw.getId(),
                routeService.getLocationName(currentLocationId),
                destText,
                rickshaw.getAvailableSeats(),
                rickshaw.getMaxSeats());
        pushNotification(msg);
        notifyListeners();
        return msg;
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

    public Map<String, List<RideRequest>> getActiveRidesByRickshawMap() {
        Map<String, List<RideRequest>> map = new HashMap<>();
        for (Rickshaw r : rickshaws.values()) {
            map.put(r.getId(), getActiveRidesForRickshaw(r.getId()));
        }
        return map;
    }

    /**
     * Evaluates and sorts available rickshaws (both idle and seat-sharing) for a
     * passenger's (pickup -> destination, seats) query using Merge Sort & Route Compatibility.
     */
    public List<RouteCompatibility.CompatibilityResult> findSortedCompatibleRickshaws(
            String pickupId,
            String destinationId,
            int seatsNeeded,
            RickshawSorter.SortCriterion sortCriterion) {
        if (!routeService.isValidLocation(pickupId) || !routeService.isValidLocation(destinationId)) {
            return List.of();
        }
        return matchingService.findCompatibleRickshawsSorted(
                pickupId, destinationId, seatsNeeded,
                getRickshaws(), getActiveRidesByRickshawMap(), sortCriterion);
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

    private void assignBestOfferIfNeeded(RideRequest ride) {
        if (ride.getStatus() != RideStatus.PENDING) {
            return;
        }
        RouteCompatibility.CompatibilityResult best = matchingService.findNextCompatibleRickshawForRequest(
                ride, getRickshaws(), getActiveRidesByRickshawMap());
        if (best != null) {
            ride.setOfferedRickshawId(best.getRickshaw().getId());
            ride.setDetourMeters(best.getDetourMeters());
        } else {
            ride.setOfferedRickshawId("");
        }
    }

    // ------------------------------------------------------------- actions

    /** Creates a PENDING request (1 seat) and adds it to the queue. */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId, String destinationId) {
        return createRequest(passengerName, type, pickupId, destinationId, 1, null);
    }

    /** Creates a PENDING request with the specified seat count (1 to 3) and adds it to the queue. */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId,
                                     String destinationId, int seatsRequested) {
        return createRequest(passengerName, type, pickupId, destinationId, seatsRequested, null);
    }

    /**
     * Creates a PENDING request with seat count and optional preferred/selected rickshaw.
     * If preferredRickshawId is null/blank, the Greedy Route Compatibility matcher automatically
     * recommends the most suitable compatible rickshaw and forwards the request to it.
     */
    public RideRequest createRequest(String passengerName, PassengerType type, String pickupId,
                                     String destinationId, int seatsRequested, String preferredRickshawId) {
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
        if (loggedInPassenger != null && loggedInPassenger.getFullName().equalsIgnoreCase(name)) {
            ride.setPassengerId(loggedInPassenger.getId());
        }
        nextRideId++;

        if (preferredRickshawId != null && !preferredRickshawId.isBlank() && rickshaws.containsKey(preferredRickshawId)) {
            Rickshaw pref = rickshaws.get(preferredRickshawId);
            RouteCompatibility.CompatibilityResult eval = RouteCompatibility.evaluate(
                    routeService.getGraph(), pref, getActiveRidesForRickshaw(preferredRickshawId),
                    pickupId, destinationId, seatsRequested);
            ride.setOfferedRickshawId(preferredRickshawId);
            ride.setDetourMeters(eval.getDetourMeters());
        } else {
            assignBestOfferIfNeeded(ride);
        }

        rides.put(ride.getId(), ride);
        pendingQueue.add(ride);
        save();

        String offerInfo = ride.getOfferedRickshawId().isEmpty()
                ? "Queued for next free rickshaw"
                : "Sent to best match: " + ride.getOfferedRickshawId();
        pushNotification(String.format(Locale.ROOT,
                "New Request #%d: %s (%s, %d seat%s) %s -> %s (%.0f m) [%s]",
                ride.getId(), ride.getPassengerName(), ride.getPassengerType(),
                ride.getSeatsRequested(), ride.getSeatsRequested() > 1 ? "s" : "",
                routeService.getLocationName(pickupId), routeService.getLocationName(destinationId),
                ride.getDistanceMeters(), offerInfo));
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
        next.setOfferedRickshawId(rickshaw.getId());
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
     * Supports shared rides up to the rickshaw's maximum seat capacity (3 seats)
     * and records the route compatibility detour in ride_passengers.csv.
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

        List<RideRequest> existingActive = getActiveRidesForRickshaw(rickshawId);
        RouteCompatibility.CompatibilityResult compat = RouteCompatibility.evaluate(
                routeService.getGraph(), rickshaw, existingActive,
                ride.getPickupId(), ride.getDestinationId(), ride.getSeatsRequested());

        RouteResult toPickup = matchingService.routeFromRickshaw(rickshaw, ride.getPickupId());
        if (!toPickup.isFound()) {
            throw new IllegalStateException("No road route from " + rickshaw.getLocationId() + " to pickup.");
        }

        pendingQueue.remove(ride);
        ride.moveTo(RideStatus.MATCHED);
        ride.setRickshawId(rickshaw.getId());
        ride.setOfferedRickshawId(rickshaw.getId());
        ride.setDetourMeters(compat.getDetourMeters());
        syncRickshawSeats(rickshaw);
        save();

        String sharedNote = getActiveRidesForRickshaw(rickshawId).size() > 1
                ? String.format(Locale.ROOT, " [Shared Ride: %d/%d seats occupied, +%.0fm route detour]",
                        rickshaw.getOccupiedSeats(), rickshaw.getMaxSeats(), compat.getDetourMeters())
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
     * Automatically finds the NEXT most compatible rickshaw using the Greedy Route
     * Compatibility algorithm and forwards the request to that driver!
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

        // Automatically find the next best compatible rickshaw
        RouteCompatibility.CompatibilityResult nextBest = matchingService.findNextCompatibleRickshawForRequest(
                ride, getRickshaws(), getActiveRidesByRickshawMap());
        String fallbackNote;
        if (nextBest != null) {
            ride.setOfferedRickshawId(nextBest.getRickshaw().getId());
            ride.setDetourMeters(nextBest.getDetourMeters());
            fallbackNote = String.format(Locale.ROOT,
                    "Forwarded to next compatible rickshaw: %s (%s, %.0fm to pickup).",
                    nextBest.getRickshaw().getId(),
                    nextBest.getRickshaw().getDriverName(),
                    nextBest.getDistanceToPickupMeters());
        } else {
            ride.setOfferedRickshawId("");
            fallbackNote = "Waiting in queue for another rickshaw to become available.";
        }

        save();
        String msg = String.format(Locale.ROOT,
                "Driver %s (%s) declined Ride #%d (%s). %s",
                rickshaw.getDriverName(), rickshaw.getId(), ride.getId(), ride.getPassengerName(), fallbackNote);
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
     * Used by the Driver's "Move Rickshaw" button and the Live GPS Simulation timer.
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
            saveRidePassengersSnapshot();
        } catch (IOException e) {
            throw new UncheckedIOException("The change was made but could not be saved to "
                    + storage.getDataDir() + ": " + e.getMessage(), e);
        }
    }

    private void saveRidePassengersSnapshot() throws IOException {
        List<RidePassengerRecord> records = new ArrayList<>();
        for (RideRequest r : rides.values()) {
            String pId = r.getPassengerId().isEmpty() ? "P-GUEST" : r.getPassengerId();
            String rId = r.getRickshawId().isEmpty() ? r.getOfferedRickshawId() : r.getRickshawId();
            records.add(new RidePassengerRecord(
                    r.getId(), pId, r.getPassengerName(), rId,
                    r.getPickupId(), r.getDestinationId(),
                    r.getSeatsRequested(), r.getDetourMeters(), r.getStatus()));
        }
        storage.saveRidePassengers(records);
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
