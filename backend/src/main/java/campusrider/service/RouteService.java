package campusrider.service;

import campusrider.algorithms.CampusGraph;
import campusrider.algorithms.Dijkstra;
import campusrider.algorithms.Edge;
import campusrider.algorithms.LocationTrie;
import campusrider.algorithms.MergeSort;
import campusrider.algorithms.RouteResult;
import campusrider.model.Location;
import campusrider.model.Road;
import campusrider.storage.CsvStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Builds the campus Graph and the location Trie from locations.csv / roads.csv
 * at startup, and answers route and search questions for the rest of the backend.
 */
@Service
public class RouteService {
    private final CampusGraph graph = new CampusGraph();
    private final LocationTrie trie = new LocationTrie();
    private final List<Road> roads = new ArrayList<>();
    private final Set<String> disconnected;

    public RouteService(CsvStorage storage) throws IOException {
        List<Location> locations = storage.loadLocations();
        List<Road> roadRows = storage.loadRoads();
        for (Location location : locations) {
            try {
                graph.addLocation(location);
                trie.addLocation(location);
            } catch (IllegalArgumentException e) {
                storage.addWarning("Skipped location: " + e.getMessage());
            }
        }
        for (Road road : roadRows) {
            try {
                graph.addRoad(road.getFromId(), road.getToId(), road.getDistanceMeters(), road.getName());
                roads.add(road);
            } catch (IllegalArgumentException e) {
                storage.addWarning("Skipped road: " + e.getMessage());
            }
        }
        // BFS over the graph: places cut off from the main network are reported and flagged.
        disconnected = graph.findDisconnectedLocations();
        if (!disconnected.isEmpty()) {
            storage.addWarning("Locations not connected to the campus road network: " + disconnected);
        }
    }

    public CampusGraph getGraph() {
        return graph;
    }

    /** All locations sorted by name (custom merge sort). */
    public List<Location> getLocationsSorted() {
        return MergeSort.sort(new ArrayList<>(graph.getAllLocations()),
                (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    }

    public List<Road> getRoads() {
        return Collections.unmodifiableList(roads);
    }

    public boolean isValidLocation(String id) {
        return graph.hasLocation(id);
    }

    /** A location that exists and can be reached from the rest of campus. */
    public boolean isRoutable(String id) {
        return graph.hasLocation(id) && !disconnected.contains(id);
    }

    public boolean isDisconnected(String id) {
        return disconnected.contains(id);
    }

    public Location getLocation(String id) {
        return graph.getLocation(id);
    }

    public String getLocationName(String id) {
        Location location = graph.getLocation(id);
        return location == null ? id : location.getName();
    }

    /** Trie prefix search. */
    public List<Location> searchLocations(String prefix) {
        return trie.search(prefix);
    }

    /** Dijkstra shortest path. */
    public RouteResult findRoute(String fromId, String toId) {
        return Dijkstra.findShortestPath(graph, fromId, toId);
    }

    /** Road names along a path, one per leg (used for turn-by-turn style route details). */
    public List<String> roadNames(List<String> path) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            Edge road = graph.getRoad(path.get(i - 1), path.get(i));
            names.add(road == null ? "" : road.getRoadName());
        }
        return names;
    }

    /** Length of the direct road between two neighbours, or -1. */
    public double roadLength(String fromId, String toId) {
        return graph.getRoadDistance(fromId, toId);
    }
}
