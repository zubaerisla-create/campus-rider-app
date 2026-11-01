package campusrider.algorithms;

import campusrider.model.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Weighted, undirected graph of the campus stored as an adjacency list.
 *
 * Vertices: campus locations (key = location id).
 * Edges:    roads, weighted by distance in meters.
 *
 * adjacencyList.get("bottola") gives every road leaving Bottola.
 * Memory use is O(V + E), which suits a sparse road network.
 *
 * Built once at startup from locations.csv and roads.csv (RouteService) and used
 * by Dijkstra, route compatibility, the web map (GET /api/map) and the
 * simulated rickshaw movement, which only ever moves along these edges.
 */
public class CampusGraph {
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacencyList = new LinkedHashMap<>();

    public void addLocation(Location location) {
        if (location == null || location.getId().isBlank()) {
            throw new IllegalArgumentException("Location must have an id");
        }
        if (locations.containsKey(location.getId())) {
            throw new IllegalArgumentException("Duplicate location id: " + location.getId());
        }
        locations.put(location.getId(), location);
        adjacencyList.put(location.getId(), new ArrayList<>());
    }

    /** Adds a two-way road, so one edge is stored in each direction. */
    public void addRoad(String fromId, String toId, double distanceMeters, String roadName) {
        if (!hasLocation(fromId) || !hasLocation(toId)) {
            throw new IllegalArgumentException("Unknown location in road: " + fromId + " - " + toId);
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("A road cannot connect a location to itself: " + fromId);
        }
        if (!(distanceMeters > 0) || Double.isInfinite(distanceMeters)) {
            throw new IllegalArgumentException("Road distance must be a positive number: " + distanceMeters);
        }
        adjacencyList.get(fromId).add(new Edge(toId, distanceMeters, roadName));
        adjacencyList.get(toId).add(new Edge(fromId, distanceMeters, roadName));
    }

    public boolean hasLocation(String id) {
        return id != null && locations.containsKey(id);
    }

    public Location getLocation(String id) {
        return locations.get(id);
    }

    public Collection<Location> getAllLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    /** Roads leaving a location (its neighbours). Empty for an unknown id. */
    public List<Edge> getNeighbors(String id) {
        List<Edge> edges = adjacencyList.get(id);
        return edges == null ? Collections.emptyList() : Collections.unmodifiableList(edges);
    }

    /** The shortest direct road between two neighbours, or null if they are not neighbours. */
    public Edge getRoad(String fromId, String toId) {
        Edge best = null;
        for (Edge edge : getNeighbors(fromId)) {
            if (edge.getToId().equals(toId) && (best == null || edge.getDistanceMeters() < best.getDistanceMeters())) {
                best = edge;
            }
        }
        return best;
    }

    /** Length of the direct road between two neighbours, or -1 if no direct road exists. */
    public double getRoadDistance(String fromId, String toId) {
        Edge road = getRoad(fromId, toId);
        return road == null ? -1 : road.getDistanceMeters();
    }

    /**
     * Breadth-first traversal: every location reachable from startId by road.
     * Uses a FIFO queue and a visited set. Time O(V + E).
     * Used at startup to warn about (and flag in the API) locations that are cut off
     * from the main campus network, so passengers cannot pick an unreachable place.
     */
    public Set<String> reachableFrom(String startId) {
        Set<String> visited = new LinkedHashSet<>();
        if (!hasLocation(startId)) {
            return visited;
        }
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Edge edge : getNeighbors(current)) {
                if (visited.add(edge.getToId())) {
                    queue.add(edge.getToId());
                }
            }
        }
        return visited;
    }

    /** Locations that cannot be reached from the largest connected part of the network. */
    public Set<String> findDisconnectedLocations() {
        Set<String> best = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (String id : locations.keySet()) {
            if (seen.contains(id)) {
                continue;
            }
            Set<String> component = reachableFrom(id);
            seen.addAll(component);
            if (component.size() > best.size()) {
                best = component;
            }
        }
        Set<String> disconnected = new LinkedHashSet<>(locations.keySet());
        disconnected.removeAll(best);
        return disconnected;
    }

    public int getLocationCount() {
        return locations.size();
    }

    /** Each two-way road is stored twice, so divide by 2. */
    public int getRoadCount() {
        int total = 0;
        for (List<Edge> edges : adjacencyList.values()) {
            total += edges.size();
        }
        return total / 2;
    }
}
