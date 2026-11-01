package campusrider.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra's shortest path algorithm using a min PriorityQueue.
 *
 * Idea: always take the unvisited location with the smallest known distance
 * from the source. Its distance is then final, because all road lengths are
 * positive. Then try to improve ("relax") the distance of each neighbour.
 *
 * Time:  O((V + E) log V)   Space: O(V + E)
 *
 * Used for: the passenger's trip route and fare estimate (GET /api/routes/shortest-path),
 * the driver's route to each pickup, every leg of a shared-ride plan and its detour
 * (RouteCompatibility), ranking rickshaws by pickup distance (RideMatchingService),
 * and the road-by-road path the simulated rickshaw drives (TrackingService).
 * Why Dijkstra: all road weights are positive distances, and the campus graph is
 * small and sparse, so a binary-heap Dijkstra gives exact answers instantly.
 */
public class Dijkstra {

    /** One entry in the priority queue: a location and the distance found for it. */
    private static class QueueEntry {
        final String locationId;
        final double distance;

        QueueEntry(String locationId, double distance) {
            this.locationId = locationId;
            this.distance = distance;
        }
    }

    public static RouteResult findShortestPath(CampusGraph graph, String sourceId, String targetId) {
        if (!graph.hasLocation(sourceId)) {
            return RouteResult.notFound("Unknown start location: " + sourceId);
        }
        if (!graph.hasLocation(targetId)) {
            return RouteResult.notFound("Unknown destination: " + targetId);
        }
        if (sourceId.equals(targetId)) {
            return RouteResult.found(List.of(sourceId), 0);
        }

        Map<String, Double> distance = new HashMap<>();   // best distance found so far
        Map<String, String> previous = new HashMap<>();   // previous location on the best path
        Set<String> visited = new HashSet<>();

        // Smallest distance first. Ties are broken by id so results are always the same.
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>((a, b) -> {
            int byDistance = Double.compare(a.distance, b.distance);
            return byDistance != 0 ? byDistance : a.locationId.compareTo(b.locationId);
        });

        distance.put(sourceId, 0.0);
        queue.add(new QueueEntry(sourceId, 0.0));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            String u = current.locationId;

            // A location can be in the queue more than once; only the first poll counts.
            if (!visited.add(u)) {
                continue;
            }
            if (u.equals(targetId)) {
                break; // the destination's distance is now final
            }

            // Relax every road leaving u.
            for (Edge edge : graph.getNeighbors(u)) {
                String v = edge.getToId();
                double newDistance = distance.get(u) + edge.getDistanceMeters();
                if (newDistance < distance.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    distance.put(v, newDistance);
                    previous.put(v, u);
                    queue.add(new QueueEntry(v, newDistance));
                }
            }
        }

        if (!distance.containsKey(targetId)) {
            return RouteResult.notFound("No road connects " + graph.getLocation(sourceId).getName()
                    + " to " + graph.getLocation(targetId).getName());
        }

        // Walk backwards from the destination using "previous", then reverse.
        List<String> path = new ArrayList<>();
        for (String at = targetId; at != null; at = previous.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        return RouteResult.found(path, distance.get(targetId));
    }
}
