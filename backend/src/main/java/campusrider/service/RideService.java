package campusrider.service;

import campusrider.algorithms.MergeSort;
import campusrider.algorithms.RickshawSorter;
import campusrider.algorithms.RideQueue;
import campusrider.algorithms.RouteCompatibility;
import campusrider.algorithms.RouteResult;
import campusrider.config.AppProperties;
import campusrider.dto.DriverDashboardDto;
import campusrider.dto.OfferDto;
import campusrider.dto.RickshawOptionDto;
import campusrider.dto.RideDto;
import campusrider.dto.RideRequestBody;
import campusrider.dto.RouteDto;
import campusrider.dto.StopDto;
import campusrider.exception.BadRequestException;
import campusrider.exception.ConflictException;
import campusrider.exception.ForbiddenException;
import campusrider.exception.NotFoundException;
import campusrider.matching.RideMatchingService;
import campusrider.model.PassengerAccount;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.PassengerRepository;
import campusrider.repository.RickshawRepository;
import campusrider.repository.RideRepository;
import campusrider.tracking.RealtimeNotifier;
import campusrider.tracking.RidePlanner;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ride lifecycle: request -> dispatch -> accept / reject -> start -> complete (or cancel).
 *
 * Dispatching:
 *   New requests (and requests that need a new driver) go into the {@link RideQueue}:
 *   emergency requests into its PriorityQueue, others into its FIFO queue. dispatch()
 *   drains the queue in that order and, for each request, asks {@link RideMatchingService}
 *   for the best compatible rickshaw (Dijkstra + RouteCompatibility + merge sort + greedy).
 *   Seats promised to earlier requests are reserved, so a scarce rickshaw goes to the
 *   request that is first in queue order. A request that cannot be offered yet stays
 *   queued; one that every suitable driver has rejected becomes REJECTED.
 *
 * Status changes always go through the RideStateMachine (RideRequest.moveTo).
 * Every method runs inside {@link RideLock}, which prevents overbooking when several
 * passengers book or several drivers accept at the same moment.
 */
@Service
public class RideService {

    private final RideLock lock;
    private final RouteService routes;
    private final RideRepository rides;
    private final RickshawRepository rickshaws;
    private final PassengerRepository passengers;
    private final RideMatchingService matching;
    private final RidePlanner planner;
    private final RideViewMapper views;
    private final RealtimeNotifier notifier;
    private final AppProperties properties;
    private final RideQueue queue = new RideQueue();

    public RideService(RideLock lock, RouteService routes, RideRepository rides, RickshawRepository rickshaws,
                       PassengerRepository passengers, RideMatchingService matching, RidePlanner planner,
                       RideViewMapper views, RealtimeNotifier notifier, AppProperties properties) {
        this.lock = lock;
        this.routes = routes;
        this.rides = rides;
        this.rickshaws = rickshaws;
        this.passengers = passengers;
        this.matching = matching;
        this.planner = planner;
        this.views = views;
        this.notifier = notifier;
        this.properties = properties;
    }

    /**
     * After loading the CSV files: rebuild each rickshaw's booked seats from its active
     * rides (cancelling any ride that would overbook it), and put every pending request
     * back in the queue so it is offered again.
     */
    @PostConstruct
    void restoreState() {
        lock.run(() -> {
            Map<String, Integer> booked = new HashMap<>();
            for (RideRequest ride : rides.findAll()) {
                if (ride.getStatus().isActive()) {
                    Rickshaw r = rickshaws.findById(ride.getRickshawId()).orElseThrow();
                    int seats = booked.getOrDefault(r.getId(), 0) + ride.getSeatsRequested();
                    if (seats > r.getMaxSeats()) {
                        ride.moveTo(RideStatus.CANCELLED);
                        ride.setStatusNote("Cancelled while restoring data: rickshaw was over capacity");
                        continue;
                    }
                    booked.put(r.getId(), seats);
                }
            }
            for (Rickshaw r : rickshaws.findAll()) {
                r.setBookedSeats(booked.getOrDefault(r.getId(), 0));
            }
            for (RideRequest ride : rides.findAll()) {
                if (ride.getStatus() == RideStatus.PENDING) {
                    ride.setOfferedRickshawId("");
                    ride.touch();
                    queue.add(ride);
                }
            }
            dispatch();
            rides.flush();
            rickshaws.flush();
        });
    }

    // ================================================================ passenger side

    /** Trip estimate for the booking panel (Dijkstra route + fare). */
    public RouteDto estimate(String fromId, String toId) {
        requireRoutable(fromId, "pickup");
        requireRoutable(toId, "destination");
        RouteResult route = routes.findRoute(fromId, toId);
        if (!route.isFound()) {
            throw new BadRequestException(route.getMessage());
        }
        double d = route.getTotalDistanceMeters();
        return new RouteDto(fromId, toId, route.getPath(), routes.roadNames(route.getPath()), d,
                RideViewMapper.minutes(d), FareCalculator.estimate(d, 1, false), FareCalculator.estimate(d, 1, true));
    }

    /** Available rickshaws for a trip, evaluated for route compatibility and merge-sorted. */
    public List<RickshawOptionDto> availableRickshaws(String pickupId, String destinationId, int seats, String sort) {
        requireRoutable(pickupId, "pickup");
        requireRoutable(destinationId, "destination");
        if (pickupId.equals(destinationId)) {
            throw new BadRequestException("Pickup and destination must be different.");
        }
        if (seats < 1 || seats > Rickshaw.DEFAULT_MAX_SEATS) {
            throw new BadRequestException("Seats must be between 1 and " + Rickshaw.DEFAULT_MAX_SEATS + ".");
        }
        return lock.call(() -> {
            List<RouteCompatibility.CompatibilityResult> ranked = matching.rankRickshaws(pickupId, destinationId,
                    seats, rickshaws.findAll(), rides.activeRidesByRickshaw(), reservedSeats(),
                    RickshawSorter.SortCriterion.fromParam(sort));
            double trip = routes.findRoute(pickupId, destinationId).getTotalDistanceMeters();
            int fare = FareCalculator.estimate(trip, seats, false);
            List<RickshawOptionDto> options = new ArrayList<>();
            int rank = 1;
            for (RouteCompatibility.CompatibilityResult c : ranked) {
                options.add(views.option(c, seats, fare, c.isCompatible() ? rank++ : 0));
            }
            return options;
        });
    }

    public RideDto requestRide(String passengerId, RideRequestBody body) {
        PassengerAccount passenger = passengers.findById(passengerId)
                .orElseThrow(() -> new NotFoundException("Passenger account not found."));
        String pickup = body.pickupId().trim();
        String destination = body.destinationId().trim();
        requireRoutable(pickup, "pickup");
        requireRoutable(destination, "destination");
        if (pickup.equals(destination)) {
            throw new BadRequestException("Pickup and destination must be different.");
        }
        RouteResult route = routes.findRoute(pickup, destination);
        if (!route.isFound()) {
            throw new BadRequestException(route.getMessage());
        }
        String preferred = body.preferredRickshawId() == null ? "" : body.preferredRickshawId().trim();
        if (!preferred.isEmpty() && rickshaws.findById(preferred).isEmpty()) {
            throw new BadRequestException("Unknown rickshaw " + preferred + ".");
        }

        return lock.call(() -> {
            if (currentRide(passengerId).isPresent()) {
                throw new ConflictException("You already have an open ride. Cancel or finish it first.");
            }
            long now = System.currentTimeMillis();
            RideRequest ride = new RideRequest(rides.nextId(), passenger.getId(), passenger.getFullName(),
                    passenger.getPassengerType(), pickup, destination, route.getTotalDistanceMeters(),
                    body.seats(), body.emergency(), RideStatus.PENDING, now);
            ride.setPreferredRickshawId(preferred);
            ride.setFare(FareCalculator.estimate(route.getTotalDistanceMeters(), body.seats(), false));
            ride.setStatusNote("Finding the best rickshaw for you");
            rides.add(ride);
            queue.add(ride);
            dispatch();
            rides.flush();
            return views.ride(ride, false);
        });
    }

    /** Passenger cancels a PENDING or ACCEPTED ride. */
    public RideDto cancelRide(String passengerId, int rideId) {
        return lock.call(() -> {
            RideRequest ride = requireRide(rideId);
            if (!ride.getPassengerId().equals(passengerId)) {
                throw new ForbiddenException("This is not your ride.");
            }
            RideStatus before = ride.getStatus();
            ride.moveTo(RideStatus.CANCELLED); // state machine rejects STARTED / final rides
            ride.setStatusNote("Cancelled by passenger");
            queue.remove(ride);
            String rickshawId = before == RideStatus.PENDING ? ride.getOfferedRickshawId() : ride.getRickshawId();
            ride.setOfferedRickshawId("");
            if (before.isActive()) {
                rickshaws.findById(ride.getRickshawId()).ifPresent(this::syncSeats);
                notifyRickshawPassengers(ride.getRickshawId(), "A co-passenger cancelled; your route was updated.");
            }
            if (!rickshawId.isEmpty()) {
                notifier.toDriverOfRickshaw(rickshawId, "driver_update",
                        msg(ride.getPassengerName() + " cancelled ride #" + ride.getId() + ".", ride));
            }
            dispatch();
            persist();
            return views.ride(ride, false);
        });
    }

    public Optional<RideRequest> currentRide(String passengerId) {
        return lock.call(() -> {
            RideRequest current = null;
            for (RideRequest r : rides.findByPassenger(passengerId)) {
                if (r.getStatus().isOpen() && (current == null || r.getId() > current.getId())) {
                    current = r;
                }
            }
            return Optional.ofNullable(current);
        });
    }

    public Optional<RideDto> currentRideView(String passengerId) {
        return lock.call(() -> currentRide(passengerId).map(r -> views.ride(r, false)));
    }

    /** Ride history, newest first (merge sort by request id). */
    public List<RideDto> passengerHistory(String passengerId) {
        return lock.call(() -> {
            List<RideRequest> list = MergeSort.sort(rides.findByPassenger(passengerId),
                    Comparator.comparingInt(RideRequest::getId).reversed());
            List<RideDto> out = new ArrayList<>();
            for (RideRequest r : list) {
                out.add(views.ride(r, false));
            }
            return out;
        });
    }

    /** A ride, visible only to its passenger and to the driver it is offered to or assigned to. */
    public RideDto viewRide(int rideId, String passengerId, String rickshawId) {
        return lock.call(() -> {
            RideRequest ride = requireRide(rideId);
            if (passengerId != null && ride.getPassengerId().equals(passengerId)) {
                return views.ride(ride, false);
            }
            if (rickshawId != null && (ride.getRickshawId().equals(rickshawId)
                    || ride.getOfferedRickshawId().equals(rickshawId))) {
                return views.ride(ride, true);
            }
            throw new ForbiddenException("You do not have access to this ride.");
        });
    }

    // ================================================================ driver side

    public DriverDashboardDto driverDashboard(String rickshawId) {
        return lock.call(() -> {
            Rickshaw r = requireRickshaw(rickshawId);
            List<OfferDto> offers = new ArrayList<>();
            long timeoutMs = properties.offerTimeoutSeconds() * 1000L;
            for (RideRequest ride : rides.findPendingOfferedTo(rickshawId)) {
                RouteCompatibility.CompatibilityResult c = matching.evaluate(r,
                        rides.findActiveByRickshaw(rickshawId), ride.getPickupId(), ride.getDestinationId(),
                        ride.getSeatsRequested(), r.getAvailableSeats());
                offers.add(new OfferDto(views.ride(ride, true),
                        c.isCompatible() ? c.getDistanceToPickupMeters() : -1,
                        c.isCompatible() ? c.getDetourMeters() : -1, c.isShared(),
                        ride.getOfferedAt() + timeoutMs, c.getSummary()));
            }
            List<RideDto> active = new ArrayList<>();
            for (RideRequest ride : rides.findActiveByRickshaw(rickshawId)) {
                active.add(views.ride(ride, true));
            }
            List<StopDto> stops = new ArrayList<>();
            for (RouteCompatibility.Stop s : planner.plan(r).getStops()) {
                stops.add(views.stop(s));
            }
            return new DriverDashboardDto(views.rickshawState(r), offers, active, stops, planner.path(r),
                    stops.isEmpty() ? null : stops.get(0), planner.isWaitingAtStop(r),
                    properties.offerTimeoutSeconds());
        });
    }

    public List<RideDto> driverHistory(String rickshawId) {
        return lock.call(() -> {
            List<RideRequest> list = MergeSort.sort(rides.findByRickshaw(rickshawId),
                    Comparator.comparingInt(RideRequest::getId).reversed());
            List<RideDto> out = new ArrayList<>();
            for (RideRequest r : list) {
                out.add(views.ride(r, true));
            }
            return out;
        });
    }

    public List<OfferDto> driverRequests(String rickshawId) {
        return driverDashboard(rickshawId).offers();
    }

    /** The driver accepts a request that was offered to them. */
    public RideDto acceptRide(String rickshawId, int rideId) {
        return lock.call(() -> {
            Rickshaw rickshaw = requireRickshaw(rickshawId);
            RideRequest ride = requireRide(rideId);
            if (ride.getStatus() != RideStatus.PENDING) {
                throw new ConflictException("Ride #" + rideId + " is no longer waiting for a driver ("
                        + ride.getStatus().getLabel() + ").");
            }
            if (!ride.getOfferedRickshawId().equals(rickshawId)) {
                throw new ConflictException("This request is not offered to you any more.");
            }
            if (!rickshaw.isOnline()) {
                throw new ConflictException("Go online before accepting rides.");
            }
            List<RideRequest> existing = rides.findActiveByRickshaw(rickshawId);
            // Re-check seats and route at the moment of acceptance: things may have changed since the offer.
            RouteCompatibility.CompatibilityResult check = matching.evaluate(rickshaw, existing,
                    ride.getPickupId(), ride.getDestinationId(), ride.getSeatsRequested(), rickshaw.getAvailableSeats());
            if (!check.isCompatible()) {
                ride.addRejectedBy(rickshawId);
                ride.setOfferedRickshawId("");
                ride.setStatusNote("Finding another rickshaw");
                queue.add(ride);
                dispatch();
                persist();
                throw new ConflictException("This ride no longer fits your rickshaw: " + check.getSummary()
                        + ". It was passed to another driver.");
            }

            ride.moveTo(RideStatus.ACCEPTED);
            queue.remove(ride);
            ride.setRickshawId(rickshawId);
            ride.setDetourMeters(check.getDetourMeters());
            boolean shared = !existing.isEmpty();
            ride.setShared(shared);
            ride.setFare(FareCalculator.estimate(ride.getDistanceMeters(), ride.getSeatsRequested(), shared));
            boolean here = !rickshaw.isMoving() && rickshaw.getLocationId().equals(ride.getPickupId());
            ride.setStatusNote(here ? "Your rickshaw is at the pickup point"
                    : rickshaw.getDriverName() + " is on the way");
            if (shared) {
                for (RideRequest other : existing) {
                    if (!other.isShared()) {
                        other.setShared(true);
                        other.setFare(FareCalculator.estimate(other.getDistanceMeters(), other.getSeatsRequested(), true));
                    }
                    notifier.toPassenger(other.getPassengerId(), "ride_update",
                            msg(firstName(ride.getPassengerName()) + " is joining your rickshaw (shared ride discount applied).", other));
                }
            }
            syncSeats(rickshaw);
            notifier.toPassenger(ride.getPassengerId(), "ride_update",
                    msg(rickshaw.getDriverName() + " accepted your ride.", ride));
            dispatch(); // seat counts changed: other queued requests may need a different rickshaw
            persist();
            return views.ride(ride, true);
        });
    }

    /** The driver rejects an offered request; it is automatically sent to the next most suitable rickshaw. */
    public RideDto rejectRide(String rickshawId, int rideId) {
        return lock.call(() -> {
            requireRickshaw(rickshawId);
            RideRequest ride = requireRide(rideId);
            if (ride.getStatus() != RideStatus.PENDING || !ride.getOfferedRickshawId().equals(rickshawId)) {
                throw new ConflictException("This request is not offered to you any more.");
            }
            passToNextDriver(ride, rickshawId, "Driver unavailable. Finding another rickshaw");
            persist();
            return views.ride(ride, true);
        });
    }

    /** Passenger boards: the rickshaw must be parked at the pickup point. */
    public RideDto startRide(String rickshawId, int rideId) {
        return lock.call(() -> {
            Rickshaw rickshaw = requireRickshaw(rickshawId);
            RideRequest ride = requireAssigned(rickshawId, rideId);
            if (ride.getStatus() == RideStatus.ACCEPTED
                    && (rickshaw.isMoving() || !rickshaw.getLocationId().equals(ride.getPickupId()))) {
                throw new ConflictException("Drive to " + routes.getLocationName(ride.getPickupId())
                        + " to pick up " + ride.getPassengerName() + " first.");
            }
            ride.moveTo(RideStatus.STARTED);
            ride.setStatusNote("Heading to " + routes.getLocationName(ride.getDestinationId()));
            notifier.toPassenger(ride.getPassengerId(), "ride_update", msg("Your trip has started.", ride));
            notifyRickshawPassengers(rickshawId, null);
            persist();
            return views.ride(ride, true);
        });
    }

    /** Passenger drops off: the rickshaw must be parked at the destination. Frees the seats. */
    public RideDto completeRide(String rickshawId, int rideId) {
        return lock.call(() -> {
            Rickshaw rickshaw = requireRickshaw(rickshawId);
            RideRequest ride = requireAssigned(rickshawId, rideId);
            if (ride.getStatus() == RideStatus.STARTED
                    && (rickshaw.isMoving() || !rickshaw.getLocationId().equals(ride.getDestinationId()))) {
                throw new ConflictException("Drive to " + routes.getLocationName(ride.getDestinationId())
                        + " before completing this ride.");
            }
            ride.moveTo(RideStatus.COMPLETED);
            ride.setStatusNote("Arrived at " + routes.getLocationName(ride.getDestinationId()));
            syncSeats(rickshaw);
            notifier.toPassenger(ride.getPassengerId(), "ride_update",
                    msg("You have arrived. Fare: " + ride.getFare() + " Tk.", ride));
            notifyRickshawPassengers(rickshawId, null);
            dispatch();
            persist();
            return views.ride(ride, true);
        });
    }

    public DriverDashboardDto setOnline(String rickshawId, boolean online) {
        lock.run(() -> {
            Rickshaw r = requireRickshaw(rickshawId);
            if (!online && !rides.findActiveByRickshaw(rickshawId).isEmpty()) {
                throw new ConflictException("Finish your active rides before going offline.");
            }
            r.setOnline(online);
            if (!online) {
                r.setAutoDrive(false);
                for (RideRequest offer : rides.findPendingOfferedTo(rickshawId)) {
                    offer.setOfferedRickshawId("");
                    offer.setStatusNote("Finding another rickshaw");
                    queue.add(offer);
                }
            }
            dispatch();
            persist();
        });
        return driverDashboard(rickshawId);
    }

    /** Driver dashboard settings: current location (only while empty), own destination, seats offered. */
    public DriverDashboardDto updateRickshawState(String rickshawId, String locationId, String targetId,
                                                  int availableSeats) {
        lock.run(() -> {
            Rickshaw r = requireRickshaw(rickshawId);
            requireRoutable(locationId, "current location");
            String target = targetId == null ? "" : targetId.trim();
            if (!target.isEmpty()) {
                requireRoutable(target, "destination");
            }
            boolean hasRides = !rides.findActiveByRickshaw(rickshawId).isEmpty();
            if (!locationId.equals(r.getLocationId())) {
                if (hasRides || r.isMoving()) {
                    throw new ConflictException("You can only change your location while parked with no passengers. "
                            + "Use Move to drive along the route.");
                }
                r.setLocationId(locationId);
            }
            int maxOffer = r.getMaxSeats() - r.getBookedSeats();
            if (availableSeats < 0 || availableSeats > maxOffer) {
                throw new BadRequestException("You can offer between 0 and " + maxOffer + " seats right now.");
            }
            r.setBlockedSeats(maxOffer - availableSeats);
            r.setTargetDestinationId(target);
            dispatch();
            persist();
        });
        return driverDashboard(rickshawId);
    }

    // ================================================================ dispatching

    /**
     * Offers every queued request to its best rickshaw, emergency requests first.
     * Must be called with the lock held.
     */
    private void dispatch() {
        Map<String, Integer> reserved = reservedSeats();
        Map<String, List<RideRequest>> active = rides.activeRidesByRickshaw();
        List<Rickshaw> all = rickshaws.findAll();
        for (RideRequest ride : queue.drainInOrder()) {
            if (ride.getStatus() != RideStatus.PENDING) {
                continue;
            }
            RouteCompatibility.CompatibilityResult best = matching.findBest(ride, all, active, reserved);
            if (best != null) {
                offer(ride, best);
                reserved.merge(best.getRickshaw().getId(), ride.getSeatsRequested(), Integer::sum);
                continue;
            }
            boolean anyLeft = matching.findBest(ride, all, active, Map.of()) != null;
            if (!anyLeft && !ride.getRejectedByRickshaws().isEmpty()) {
                reject(ride, "No nearby rickshaw accepted your request. Please try again.");
            } else {
                ride.setStatusNote(anyLeft ? "All nearby rickshaws are busy. You are in the queue"
                        + (ride.isEmergency() ? " (emergency priority)" : "")
                        : "Waiting for a rickshaw to come online");
                queue.add(ride);
            }
        }
    }

    private void offer(RideRequest ride, RouteCompatibility.CompatibilityResult best) {
        Rickshaw r = best.getRickshaw();
        ride.setOfferedRickshawId(r.getId());
        ride.setOfferedAt(System.currentTimeMillis());
        ride.setDetourMeters(best.getDetourMeters());
        ride.setStatusNote("Request sent to " + r.getDriverName());
        ride.touch();
        notifier.toDriverOfRickshaw(r.getId(), "driver_update",
                msg("New ride request from " + firstName(ride.getPassengerName())
                        + (ride.isEmergency() ? " (EMERGENCY)" : "") + ".", ride));
        notifier.toPassenger(ride.getPassengerId(), "ride_update", msg("Request sent to " + r.getDriverName() + ".", ride));
    }

    private void passToNextDriver(RideRequest ride, String rickshawId, String note) {
        ride.addRejectedBy(rickshawId);
        ride.setOfferedRickshawId("");
        ride.setStatusNote(note);
        ride.touch();
        queue.add(ride);
        dispatch();
        if (ride.getStatus() == RideStatus.PENDING) {
            notifier.toPassenger(ride.getPassengerId(), "ride_update", msg(ride.getStatusNote() + ".", ride));
        }
    }

    private void reject(RideRequest ride, String note) {
        ride.moveTo(RideStatus.REJECTED);
        ride.setStatusNote(note);
        ride.setOfferedRickshawId("");
        queue.remove(ride);
        notifier.toPassenger(ride.getPassengerId(), "ride_update", msg(note, ride));
    }

    /** Seats promised to outstanding offers, per rickshaw. */
    private Map<String, Integer> reservedSeats() {
        Map<String, Integer> reserved = new HashMap<>();
        for (RideRequest r : rides.findAll()) {
            if (r.getStatus() == RideStatus.PENDING && r.hasOffer()) {
                reserved.merge(r.getOfferedRickshawId(), r.getSeatsRequested(), Integer::sum);
            }
        }
        return reserved;
    }

    /**
     * Every few seconds: an offer the driver ignored past the timeout counts as a rejection,
     * and a request that has waited past the search timeout without any rickshaw is REJECTED.
     */
    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void expireStaleRequests() {
        lock.run(() -> {
            long now = System.currentTimeMillis();
            long offerMs = properties.offerTimeoutSeconds() * 1000L;
            long searchMs = properties.searchTimeoutSeconds() * 1000L;
            boolean changed = false;
            for (RideRequest ride : rides.findAll()) {
                if (ride.getStatus() != RideStatus.PENDING) {
                    continue;
                }
                if (ride.hasOffer() && now - ride.getOfferedAt() > offerMs) {
                    String rickshawId = ride.getOfferedRickshawId();
                    notifier.toDriverOfRickshaw(rickshawId, "driver_update",
                            msg("Request #" + ride.getId() + " expired.", ride));
                    passToNextDriver(ride, rickshawId, "Driver did not respond. Finding another rickshaw");
                    changed = true;
                } else if (!ride.hasOffer() && now - ride.getUpdatedAt() > searchMs) {
                    reject(ride, "No rickshaw became available. Please try again.");
                    changed = true;
                }
            }
            if (changed) {
                persist();
            }
        });
    }

    // ================================================================ helpers

    /** Booked seats = sum of the rickshaw's ACCEPTED + STARTED rides. */
    private void syncSeats(Rickshaw rickshaw) {
        int seats = 0;
        for (RideRequest r : rides.findActiveByRickshaw(rickshaw.getId())) {
            seats += r.getSeatsRequested();
        }
        rickshaw.setBookedSeats(seats);
    }

    private void notifyRickshawPassengers(String rickshawId, String message) {
        for (RideRequest r : rides.findActiveByRickshaw(rickshawId)) {
            notifier.toPassenger(r.getPassengerId(), "ride_update", msg(message, r));
        }
    }

    private void persist() {
        rides.flush();
        rickshaws.flush();
    }

    private RideRequest requireRide(int rideId) {
        return rides.findById(rideId).orElseThrow(() -> new NotFoundException("Ride #" + rideId + " does not exist."));
    }

    private Rickshaw requireRickshaw(String rickshawId) {
        return rickshaws.findById(rickshawId).orElseThrow(() -> new NotFoundException("Rickshaw not found."));
    }

    private RideRequest requireAssigned(String rickshawId, int rideId) {
        RideRequest ride = requireRide(rideId);
        if (!ride.getRickshawId().equals(rickshawId)) {
            throw new ForbiddenException("Ride #" + rideId + " is not assigned to your rickshaw.");
        }
        return ride;
    }

    private void requireRoutable(String id, String what) {
        if (id == null || !routes.isValidLocation(id)) {
            throw new BadRequestException("Choose a valid " + what + " on campus.");
        }
        if (!routes.isRoutable(id)) {
            throw new BadRequestException(routes.getLocationName(id) + " is not connected to the campus road network.");
        }
    }

    static Map<String, Object> msg(String message, RideRequest ride) {
        Map<String, Object> m = new HashMap<>();
        if (message != null) {
            m.put("message", message);
        }
        if (ride != null) {
            m.put("rideId", ride.getId());
            m.put("status", ride.getStatus().name());
        }
        return m;
    }

    private static String firstName(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        if (parts.length > 1 && parts[0].endsWith(".")) {
            return parts[1];
        }
        return parts.length == 0 ? "" : parts[0];
    }

    /** Snapshot of the dispatch queue (emergency first) for diagnostics and tests. */
    public List<Integer> queuedRideIds() {
        return lock.call(() -> {
            List<Integer> ids = new ArrayList<>();
            for (RideRequest r : queue.inProcessingOrder()) {
                ids.add(r.getId());
            }
            return ids;
        });
    }
}
