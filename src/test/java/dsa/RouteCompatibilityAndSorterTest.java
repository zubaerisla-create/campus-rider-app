package dsa;

import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.RideService;
import service.TestApp;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteCompatibilityAndSorterTest {

    @TempDir
    Path dir;

    @Test
    void multiPassengerRouteCompatibilityComputesDetourAndOnRouteStatus() throws Exception {
        RideService service = TestApp.start(dir);
        CampusGraph graph = service.getRouteService().getGraph();

        // JU-RICK-101 is at dairy_gate. Suppose it already accepted a 1-seat ride from dairy_gate -> bishmile_gate
        RideRequest r1 = service.createRequest("PassengerA", PassengerType.STUDENT, "dairy_gate", "bishmile_gate", 1);
        service.driverAcceptRide("JU-RICK-101", r1.getId());

        Rickshaw rick101 = service.getRickshaw("JU-RICK-101");
        List<RideRequest> active = service.getActiveRidesForRickshaw("JU-RICK-101");
        assertEquals(2, rick101.getAvailableSeats());

        // New passenger wants shaheed_minar -> new_arts_building (which is right along dairy_gate -> bishmile_gate!)
        RouteCompatibility.CompatibilityResult onRouteEval = RouteCompatibility.evaluate(
                graph, rick101, active, "shaheed_minar", "new_arts_building", 1);
        assertTrue(onRouteEval.isCompatible());
        assertTrue(onRouteEval.isPickupOnCurrentRoute());
        assertEquals(0.0, onRouteEval.getDetourMeters(), 1.0);

        // Requesting 3 seats on a rickshaw that only has 2 free seats is incompatible
        RouteCompatibility.CompatibilityResult tooManySeats = RouteCompatibility.evaluate(
                graph, rick101, active, "shaheed_minar", "new_arts_building", 3);
        assertFalse(tooManySeats.isCompatible());
    }

    @Test
    void mergeSortOrdersRickshawsByNearestPickupAndMostSeats() throws Exception {
        RideService service = TestApp.start(dir);

        // Occupy 2 seats on JU-RICK-101 (at dairy_gate -> 1 seat left)
        RideRequest r1 = service.createRequest("First", PassengerType.STUDENT, "dairy_gate", "shaheed_minar", 2);
        service.driverAcceptRide("JU-RICK-101", r1.getId());

        // Sort by NEAREST_PICKUP for pickup at dairy_gate: JU-RICK-101 is at dairy_gate (0m)
        List<RouteCompatibility.CompatibilityResult> byNearest = service.findSortedCompatibleRickshaws(
                "dairy_gate", "shaheed_minar", 1, RickshawSorter.SortCriterion.NEAREST_PICKUP);
        assertFalse(byNearest.isEmpty());
        assertEquals("JU-RICK-101", byNearest.get(0).getRickshaw().getId());
        assertEquals(0.0, byNearest.get(0).getDistanceToPickupMeters(), 1e-6);

        // Sort by MOST_SEATS: rickshaws with 3 free seats come before JU-RICK-101 (which has 1 free seat)
        List<RouteCompatibility.CompatibilityResult> byMostSeats = service.findSortedCompatibleRickshaws(
                "dairy_gate", "shaheed_minar", 1, RickshawSorter.SortCriterion.MOST_SEATS);
        assertEquals(3, byMostSeats.get(0).getRickshaw().getAvailableSeats());
        assertEquals("JU-RICK-101", byMostSeats.get(byMostSeats.size() - 1).getRickshaw().getId());
    }

    @Test
    void driverRejectAutomaticallyForwardsToNextBestCompatibleRickshaw() throws Exception {
        RideService service = TestApp.start(dir);

        // Request from dairy_gate -> shaheed_minar: initial best offer is JU-RICK-101 (0m away at dairy_gate)
        RideRequest req = service.createRequest("Tanvir", PassengerType.STUDENT, "dairy_gate", "shaheed_minar", 1);
        assertEquals("JU-RICK-101", req.getOfferedRickshawId());

        // Driver JU-RICK-101 rejects -> automatically re-matched to JU-RICK-103 (at shaheed_minar, 116.6m away)
        service.driverRejectRide("JU-RICK-101", req.getId());
        assertEquals("JU-RICK-103", req.getOfferedRickshawId());

        // Driver JU-RICK-103 rejects -> automatically re-matched to next nearest available rickshaw
        service.driverRejectRide("JU-RICK-103", req.getId());
        assertNotNull(req.getOfferedRickshawId());
        assertFalse(req.getOfferedRickshawId().isEmpty());
        assertFalse(req.getOfferedRickshawId().equals("JU-RICK-101"));
        assertFalse(req.getOfferedRickshawId().equals("JU-RICK-103"));
    }
}
