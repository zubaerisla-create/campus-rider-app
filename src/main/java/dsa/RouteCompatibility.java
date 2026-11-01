package dsa;

import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Multi-Passenger Route Compatibility Algorithm.
 *
 * Evaluates whether a Rickshaw (which may be empty, heading toward a driver-set
 * target destination, or already carrying 1-2 passengers) is compatible with a
 * new passenger's (pickupId -> destinationId) request.
 *
 * Uses Dijkstra's Shortest Path algorithm on {@link CampusGraph} to compute:
 *   1. Distance from the rickshaw's current node to the new passenger's pickup.
 *   2. Whether the new passenger's pickup lies along the rickshaw's current Dijkstra path.
 *   3. Detour distance (extra meters added to the existing passengers' or driver's route).
 *   4. A Greedy Compatibility Score (lower is better) combining pickup proximity,
 *      route overlap bonus, and detour overhead.
 */
public class RouteCompatibility {

    /** Maximum extra detour distance (in meters) allowed when sharing a ride with existing passengers. */
    public static final double MAX_SHARED_DETOUR_METERS = 750.0;

    public static class CompatibilityResult {
        private final Rickshaw rickshaw;
        private final boolean compatible;
        private final boolean pickupOnCurrentRoute;
        private final double distanceToPickupMeters;
        private final double passengerTripMeters;
        private final double detourMeters;
        private final double greedyScore;
        private final String summary;

        public CompatibilityResult(Rickshaw rickshaw, boolean compatible, boolean pickupOnCurrentRoute,
                                   double distanceToPickupMeters, double passengerTripMeters,
                                   double detourMeters, double greedyScore, String summary) {
            this.rickshaw = rickshaw;
            this.compatible = compatible;
            this.pickupOnCurrentRoute = pickupOnCurrentRoute;
            this.distanceToPickupMeters = distanceToPickupMeters;
            this.passengerTripMeters = passengerTripMeters;
            this.detourMeters = detourMeters;
            this.greedyScore = greedyScore;
            this.summary = summary;
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

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }

        public double getPassengerTripMeters() {
            return passengerTripMeters;
        }

        public double getDetourMeters() {
            return detourMeters;
        }

        public double getGreedyScore() {
            return greedyScore;
        }

        public String getSummary() {
            return summary;
        }
    }

    private RouteCompatibility() {
    }

    /**
     * Evaluates route compatibility between a rickshaw (with its active rides) and
     * a new passenger's pickup, destination, and seat requirement.
     */
    public static CompatibilityResult evaluate(CampusGraph graph,
                                               Rickshaw rickshaw,
                                               List<RideRequest> activeRidesOnRickshaw,
                                               String pickupId,
                                               String destinationId,
                                               int seatsNeeded) {
        if (rickshaw == null || !rickshaw.hasSeatsFor(seatsNeeded)) {
            return new CompatibilityResult(rickshaw, false, false,
                    Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, "Not enough free seats");
        }

        RouteResult toPickup = Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), pickupId);
        RouteResult tripRoute = Dijkstra.findShortestPath(graph, pickupId, destinationId);
        if (!toPickup.isFound() || !tripRoute.isFound()) {
            return new CompatibilityResult(rickshaw, false, false,
                    Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, "Unreachable route");
        }

        double distToPickup = toPickup.getTotalDistanceMeters();
        double tripMeters = tripRoute.getTotalDistanceMeters();

        // Case 1: Rickshaw has no active passenger rides right now
        if (activeRidesOnRickshaw == null || activeRidesOnRickshaw.isEmpty()) {
            String driverTarget = rickshaw.getTargetDestinationId();
            if (driverTarget != null && !driverTarget.isBlank() && !driverTarget.equals(rickshaw.getLocationId())) {
                RouteResult driverPath = Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), driverTarget);
                RouteResult destToTarget = Dijkstra.findShortestPath(graph, destinationId, driverTarget);
                if (driverPath.isFound() && destToTarget.isFound()) {
                    boolean onPath = driverPath.getPath().contains(pickupId);
                    double combined = distToPickup + tripMeters + destToTarget.getTotalDistanceMeters();
                    double detour = Math.max(0.0, combined - driverPath.getTotalDistanceMeters());
                    boolean ok = detour <= MAX_SHARED_DETOUR_METERS;
                    double score = distToPickup + (1.2 * detour) - (onPath ? 100.0 : 0.0);
                    String note = String.format(Locale.ROOT, "%s (Detour: +%.0fm%s)",
                            ok ? "Compatible" : "High Detour", detour, onPath ? ", On Driver Route" : "");
                    return new CompatibilityResult(rickshaw, ok, onPath,
                            distToPickup, tripMeters, detour, score, note);
                }
            }
            // Idle rickshaw with no conflicting target destination -> 0m detour
            String note = String.format(Locale.ROOT, "Direct Free Rickshaw (%.0fm to pickup, 0m detour)", distToPickup);
            return new CompatibilityResult(rickshaw, true, true,
                    distToPickup, tripMeters, 0.0, distToPickup, note);
        }

        // Case 2: Rickshaw is currently carrying / matched with existing passenger(s) -> Seat Sharing!
        // Build the existing baseline waypoints and route from rickshaw's current position
        List<String> existingWaypoints = new ArrayList<>();
        for (RideRequest r : activeRidesOnRickshaw) {
            if (r.getStatus() == RideStatus.MATCHED && !existingWaypoints.contains(r.getPickupId())) {
                existingWaypoints.add(r.getPickupId());
            }
        }
        for (RideRequest r : activeRidesOnRickshaw) {
            if (!existingWaypoints.contains(r.getDestinationId())) {
                existingWaypoints.add(r.getDestinationId());
            }
        }

        Set<String> baselineNodes = new LinkedHashSet<>();
        double baselineDistance = computeMultiStopDistance(graph, rickshaw.getLocationId(), existingWaypoints, baselineNodes);
        boolean pickupOnRoute = baselineNodes.contains(pickupId);
        boolean destOnRoute = baselineNodes.contains(destinationId);

        // Evaluate the two natural drop-off orderings for shared rides:
        // First collect any existing pickups (not already at current location) and existing destinations
        List<String> existingPickups = new ArrayList<>();
        List<String> existingDests = new ArrayList<>();
        for (RideRequest r : activeRidesOnRickshaw) {
            if (r.getStatus() == RideStatus.MATCHED
                    && !r.getPickupId().equals(rickshaw.getLocationId())
                    && !existingPickups.contains(r.getPickupId())) {
                existingPickups.add(r.getPickupId());
            }
            if (!existingDests.contains(r.getDestinationId())) {
                existingDests.add(r.getDestinationId());
            }
        }

        // Option A: existingPickups -> P2 -> existingDests -> D2
        List<String> orderA = new ArrayList<>(existingPickups);
        if (!orderA.contains(pickupId)) {
            orderA.add(pickupId);
        }
        for (String d : existingDests) {
            if (!orderA.contains(d)) {
                orderA.add(d);
            }
        }
        if (!orderA.contains(destinationId)) {
            orderA.add(destinationId);
        }
        double distA = computeMultiStopDistance(graph, rickshaw.getLocationId(), orderA, null);

        // Option B: existingPickups -> P2 -> D2 -> existingDests
        List<String> orderB = new ArrayList<>(existingPickups);
        if (!orderB.contains(pickupId)) {
            orderB.add(pickupId);
        }
        if (!orderB.contains(destinationId)) {
            orderB.add(destinationId);
        }
        for (String d : existingDests) {
            if (!orderB.contains(d)) {
                orderB.add(d);
            }
        }
        double distB = computeMultiStopDistance(graph, rickshaw.getLocationId(), orderB, null);

        double bestSharedDistance = Math.min(distA, distB);
        double detour = Math.max(0.0, bestSharedDistance - baselineDistance);
        boolean compatible = detour <= MAX_SHARED_DETOUR_METERS;

        double overlapBonus = (pickupOnRoute ? 90.0 : 0.0) + (destOnRoute ? 60.0 : 0.0);
        double greedyScore = distToPickup + (1.5 * detour) - overlapBonus;

        String summary = String.format(Locale.ROOT,
                "Shared (%d/%d free, +%.0fm detour%s)",
                rickshaw.getAvailableSeats(), rickshaw.getMaxSeats(), detour,
                pickupOnRoute ? ", Pickup on Route" : "");

        return new CompatibilityResult(rickshaw, compatible, pickupOnRoute,
                distToPickup, tripMeters, detour, greedyScore, summary);
    }

    private static double computeMultiStopDistance(CampusGraph graph,
                                                   String startNode,
                                                   List<String> waypoints,
                                                   Set<String> visitedPathNodesOut) {
        String current = startNode;
        if (visitedPathNodesOut != null) {
            visitedPathNodesOut.add(current);
        }
        double total = 0.0;
        for (String target : waypoints) {
            if (current.equals(target)) {
                continue;
            }
            RouteResult leg = Dijkstra.findShortestPath(graph, current, target);
            if (!leg.isFound()) {
                return Double.POSITIVE_INFINITY;
            }
            total += leg.getTotalDistanceMeters();
            if (visitedPathNodesOut != null) {
                visitedPathNodesOut.addAll(leg.getPath());
            }
            current = target;
        }
        return total;
    }
}
