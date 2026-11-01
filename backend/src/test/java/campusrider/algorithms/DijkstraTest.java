package campusrider.algorithms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DijkstraTest {
    private final CampusGraph graph = TestGraphs.smallGraph();

    @Test
    void findsTheShortestRouteNotTheFewestRoads() {
        RouteResult route = Dijkstra.findShortestPath(graph, "A", "D");
        assertTrue(route.isFound());
        assertEquals(List.of("A", "C", "B", "D"), route.getPath());
        assertEquals(8.0, route.getTotalDistanceMeters(), 1e-9);
    }

    @Test
    void worksInTheReverseDirection() {
        RouteResult route = Dijkstra.findShortestPath(graph, "D", "A");
        assertEquals(List.of("D", "B", "C", "A"), route.getPath());
        assertEquals(8.0, route.getTotalDistanceMeters(), 1e-9);
    }

    @Test
    void reportsUnreachableDestination() {
        RouteResult route = Dijkstra.findShortestPath(graph, "A", "E");
        assertFalse(route.isFound());
        assertTrue(route.getPath().isEmpty());
        assertTrue(route.getMessage().contains("No road connects"));
    }

    @Test
    void sameSourceAndDestinationIsZero() {
        RouteResult route = Dijkstra.findShortestPath(graph, "B", "B");
        assertTrue(route.isFound());
        assertEquals(List.of("B"), route.getPath());
        assertEquals(0.0, route.getTotalDistanceMeters());
    }

    @Test
    void invalidInputDoesNotCrash() {
        assertFalse(Dijkstra.findShortestPath(graph, "A", "Z").isFound());
        assertFalse(Dijkstra.findShortestPath(graph, null, "A").isFound());
        assertFalse(Dijkstra.findShortestPath(graph, "A", null).isFound());
    }

    @Test
    void totalDistanceEqualsSumOfRoadsOnPath() {
        RouteResult route = Dijkstra.findShortestPath(graph, "A", "D");
        double sum = 0;
        List<String> path = route.getPath();
        for (int i = 1; i < path.size(); i++) {
            sum += graph.getRoadDistance(path.get(i - 1), path.get(i));
        }
        assertEquals(route.getTotalDistanceMeters(), sum, 1e-9);
    }
}
