package campusrider.algorithms;

import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multi-Passenger Route Compatibility Algorithm.
 *
 * Decides whether a rickshaw (empty, heading to a driver-chosen destination, or
 * already carrying 1-2 passengers) can also take a new passenger from
 * pickupId to destinationId, and what that costs.
 *
 * Steps for one rickshaw:
 *   1. Seats       - the rickshaw must have enough free seats (max 3 per rickshaw).
 *   2. Pickup      - Dijkstra distance from the rickshaw to the pickup must be
 *                    within maxPickupMeters (pickup compatibility); after planning, the
 *                    driving done before this pickup must be within it too.
 *   3. Baseline    - best stop order for the passengers it already has (see planStops).
 *   4. With new    - best stop order after inserting the new pickup and drop-off.
 *   5. Detour      - additional distance = withNew - baseline (extra meters driven);
 *                    detour = the largest delay to any existing passenger's arrival
 *                    (or, for an empty rickshaw heading to the driver's own destination,
 *                    the extra meters beyond that route).
 *   6. Fairness    - in the chosen order, every passenger (existing and new) may
 *                    ride at most maxDetourMeters more than their own shortest path.
 *   7. Threshold   - the extra detour must also be within maxDetourMeters.
 * Only if all checks pass is the rickshaw "compatible".
 *
 * planStops: a rickshaw with k passengers has at most 2k <= 6 stops. We try every
 * order in which each pickup comes before its own drop-off (at most 6!/2^3 = 90
 * orders) with depth-first search + branch-and-bound, using Dijkstra for every
 * leg (memoised). Time per rickshaw: O(S! * S) for S <= 6 stops plus
 * O(S^2 (V + E) log V) Dijkstra work - tiny, and exact rather than a heuristic.
 *
 * Used by: RideMatchingService (ranking rickshaws for a request, re-checking on
 * accept) and TrackingService (the order in which the rickshaw drives its stops).
 */
public final class RouteCompatibility {

    public static final double DEFAULT_MAX_DETOUR_METERS = 750.0;
    public static final double DEFAULT_MAX_PICKUP_METERS = 2500.0;

    public enum StopType { PICKUP, DROPOFF }

    /** One stop on a rickshaw's plan. rideId 0 means "the new passenger being evaluated". */
    public static final class Stop {
        private final int rideId;
        private final String locationId;
        private final StopType type;

        public Stop(int rideId, String locationId, StopType type) {
            this.rideId = rideId;
            this.locationId = locationId;
            this.type = type;
        }

        public int getRideId() {
            return rideId;
        }

        public String getLocationId() {
            return locationId;
        }

        public StopType getType() {
            return type;
        }
    }

    /** A passenger as seen by the planner. onBoard passengers only still need a drop-off. */
    public static final class Leg {
        final int rideId;
        final String pickupId;
        final String destinationId;
        final boolean onBoard;

        public Leg(int rideId, String pickupId, String destinationId, boolean onBoard) {
            this.rideId = rideId;
            this.pickupId = pickupId;
            this.destinationId = destinationId;
            this.onBoard = onBoard;
        }

        public static Leg of(RideRequest ride) {
            return new Leg(ride.getId(), ride.getPickupId(), ride.getDestinationId(),
                    ride.getStatus() == RideStatus.STARTED);
        }
    }

    /** The best stop order found for a set of passengers. */
    public static final class Plan {
        private final List<Stop> stops;
        private final double totalMeters;
        private final boolean withinDetourLimit;
        private final Map<Integer, Double> riderDetours;
        private final Map<Integer, Double> metersUntilPickup;
        private final Map<Integer, Double> metersUntilDropoff;

        Plan(List<Stop> stops, double totalMeters, boolean withinDetourLimit, Map<Integer, Double> riderDetours,
             Map<Integer, Double> metersUntilPickup, Map<Integer, Double> metersUntilDropoff) {
            this.stops = List.copyOf(stops);
            this.totalMeters = totalMeters;
            this.withinDetourLimit = withinDetourLimit;
            this.riderDetours = riderDetours;
            this.metersUntilPickup = metersUntilPickup;
            this.metersUntilDropoff = metersUntilDropoff;
        }

        public List<Stop> getStops() {
            return stops;
        }

        public double getTotalMeters() {
            return totalMeters;
        }

        public boolean isWithinDetourLimit() {
            return withinDetourLimit;
        }

        /** Extra meters each passenger rides compared with their own shortest path. */
        public Map<Integer, Double> getRiderDetours() {
            return riderDetours;
        }

        /** Meters the rickshaw drives before reaching each passenger's pickup. */
        public Map<Integer, Double> getMetersUntilPickup() {
            return metersUntilPickup;
        }

        /** Meters the rickshaw drives before each passenger's drop-off (their arrival). */
        public Map<Integer, Double> getMetersUntilDropoff() {
            return metersUntilDropoff;
        }

        public boolean isFound() {
            return !Double.isInfinite(totalMeters);
        }
    }

    public static final class CompatibilityResult {
        private final Rickshaw rickshaw;
        private final boolean compatible;
        private final boolean pickupOnCurrentRoute;
        private final boolean shared;
        private final double distanceToPickupMeters;
        private final double passengerTripMeters;
        private final double detourMeters;
        private final double additionalMeters;
        private final String summary;
        private final Plan plan;

        public CompatibilityResult(Rickshaw rickshaw, boolean compatible, boolean pickupOnCurrentRoute,
                                   boolean shared, double distanceToPickupMeters, double passengerTripMeters,
                                   double detourMeters, double additionalMeters, String summary, Plan plan) {
            this.rickshaw = rickshaw;
            this.compatible = compatible;
            this.pickupOnCurrentRoute = pickupOnCurrentRoute;
            this.shared = shared;
            this.distanceToPickupMeters = distanceToPickupMeters;
            this.passengerTripMeters = passengerTripMeters;
            this.detourMeters = detourMeters;
            this.additionalMeters = additionalMeters;
            this.summary = summary;
            this.plan = plan;
        }

        static CompatibilityResult incompatible(Rickshaw rickshaw, boolean shared, String reason) {
            return new CompatibilityResult(rickshaw, false, false, shared, Double.POSITIVE_INFINITY,
                    0.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, reason, null);
        }

        public Rickshaw getRickshaw() {
            return rickshaw;
        }

        public boolean isCompatible() {
            return compatible;
        }

        public boolean isPickupOnCurrentRoute() {
            return pickupOnCurrentRoute;
        }

        public boolean isShared() {
            return shared;
        }

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }

        public double getPassengerTripMeters() {
            return passengerTripMeters;
        }

        public double getDetourMeters() {
            return detourMeters;
        }

        /** Extra meters the rickshaw drives in total once this passenger is added (incl. their trip). */
        public double getAdditionalMeters() {
            return additionalMeters;
        }

        public String getSummary() {
            return summary;
        }

        /** The stop order the rickshaw would drive if this passenger is added (null if incompatible). */
        public Plan getPlan() {
            return plan;
        }
    }

    private RouteCompatibility() {
    }

    /** Evaluates with the default thresholds. */
    public static CompatibilityResult evaluate(CampusGraph graph, Rickshaw rickshaw,
                                               List<RideRequest> activeRidesOnRickshaw,
                                               String pickupId, String destinationId, int seatsNeeded) {
        return evaluate(graph, rickshaw, activeRidesOnRickshaw, pickupId, destinationId, seatsNeeded,
                rickshaw == null ? 0 : rickshaw.getAvailableSeats(),
                DEFAULT_MAX_DETOUR_METERS, DEFAULT_MAX_PICKUP_METERS);
    }

    /**
     * @param seatsFree seats that may be given to this passenger (the caller can subtract seats
     *                  promised to other outstanding offers)
     */
    public static CompatibilityResult evaluate(CampusGraph graph, Rickshaw rickshaw,
                                               List<RideRequest> activeRidesOnRickshaw,
                                               String pickupId, String destinationId, int seatsNeeded,
                                               int seatsFree, double maxDetourMeters, double maxPickupMeters) {
        List<RideRequest> active = activeRidesOnRickshaw == null ? List.of() : activeRidesOnRickshaw;
        boolean shared = !active.isEmpty();
        if (rickshaw == null) {
            return CompatibilityResult.incompatible(null, false, "Unknown rickshaw");
        }
        // 1. Seats
        if (seatsNeeded < 1 || seatsFree < seatsNeeded) {
            return CompatibilityResult.incompatible(rickshaw, shared, "Not enough free seats");
        }

        DistanceOracle distances = new DistanceOracle(graph);
        String origin = rickshaw.getRoutingOriginId();
        double toPickupDirect = distances.distance(origin, pickupId);
        double tripMeters = distances.distance(pickupId, destinationId);
        if (Double.isInfinite(toPickupDirect) || Double.isInfinite(tripMeters)) {
            return CompatibilityResult.incompatible(rickshaw, shared, "Unreachable route");
        }

        // 2. Pickup compatibility
        if (toPickupDirect > maxPickupMeters) {
            return CompatibilityResult.incompatible(rickshaw, shared,
                    String.format(Locale.ROOT, "Too far from pickup (%.0f m)", toPickupDirect));
        }

        // 3. Baseline: the rickshaw's existing commitments (passengers, or the driver's own target).
        List<Leg> legs = new ArrayList<>();
        for (RideRequest r : active) {
            legs.add(Leg.of(r));
        }
        String driverTarget = active.isEmpty() ? rickshaw.getTargetDestinationId() : "";
        boolean hasDriverTarget = !driverTarget.isBlank() && !driverTarget.equals(origin)
                && graph.hasLocation(driverTarget);

        double baselineMeters;
        Map<Integer, Double> baselineArrival = Map.of();
        Set<String> baselineNodes = new LinkedHashSet<>();
        if (!legs.isEmpty()) {
            Plan baseline = planStops(distances, origin, legs, Double.POSITIVE_INFINITY);
            baselineMeters = baseline.getTotalMeters();
            baselineArrival = baseline.getMetersUntilDropoff();
            baselineNodes.addAll(expandPath(graph, origin, baseline.getStops()));
        } else if (hasDriverTarget) {
            baselineMeters = distances.distance(origin, driverTarget);
            baselineNodes.addAll(distances.route(origin, driverTarget).getPath());
        } else {
            baselineMeters = 0.0;
        }
        boolean hasRoute = !baselineNodes.isEmpty();
        boolean pickupOnRoute = hasRoute && baselineNodes.contains(pickupId);

        // 4. Best order including the new passenger (rideId 0), with per-passenger fairness (6).
        List<Leg> withNew = new ArrayList<>(legs);
        withNew.add(new Leg(0, pickupId, destinationId, false));
        Plan plan = planStops(distances, origin, withNew, maxDetourMeters);
        if (!plan.isFound()) {
            return CompatibilityResult.incompatible(rickshaw, shared, "Unreachable route");
        }
        double withNewMeters = plan.getTotalMeters();
        if (hasDriverTarget) {
            // Idle rickshaw going somewhere: after the drop-off it continues to its own target.
            withNewMeters += distances.distance(destinationId, driverTarget);
        }

        // 5. Additional distance the rickshaw drives, and the detour it imposes:
        //    shared ride   -> the largest delay to any existing passenger's arrival
        //                     (extra meters before their drop-off, covering wait + ride)
        //    driver target -> the extra meters beyond the driver's own route
        //    free rickshaw -> 0 (there is no existing route to deviate from)
        double additional = Math.max(0.0, withNewMeters - baselineMeters);
        double detour;
        if (shared) {
            detour = 0.0;
            for (Map.Entry<Integer, Double> e : plan.getMetersUntilDropoff().entrySet()) {
                if (e.getKey() != 0) {
                    detour = Math.max(detour, e.getValue() - baselineArrival.getOrDefault(e.getKey(), e.getValue()));
                }
            }
        } else {
            detour = hasDriverTarget ? additional : 0.0;
        }
        double waitMeters = plan.getMetersUntilPickup().getOrDefault(0, toPickupDirect);

        // 6 + 7. Fairness for every rider, the global threshold, and the new rider's wait:
        // the planned order may serve existing riders first, so the wait must also be within range.
        boolean fair = plan.isWithinDetourLimit();
        boolean waitOk = waitMeters <= maxPickupMeters;
        boolean ok = fair && waitOk && detour <= maxDetourMeters;

        String summary;
        if (!waitOk) {
            summary = String.format(Locale.ROOT, "Pickup would be too late (%.0f m of driving first)", waitMeters);
        } else if (!fair) {
            summary = "Would delay existing passengers too much";
        } else if (!ok) {
            summary = String.format(Locale.ROOT, "Detour too long (+%.0f m)", detour);
        } else if (shared) {
            summary = String.format(Locale.ROOT, "Shared ride, +%.0f m detour%s", detour,
                    pickupOnRoute ? ", pickup on route" : "");
        } else if (hasDriverTarget) {
            summary = String.format(Locale.ROOT, "Heading your way, +%.0f m detour", detour);
        } else {
            summary = "Free rickshaw";
        }
        return new CompatibilityResult(rickshaw, ok, pickupOnRoute, shared, waitMeters, tripMeters,
                detour, additional, summary, ok ? plan : null);
    }

    /** Best stop order for the passengers already on a rickshaw (used by live tracking). */
    public static Plan planForRides(CampusGraph graph, String origin, List<RideRequest> activeRides,
                                    double maxDetourMeters) {
        List<Leg> legs = new ArrayList<>();
        for (RideRequest r : activeRides) {
            legs.add(Leg.of(r));
        }
        DistanceOracle distances = new DistanceOracle(graph);
        Plan plan = planStops(distances, origin, legs, maxDetourMeters);
        if (!plan.isFound() || !plan.isWithinDetourLimit()) {
            // Fall back to the shortest order if the limit can no longer be met (e.g. manual moves).
            plan = planStops(distances, origin, legs, Double.POSITIVE_INFINITY);
        }
        return plan;
    }

    /**
     * Depth-first search over all precedence-valid stop orders (pickup before drop-off),
     * keeping the shortest one whose riders all stay within maxDetourMeters. If no order
     * satisfies the limit, the shortest overall order is returned with withinDetourLimit=false.
     */
    static Plan planStops(DistanceOracle distances, String origin, List<Leg> legs, double maxDetourMeters) {
        if (legs.isEmpty()) {
            return new Plan(List.of(), 0.0, true, Map.of(), Map.of(), Map.of());
        }
        Search search = new Search(distances, origin, legs, maxDetourMeters);
        search.run();
        if (search.bestFair != null) {
            return search.build(search.bestFair, true);
        }
        if (search.bestAny != null) {
            return search.build(search.bestAny, false);
        }
        return new Plan(List.of(), Double.POSITIVE_INFINITY, false, Map.of(), Map.of(), Map.of());
    }

    /** Concatenates Dijkstra paths between consecutive stops into one vertex path. */
    public static List<String> expandPath(CampusGraph graph, String origin, List<Stop> stops) {
        List<String> path = new ArrayList<>();
        path.add(origin);
        String current = origin;
        for (Stop stop : stops) {
            if (current.equals(stop.getLocationId())) {
                continue;
            }
            RouteResult leg = Dijkstra.findShortestPath(graph, current, stop.getLocationId());
            if (!leg.isFound()) {
                break;
            }
            path.addAll(leg.getPath().subList(1, leg.getPath().size()));
            current = stop.getLocationId();
        }
        return path;
    }

    // ------------------------------------------------------------------ internals

    /** Memoised Dijkstra distances for one evaluation. */
    static final class DistanceOracle {
        private final CampusGraph graph;
        private final Map<String, RouteResult> cache = new HashMap<>();

        DistanceOracle(CampusGraph graph) {
            this.graph = graph;
        }

        RouteResult route(String from, String to) {
            return cache.computeIfAbsent(from + "|" + to, k -> Dijkstra.findShortestPath(graph, from, to));
        }

        double distance(String from, String to) {
            if (from.equals(to)) {
                return 0.0;
            }
            RouteResult r = route(from, to);
            return r.isFound() ? r.getTotalDistanceMeters() : Double.POSITIVE_INFINITY;
        }
    }

    /** Branch-and-bound DFS state. */
    private static final class Search {
        private final DistanceOracle d;
        private final String origin;
        private final List<Leg> legs;
        private final double maxDetour;
        private final List<Stop> current = new ArrayList<>();
        private final boolean[] pickedUp;
        private final boolean[] droppedOff;
        private List<Stop> bestFair;
        private double bestFairMeters = Double.POSITIVE_INFINITY;
        private List<Stop> bestAny;
        private double bestAnyMeters = Double.POSITIVE_INFINITY;

        Search(DistanceOracle d, String origin, List<Leg> legs, double maxDetour) {
            this.d = d;
            this.origin = origin;
            this.legs = legs;
            this.maxDetour = maxDetour;
            this.pickedUp = new boolean[legs.size()];
            this.droppedOff = new boolean[legs.size()];
            for (int i = 0; i < legs.size(); i++) {
                pickedUp[i] = legs.get(i).onBoard;
            }
        }

        void run() {
            dfs(origin, 0.0);
        }

        private void dfs(String at, double meters) {
            if (meters >= bestAnyMeters && meters >= bestFairMeters) {
                return; // cannot improve either answer
            }
            int needed = 0;
            for (int i = 0; i < legs.size(); i++) {
                needed += (pickedUp[i] ? 0 : 1) + (droppedOff[i] ? 0 : 1);
            }
            if (needed == 0) {
                if (meters < bestAnyMeters) {
                    bestAnyMeters = meters;
                    bestAny = new ArrayList<>(current);
                }
                if (meters < bestFairMeters && riderDetoursOk(current)) {
                    bestFairMeters = meters;
                    bestFair = new ArrayList<>(current);
                }
                return;
            }
            for (int i = 0; i < legs.size(); i++) {
                Leg leg = legs.get(i);
                if (!pickedUp[i]) {
                    double step = d.distance(at, leg.pickupId);
                    if (Double.isInfinite(step)) {
                        continue;
                    }
                    pickedUp[i] = true;
                    current.add(new Stop(leg.rideId, leg.pickupId, StopType.PICKUP));
                    dfs(leg.pickupId, meters + step);
                    current.remove(current.size() - 1);
                    pickedUp[i] = false;
                } else if (!droppedOff[i]) {
                    double step = d.distance(at, leg.destinationId);
                    if (Double.isInfinite(step)) {
                        continue;
                    }
                    droppedOff[i] = true;
                    current.add(new Stop(leg.rideId, leg.destinationId, StopType.DROPOFF));
                    dfs(leg.destinationId, meters + step);
                    current.remove(current.size() - 1);
                    droppedOff[i] = false;
                }
            }
        }

        private boolean riderDetoursOk(List<Stop> order) {
            if (Double.isInfinite(maxDetour)) {
                return true;
            }
            for (double detour : riderDetours(order).values()) {
                if (detour > maxDetour + 1e-6) {
                    return false;
                }
            }
            return true;
        }

        /** For each rider: meters ridden in this order minus their own shortest path. */
        private Map<Integer, Double> riderDetours(List<Stop> order) {
            Map<Integer, Double> boardedAt = new HashMap<>();
            Map<Integer, Double> result = new HashMap<>();
            for (Leg leg : legs) {
                if (leg.onBoard) {
                    boardedAt.put(leg.rideId, 0.0);
                }
            }
            String at = origin;
            double meters = 0.0;
            for (Stop stop : order) {
                meters += d.distance(at, stop.getLocationId());
                at = stop.getLocationId();
                if (stop.getType() == StopType.PICKUP) {
                    boardedAt.put(stop.getRideId(), meters);
                } else {
                    Leg leg = legFor(stop.getRideId());
                    double ridden = meters - boardedAt.getOrDefault(stop.getRideId(), 0.0);
                    double direct = leg.onBoard ? d.distance(origin, leg.destinationId)
                            : d.distance(leg.pickupId, leg.destinationId);
                    result.put(stop.getRideId(), Math.max(0.0, ridden - direct));
                }
            }
            return result;
        }

        private Leg legFor(int rideId) {
            for (Leg leg : legs) {
                if (leg.rideId == rideId) {
                    return leg;
                }
            }
            throw new IllegalStateException("No leg for ride " + rideId);
        }

        Plan build(List<Stop> order, boolean fair) {
            Map<Integer, Double> untilPickup = new HashMap<>();
            Map<Integer, Double> untilDropoff = new HashMap<>();
            String at = origin;
            double meters = 0.0;
            for (Stop stop : order) {
                meters += d.distance(at, stop.getLocationId());
                at = stop.getLocationId();
                (stop.getType() == StopType.PICKUP ? untilPickup : untilDropoff).put(stop.getRideId(), meters);
            }
            return new Plan(order, meters, fair, riderDetours(order), untilPickup, untilDropoff);
        }
    }
}
