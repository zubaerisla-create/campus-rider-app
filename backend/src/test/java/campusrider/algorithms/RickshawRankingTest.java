package campusrider.algorithms;

import campusrider.model.Rickshaw;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Merge-sort ranking criteria and the greedy pick, on the line graph S-A-B-C-D. */
class RickshawRankingTest {

    private static RouteCompatibility.CompatibilityResult at(String id, String location, int booked) {
        Rickshaw r = new Rickshaw(id, "Driver " + id, location, true);
        r.setBookedSeats(booked);
        return RouteCompatibility.evaluate(RouteCompatibilityTest.line(), r, List.of(), "B", "D", 1);
    }

    @Test
    void sortsByNearestPickupAndMostSeatsWithIncompatibleLast() {
        List<RouteCompatibility.CompatibilityResult> items = new ArrayList<>();
        items.add(at("R3", "S", 0));  // 200 m away, 3 seats
        items.add(at("R1", "A", 2));  // 100 m away, 1 seat
        items.add(at("R2", "B", 3));  // full -> incompatible
        items.add(at("R4", "C", 1));  // 100 m away, 2 seats

        List<String> nearest = ids(RickshawSorter.mergeSort(items, RickshawSorter.SortCriterion.NEAREST_PICKUP, 1));
        assertEquals(List.of("R1", "R4", "R3", "R2"), nearest);

        List<String> seats = ids(RickshawSorter.mergeSort(items, RickshawSorter.SortCriterion.MOST_SEATS, 1));
        assertEquals(List.of("R3", "R4", "R1", "R2"), seats);
    }

    @Test
    void greedyPrefersTheClosestBestFitAndSkipsIncompatible() {
        // R1 (100 m, 1 seat left -> best fit) beats R4 (100 m, 2 seats) and R3 (200 m).
        List<RouteCompatibility.CompatibilityResult> items = List.of(at("R3", "S", 0), at("R4", "C", 1),
                at("R1", "A", 2), at("R2", "B", 3));
        RouteCompatibility.CompatibilityResult best = GreedyRickshawMatcher.selectBest(items, 1);
        assertEquals("R1", best.getRickshaw().getId());
        assertEquals(100.0, GreedyRickshawMatcher.score(best, 1), 1e-9);
        assertFalse(Double.isFinite(GreedyRickshawMatcher.score(at("R2", "B", 3), 1)));
        assertNull(GreedyRickshawMatcher.selectBest(List.of(at("R2", "B", 3)), 1));
    }

    private static List<String> ids(List<RouteCompatibility.CompatibilityResult> list) {
        return list.stream().map(c -> c.getRickshaw().getId()).toList();
    }
}
