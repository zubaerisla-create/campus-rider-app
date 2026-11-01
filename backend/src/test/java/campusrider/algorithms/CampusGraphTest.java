package campusrider.algorithms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusGraphTest {

    @Test
    void roadsAreStoredInBothDirections() {
        CampusGraph graph = TestGraphs.smallGraph();
        assertEquals(5, graph.getLocationCount());
        assertEquals(4, graph.getRoadCount());
        assertEquals(4, graph.getRoadDistance("A", "B"));
        assertEquals(4, graph.getRoadDistance("B", "A"));
        assertEquals(-1, graph.getRoadDistance("A", "D"));
        assertEquals(3, graph.getNeighbors("B").size());
        assertTrue(graph.getNeighbors("E").isEmpty());
        assertTrue(graph.getNeighbors("unknown").isEmpty());
    }

    @Test
    void invalidRoadsAndLocationsAreRejected() {
        CampusGraph graph = TestGraphs.smallGraph();
        assertThrows(IllegalArgumentException.class, () -> graph.addRoad("A", "Z", 10, "x"));
        assertThrows(IllegalArgumentException.class, () -> graph.addRoad("A", "A", 10, "x"));
        assertThrows(IllegalArgumentException.class, () -> graph.addRoad("A", "D", 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> graph.addRoad("A", "D", -3, "x"));
        assertThrows(IllegalArgumentException.class, () -> graph.addRoad("A", "D", Double.NaN, "x"));
        assertThrows(IllegalArgumentException.class, () -> graph.addLocation(TestGraphs.location("A", "Again")));
    }
}
