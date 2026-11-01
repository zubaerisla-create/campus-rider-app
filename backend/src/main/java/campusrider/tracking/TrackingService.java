package campusrider.tracking;

import campusrider.config.AppProperties;
import campusrider.dto.RickshawPositionDto;
import campusrider.exception.ConflictException;
import campusrider.exception.NotFoundException;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.RickshawRepository;
import campusrider.repository.RideRepository;
import campusrider.service.RideLock;
import campusrider.service.RideViewMapper;
import campusrider.service.RouteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulated live tracking (no real GPS).
 *
 * A rickshaw only ever moves along roads of the campus Graph, following the
 * Dijkstra path to the next stop of its RidePlanner plan:
 *   - Manual move: the driver presses "Move" and the rickshaw jumps to the next
 *     vertex on its path (or finishes the road it is on).
 *   - Auto drive: every tick the rickshaw advances speed * dt meters along the
 *     current road; at the end of a road it continues on the next one.
 * The rickshaw stops at a pickup / drop-off until the driver starts / completes
 * that ride, then auto drive carries on to the next stop.
 *
 * After every change the new positions are pushed to all browsers over the
 * WebSocket ("positions" event), so passengers sharing a rickshaw all see the
 * same position at the same time.
 */
@Service
public class TrackingService {

    private final RideLock lock;
    private final RickshawRepository rickshaws;
    private final RideRepository rides;
    private final RidePlanner planner;
    private final RouteService routes;
    private final RideViewMapper views;
    private final RealtimeNotifier notifier;
    private final AppProperties properties;
    private long lastTick = System.currentTimeMillis();
    private long lastBroadcast;

    public TrackingService(RideLock lock, RickshawRepository rickshaws, RideRepository rides, RidePlanner planner,
                           RouteService routes, RideViewMapper views, RealtimeNotifier notifier,
                           AppProperties properties) {
        this.lock = lock;
        this.rickshaws = rickshaws;
        this.rides = rides;
        this.planner = planner;
        this.routes = routes;
        this.views = views;
        this.notifier = notifier;
        this.properties = properties;
    }

    /** Driver presses "Move to next location". */
    public String moveStep(String rickshawId) {
        String message = lock.call(() -> {
            Rickshaw r = require(rickshawId);
            if (!r.isOnline()) {
                throw new ConflictException("Go online to drive.");
            }
            if (r.isMoving()) {
                arrive(r, r.getMovingToId());
                return "Arrived at " + routes.getLocationName(r.getLocationId()) + ".";
            }
            String next = nextVertex(r);
            arrive(r, next);
            return "Moved to " + routes.getLocationName(next) + ".";
        });
        broadcastPositions();
        return message;
    }

    /** Driver turns automatic simulated driving on or off. */
    public boolean setAutoDrive(String rickshawId, boolean enabled) {
        lock.run(() -> {
            Rickshaw r = require(rickshawId);
            if (enabled) {
                if (!r.isOnline()) {
                    throw new ConflictException("Go online to drive.");
                }
                if (planner.nextTargetId(r) == null) {
                    throw new ConflictException("Nothing to drive to: accept a ride or set a destination.");
                }
            }
            r.setAutoDrive(enabled);
        });
        broadcastPositions();
        return enabled;
    }

    /** Advances every auto-driving rickshaw by speed * elapsed time. */
    @Scheduled(fixedDelayString = "${campusrider.simulation-tick-ms:500}")
    public void tick() {
        long now = System.currentTimeMillis();
        double seconds = Math.min(2.0, (now - lastTick) / 1000.0);
        lastTick = now;
        boolean moved = lock.call(() -> {
            boolean any = false;
            for (Rickshaw r : rickshaws.findAll()) {
                if (r.isAutoDrive() && r.isOnline()) {
                    any |= advance(r, properties.simulationSpeedMps() * seconds);
                }
            }
            return any;
        });
        // Push on every movement, and every few seconds anyway so seat counts and
        // online status on everyone's map stay fresh without polling.
        if (moved || now - lastBroadcast > 3000) {
            lastBroadcast = now;
            broadcastPositions();
        }
    }

    /** Moves one rickshaw up to {@code meters} along its route. Returns true if it moved. */
    boolean advance(Rickshaw r, double meters) {
        if (!r.isMoving()) {
            String target = planner.nextTargetId(r);
            if (target == null || (target.equals(r.getLocationId()) && rides.findActiveByRickshaw(r.getId()).isEmpty())) {
                r.setAutoDrive(false); // reached the driver's own destination with nobody on board
                return true;
            }
            if (target.equals(r.getLocationId())) {
                return false; // waiting at a stop for the driver to start / complete a ride
            }
            String next = routes.findRoute(r.getLocationId(), target).getPath().get(1);
            r.setMovement(next, 0.0);
        }
        double length = routes.roadLength(r.getLocationId(), r.getMovingToId());
        double progress = r.getSegmentProgress() + (length <= 0 ? 1.0 : meters / length);
        if (progress >= 1.0) {
            arrive(r, r.getMovingToId());
        } else {
            r.setMovement(r.getMovingToId(), progress);
        }
        return true;
    }

    private String nextVertex(Rickshaw r) {
        String target = planner.nextTargetId(r);
        if (target == null) {
            throw new ConflictException("Nothing to drive to: accept a ride or set a destination.");
        }
        if (target.equals(r.getLocationId())) {
            if (!planner.actionsHere(r).isEmpty()) {
                RideRequest waiting = planner.actionsHere(r).get(0);
                throw new ConflictException(waiting.getStatus() == RideStatus.ACCEPTED
                        ? "You are at the pickup. Start the ride for " + waiting.getPassengerName() + " first."
                        : "You are at the drop-off. Complete the ride for " + waiting.getPassengerName() + " first.");
            }
            throw new ConflictException("You have reached your destination.");
        }
        return routes.findRoute(r.getLocationId(), target).getPath().get(1);
    }

    /** The rickshaw reaches a vertex: park it there, save, and tell the riders about arrivals. */
    private void arrive(Rickshaw r, String vertexId) {
        r.setLocationId(vertexId);
        rickshaws.flush();
        String place = routes.getLocationName(vertexId);
        boolean noteChanged = false;
        for (RideRequest ride : rides.findActiveByRickshaw(r.getId())) {
            if (ride.getStatus() == RideStatus.ACCEPTED && ride.getPickupId().equals(vertexId)) {
                ride.setStatusNote("Your rickshaw has arrived at " + place);
                noteChanged = true;
                notifier.toPassenger(ride.getPassengerId(), "ride_update", event(
                        "Your rickshaw has arrived at " + place + ".", ride));
                notifier.toDriverOfRickshaw(r.getId(), "driver_update", event(
                        "At pickup: start the ride for " + ride.getPassengerName() + ".", ride));
            } else if (ride.getStatus() == RideStatus.STARTED && ride.getDestinationId().equals(vertexId)) {
                ride.setStatusNote("Arrived at " + place);
                noteChanged = true;
                notifier.toPassenger(ride.getPassengerId(), "ride_update", event(
                        "You have reached " + place + ".", ride));
                notifier.toDriverOfRickshaw(r.getId(), "driver_update", event(
                        "At drop-off: complete the ride for " + ride.getPassengerName() + ".", ride));
            }
        }
        if (noteChanged) {
            rides.flush();
        }
    }

    public List<RickshawPositionDto> positions() {
        return lock.call(() -> {
            List<RickshawPositionDto> list = new ArrayList<>();
            for (Rickshaw r : rickshaws.findAll()) {
                list.add(views.position(r));
            }
            return list;
        });
    }

    public void broadcastPositions() {
        Map<String, Object> data = new HashMap<>();
        data.put("rickshaws", positions());
        notifier.broadcast("positions", data);
    }

    private Rickshaw require(String rickshawId) {
        return rickshaws.findById(rickshawId).orElseThrow(() -> new NotFoundException("Rickshaw not found."));
    }

    private static Map<String, Object> event(String message, RideRequest ride) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", message);
        m.put("rideId", ride.getId());
        m.put("status", ride.getStatus().name());
        return m;
    }
}
