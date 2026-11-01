package campusrider.tracking;

import campusrider.algorithms.Dijkstra;
import campusrider.algorithms.RouteCompatibility;
import campusrider.algorithms.RouteResult;
import campusrider.config.AppProperties;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.RideRepository;
import campusrider.service.RouteService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The route a rickshaw is currently driving.
 *
 * The stop order comes from {@link RouteCompatibility#planForRides} (the same planner
 * used to accept shared passengers), and each leg is a Dijkstra shortest path on
 * the campus graph. Both the simulated movement and every map the passengers and
 * driver see are built from this one plan, so they always agree.
 */
@Component
public class RidePlanner {

    private final RouteService routes;
    private final RideRepository rides;
    private final AppProperties properties;

    public RidePlanner(RouteService routes, RideRepository rides, AppProperties properties) {
        this.routes = routes;
        this.rides = rides;
        this.properties = properties;
    }

    public RouteCompatibility.Plan plan(Rickshaw rickshaw) {
        List<RideRequest> active = rides.findActiveByRickshaw(rickshaw.getId());
        return RouteCompatibility.planForRides(routes.getGraph(), rickshaw.getRoutingOriginId(), active,
                properties.maxDetourMeters());
    }

    /** The location the rickshaw should drive to next: its first stop, or the driver's own destination. */
    public String nextTargetId(Rickshaw rickshaw) {
        List<RouteCompatibility.Stop> stops = plan(rickshaw).getStops();
        if (!stops.isEmpty()) {
            return stops.get(0).getLocationId();
        }
        String target = rickshaw.getTargetDestinationId();
        return target.isBlank() ? null : target;
    }

    /** Full vertex path of the plan, starting at the rickshaw's routing origin. */
    public List<String> path(Rickshaw rickshaw) {
        RouteCompatibility.Plan plan = plan(rickshaw);
        if (!plan.getStops().isEmpty()) {
            return RouteCompatibility.expandPath(routes.getGraph(), rickshaw.getRoutingOriginId(), plan.getStops());
        }
        String target = rickshaw.getTargetDestinationId();
        if (!target.isBlank() && !target.equals(rickshaw.getRoutingOriginId())) {
            RouteResult r = Dijkstra.findShortestPath(routes.getGraph(), rickshaw.getRoutingOriginId(), target);
            if (r.isFound()) {
                return r.getPath();
            }
        }
        return List.of(rickshaw.getRoutingOriginId());
    }

    /** Rides that wait for a driver action at the rickshaw's current vertex (start at pickup / complete at drop-off). */
    public List<RideRequest> actionsHere(Rickshaw rickshaw) {
        List<RideRequest> list = new ArrayList<>();
        if (rickshaw.isMoving()) {
            return list;
        }
        for (RideRequest r : rides.findActiveByRickshaw(rickshaw.getId())) {
            boolean pickupHere = r.getStatus() == RideStatus.ACCEPTED && r.getPickupId().equals(rickshaw.getLocationId());
            boolean dropHere = r.getStatus() == RideStatus.STARTED && r.getDestinationId().equals(rickshaw.getLocationId());
            if (pickupHere || dropHere) {
                list.add(r);
            }
        }
        return list;
    }

    /** True when the next stop is right here, so the rickshaw must wait for the driver to start/complete. */
    public boolean isWaitingAtStop(Rickshaw rickshaw) {
        String next = nextTargetId(rickshaw);
        return next != null && !rickshaw.isMoving() && next.equals(rickshaw.getLocationId())
                && !actionsHere(rickshaw).isEmpty();
    }

    /** Paths for one passenger's view: rickshaw -> pickup (approach) and pickup -> drop-off (trip). */
    public RidePaths pathsFor(RideRequest ride, Rickshaw rickshaw) {
        if (rickshaw == null || !ride.getStatus().isActive()) {
            RouteResult trip = routes.findRoute(ride.getPickupId(), ride.getDestinationId());
            List<String> approach = List.of();
            double toPickup = -1;
            if (rickshaw != null) {
                RouteResult a = routes.findRoute(rickshaw.getRoutingOriginId(), ride.getPickupId());
                approach = a.isFound() ? a.getPath() : List.of();
                toPickup = a.isFound() ? a.getTotalDistanceMeters() + remainingOnSegment(rickshaw) : -1;
            }
            return new RidePaths(approach, trip.isFound() ? trip.getPath() : List.of(), toPickup,
                    trip.isFound() ? trip.getTotalDistanceMeters() : -1);
        }

        // Active ride: walk the shared plan stop by stop.
        String origin = rickshaw.getRoutingOriginId();
        List<RouteCompatibility.Stop> stops = plan(rickshaw).getStops();
        List<String> approach = new ArrayList<>();
        List<String> trip = new ArrayList<>();
        approach.add(origin);
        boolean onBoard = ride.getStatus() == RideStatus.STARTED;
        if (onBoard) {
            trip.add(origin);
        }
        double metersToPickup = onBoard ? 0 : remainingOnSegment(rickshaw);
        double metersToDrop = remainingOnSegment(rickshaw);
        String at = origin;
        for (RouteCompatibility.Stop stop : stops) {
            RouteResult leg = routes.findRoute(at, stop.getLocationId());
            List<String> legPath = leg.isFound() ? leg.getPath() : List.of(at);
            double legMeters = leg.isFound() ? leg.getTotalDistanceMeters() : 0;
            List<String> tail = legPath.subList(1, legPath.size());
            if (!onBoard) {
                approach.addAll(tail);
                metersToPickup += legMeters;
            } else {
                trip.addAll(tail);
            }
            metersToDrop += legMeters;
            at = stop.getLocationId();
            if (stop.getRideId() == ride.getId()) {
                if (stop.getType() == RouteCompatibility.StopType.PICKUP) {
                    onBoard = true;
                    trip.add(at);
                } else {
                    break;
                }
            }
        }
        if (ride.getStatus() == RideStatus.STARTED) {
            approach = List.of();
            metersToPickup = 0;
        }
        return new RidePaths(approach, trip, metersToPickup, metersToDrop);
    }

    /** Meters left on the road the rickshaw is currently driving (0 when parked). */
    public double remainingOnSegment(Rickshaw rickshaw) {
        if (!rickshaw.isMoving()) {
            return 0;
        }
        double length = routes.roadLength(rickshaw.getLocationId(), rickshaw.getMovingToId());
        return length <= 0 ? 0 : length * (1.0 - rickshaw.getSegmentProgress());
    }

    /**
     * @param approachPath    vertices from the rickshaw to the pickup (empty once picked up)
     * @param tripPath        vertices from the pickup (or the rickshaw, once on board) to the drop-off
     * @param metersToPickup  driving distance until the pickup (-1 when unknown)
     * @param metersToDropoff driving distance until this passenger's drop-off
     */
    public record RidePaths(List<String> approachPath, List<String> tripPath,
                            double metersToPickup, double metersToDropoff) {
    }
}
