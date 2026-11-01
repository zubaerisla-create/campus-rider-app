package campusrider.algorithms;

import campusrider.model.Location;

import java.util.List;

/** Small hand-made graph used by several tests. */
public class TestGraphs {

    public static Location location(String id, String name, String... aliases) {
        return new Location(id, name, "TEST", false, List.of(aliases));
    }

    /**
     *   A --4-- B --5-- D        E (no roads)
     *    \     /
     *     1   2
     *      \ /
     *       C
     * Shortest A to D is A, C, B, D = 1 + 2 + 5 = 8 (not A, B, D = 9).
     */
    public static CampusGraph smallGraph() {
        CampusGraph graph = new CampusGraph();
        for (String id : new String[]{"A", "B", "C", "D", "E"}) {
            graph.addLocation(location(id, "Place " + id));
        }
        graph.addRoad("A", "B", 4, "Road AB");
        graph.addRoad("A", "C", 1, "Road AC");
        graph.addRoad("C", "B", 2, "Road CB");
        graph.addRoad("B", "D", 5, "Road BD");
        return graph;
    }
}
