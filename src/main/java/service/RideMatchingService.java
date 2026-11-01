package service;

import dsa.CampusGraph;
import dsa.Dijkstra;
import dsa.RickshawSorter;
import dsa.RouteCompatibility;
import dsa.RouteResult;
import model.RideRequest;
import model.Rickshaw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chooses a rickshaw for a ride request using:
 * 1) Dijkstra's Shortest Path algorithm on {@link CampusGraph}
 * 2) Multi-Passenger {@link RouteCompatibility} evaluation (detour & route overlap)
 * 3) Greedy selection & {@link RickshawSorter} Merge Sort O(R log R)
 */
public class RideMatchingService {

    /** The chosen rickshaw and how far it must travel to reach the pickup. */
    public static class Match {
        private final Rickshaw rickshaw;
        private final double distanceToPickupMeters;
        private final double detourMeters;
        private final String compatibilityNote;

        Match(Rickshaw rickshaw, double distanceToPickupMeters) {
            this(rickshaw, distanceToPickupMeters, 0.0, "Direct match");
        }

        Match(Rickshaw rickshaw, double distanceToPickupMeters, double detourMeters, String compatibilityNote) {
            this.rickshaw = rickshaw;
            this.distanceToPickupMeters = distanceToPickupMeters;
            this.detourMeters = detourMeters;
            this.compatibilityNote = compatibilityNote;
        }

        public Rickshaw getRickshaw() {
            return rickshaw;
        }

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }

        public double getDetourMeters() {
            return detourMeters;
        }

        public String getCompatibilityNote() {
            return compatibilityNote;
        }
    }

    private final CampusGraph graph;

    public RideMatchingService(CampusGraph graph) {
        this.graph = graph;
    }

    /** Returns the best idle rickshaw match, or null if no available rickshaw can reach the pickup. */
    public Match findBestRickshaw(RideRequest request, List<Rickshaw> rickshaws) {
        Match best = null;
        for (Rickshaw rickshaw : rickshaws) {
            if (!rickshaw.isAvailable()) {
                continue; // busy rickshaws never get a second ride in exclusive auto-match
            }
            if (!rickshaw.hasSeatsFor(request.getSeatsRequested())) {
                continue;
            }
            if (request.isRejectedBy(rickshaw.getId())) {
                continue;
            }
            RouteResult toPickup = Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), request.getPickupId());
            if (!toPickup.isFound()) {
                continue;
            }
            double distance = toPickup.getTotalDistanceMeters();
            if (best == null
                    || distance < best.distanceToPickupMeters
                    || (distance == best.distanceToPickupMeters
                        && rickshaw.getId().compareTo(best.rickshaw.getId()) < 0)) {
                best = new Match(rickshaw, distance);
            }
        }
        return best;
    }

    /**
     * Evaluates all rickshaws that have at least {@code seatsNeeded} free seats
     * (including shared rickshaws already carrying passengers) and sorts them
     * using custom Merge Sort ({@link RickshawSorter}).
     */
    public List<RouteCompatibility.CompatibilityResult> findCompatibleRickshawsSorted(
            String pickupId,
            String destinationId,
            int seatsNeeded,
            List<Rickshaw> rickshaws,
            Map<String, List<RideRequest>> activeRidesByRickshaw,
            RickshawSorter.SortCriterion sortCriterion) {
        List<RouteCompatibility.CompatibilityResult> results = new ArrayList<>();
        for (Rickshaw r : rickshaws) {
            if (!r.hasSeatsFor(seatsNeeded)) {
                continue;
            }
            List<RideRequest> active = activeRidesByRickshaw == null
                    ? List.of()
                    : activeRidesByRickshaw.getOrDefault(r.getId(), List.of());
            RouteCompatibility.CompatibilityResult eval = RouteCompatibility.evaluate(
                    graph, r, active, pickupId, destinationId, seatsNeeded);
            results.add(eval);
        }
        return RickshawSorter.mergeSort(results, sortCriterion);
    }

    /**
     * Greedy algorithm: finds the next best route-compatible rickshaw (idle or shared)
     * that has enough seats and has NOT rejected the request.
     */
    public RouteCompatibility.CompatibilityResult findNextCompatibleRickshawForRequest(
            RideRequest request,
            List<Rickshaw> rickshaws,
            Map<String, List<RideRequest>> activeRidesByRickshaw) {
        List<RouteCompatibility.CompatibilityResult> candidates = new ArrayList<>();
        for (Rickshaw r : rickshaws) {
            if (!r.hasSeatsFor(request.getSeatsRequested())) {
                continue;
            }
            if (request.isRejectedBy(r.getId())) {
                continue;
            }
            List<RideRequest> active = activeRidesByRickshaw == null
                    ? List.of()
                    : activeRidesByRickshaw.getOrDefault(r.getId(), List.of());
            RouteCompatibility.CompatibilityResult eval = RouteCompatibility.evaluate(
                    graph, r, active, request.getPickupId(), request.getDestinationId(), request.getSeatsRequested());
            if (eval.isCompatible()) {
                candidates.add(eval);
            }
        }
        List<RouteCompatibility.CompatibilityResult> sorted =
                RickshawSorter.mergeSort(candidates, RickshawSorter.SortCriterion.BEST_COMPATIBILITY);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /** Computes the Dijkstra route from a rickshaw's current position to a target location. */
    public RouteResult routeFromRickshaw(Rickshaw rickshaw, String targetLocationId) {
        return Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), targetLocationId);
    }
}
