package service;

import dsa.CampusGraph;
import dsa.Dijkstra;
import dsa.RouteResult;
import model.RideRequest;
import model.Rickshaw;

import java.util.List;

/**
 * Chooses a rickshaw for a ride request.
 *
 * Rule: among the AVAILABLE rickshaws, pick the one with the shortest road
 * distance (Dijkstra) from its current location to the passenger's pickup.
 * If two are equally close, the smaller rickshaw id wins, so the result is
 * always the same for the same data.
 *
 * Cost: one Dijkstra run per available rickshaw, O(R * (V + E) log V).
 */
public class RideMatchingService {

    /** The chosen rickshaw and how far it must travel to reach the pickup. */
    public static class Match {
        private final Rickshaw rickshaw;
        private final double distanceToPickupMeters;

        Match(Rickshaw rickshaw, double distanceToPickupMeters) {
            this.rickshaw = rickshaw;
            this.distanceToPickupMeters = distanceToPickupMeters;
        }

        public Rickshaw getRickshaw() {
            return rickshaw;
        }

        public double getDistanceToPickupMeters() {
            return distanceToPickupMeters;
        }
    }

    private final CampusGraph graph;

    public RideMatchingService(CampusGraph graph) {
        this.graph = graph;
    }

    /** Returns the best match, or null if no available rickshaw can reach the pickup. */
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

    /** Computes the Dijkstra route from a rickshaw's current position to a target location. */
    public RouteResult routeFromRickshaw(Rickshaw rickshaw, String targetLocationId) {
        return Dijkstra.findShortestPath(graph, rickshaw.getLocationId(), targetLocationId);
    }
}
