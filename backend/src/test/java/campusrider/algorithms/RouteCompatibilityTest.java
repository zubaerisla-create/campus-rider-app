package campusrider.algorithms;

import campusrider.model.PassengerType;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A straight road S - A - B - C - D (100 m each) plus a long side street A - X (600 m).
 */
class RouteCompatibilityTest {

    static CampusGraph line() {
        CampusGraph g = new CampusGraph();
        for (String id : new String[]{"S", "A", "B", "C", "D", "X"}) {
            g.addLocation(TestGraphs.location(id, "Place " + id));
        }
        g.addRoad("S", "A", 100, "r1");
        g.addRoad("A", "B", 100, "r2");
        g.addRoad("B", "C", 100, "r3");
        g.addRoad("C", "D", 100, "r4");
        g.addRoad("A", "X", 600, "side");
        return g;
    }

    static RideRequest ride(int id, String from, String to, int seats, RideStatus status) {
        RideRequest r = new RideRequest(id, "P-" + id, "Rider " + id, PassengerType.STUDENT, from, to, 0, seats,
                false, RideStatus.PENDING, id);
        if (status != RideStatus.PENDING) {
            r.moveTo(RideStatus.ACCEPTED);
            if (status == RideStatus.STARTED) {
                r.moveTo(RideStatus.STARTED);
            }
        }
        r.setRickshawId("R1");
        return r;
    }

    static Rickshaw rickshawAt(String location, int booked) {
        Rickshaw r = new Rickshaw("R1", "Driver", location, true);
        r.setBookedSeats(booked);
        return r;
    }

    @Test
    void freeRickshawIsCompatibleWithZeroDetour() {
        RouteCompatibility.CompatibilityResult c = RouteCompatibility.evaluate(line(), rickshawAt("S", 0),
                List.of(), "B", "D", 1);
        assertTrue(c.isCompatible());
        assertFalse(c.isShared());
        assertEquals(200.0, c.getDistanceToPickupMeters(), 1e-9);
        assertEquals(0.0, c.getDetourMeters(), 1e-9);
        assertEquals(200.0, c.getPassengerTripMeters(), 1e-9);
    }

    @Test
    void passengerAlongTheExistingRouteAddsNoDetour() {
        // Rider 1 is on board S -> D; new passenger B -> C lies on that route.
        List<RideRequest> active = List.of(ride(1, "S", "D", 1, RideStatus.STARTED));
        RouteCompatibility.CompatibilityResult c = RouteCompatibility.evaluate(line(), rickshawAt("S", 1),
                active, "B", "C", 1);
        assertTrue(c.isCompatible());
        assertTrue(c.isShared());
        assertTrue(c.isPickupOnCurrentRoute());
        assertEquals(0.0, c.getDetourMeters(), 1e-9);
        assertEquals(0.0, c.getAdditionalMeters(), 1e-9);
        // Planned order: pick up the new rider at B, drop at C, then rider 1 at D.
        List<String> order = new ArrayList<>();
        for (RouteCompatibility.Stop s : c.getPlan().getStops()) {
            order.add(s.getType().name().charAt(0) + s.getLocationId());
        }
        assertEquals(List.of("PB", "DC", "DD"), order);
    }

    @Test
    void sideStreetPickupServesTheExistingRiderFirstAndLimitsTheNewRidersWait() {
        // Rider 1 is on board at A going to D. Fetching X first would delay them by 1200 m (> 750 m),
        // so the planner drops rider 1 at D first: zero detour for them, 1200 m wait for the new rider.
        List<RideRequest> active = List.of(ride(1, "S", "D", 1, RideStatus.STARTED));
        RouteCompatibility.CompatibilityResult c = RouteCompatibility.evaluate(line(), rickshawAt("A", 1),
                active, "X", "D", 1);
        assertTrue(c.isCompatible());
        assertEquals(0.0, c.getDetourMeters(), 1e-9);
        assertEquals(1200.0, c.getDistanceToPickupMeters(), 1e-9);
        assertEquals("D", c.getPlan().getStops().get(0).getLocationId());

        // With a 1000 m pickup limit that 1200 m wait is too long, so the rickshaw is not compatible.
        RouteCompatibility.CompatibilityResult limited = RouteCompatibility.evaluate(line(), rickshawAt("A", 1),
                active, "X", "D", 1, 2, 750, 1000);
        assertFalse(limited.isCompatible());
        assertNull(limited.getPlan());
    }

    @Test
    void detourIsTheExistingRidersDelayNotTheNewRidersTrip() {
        // Rider 1 waits at C for D. New rider at B going to D: the rickshaw picks up both on the way.
        List<RideRequest> active = List.of(ride(1, "C", "D", 1, RideStatus.ACCEPTED));
        RouteCompatibility.CompatibilityResult c = RouteCompatibility.evaluate(line(), rickshawAt("S", 1),
                active, "B", "D", 1);
        assertTrue(c.isCompatible());
        assertEquals(0.0, c.getDetourMeters(), 1e-9);

        // With a limit of 100 m the 1200 m side-street detour is refused even for an empty seat count of 2.
        RouteCompatibility.CompatibilityResult far = RouteCompatibility.evaluate(line(), rickshawAt("S", 1),
                active, "X", "A", 1, 2, 100, 5000);
        assertFalse(far.isCompatible());
    }

    @Test
    void seatsAndPickupDistanceAreChecked() {
        List<RideRequest> active = List.of(ride(1, "S", "D", 2, RideStatus.STARTED));
        RouteCompatibility.CompatibilityResult full = RouteCompatibility.evaluate(line(), rickshawAt("S", 2),
                active, "B", "C", 2);
        assertFalse(full.isCompatible());
        assertEquals("Not enough free seats", full.getSummary());

        RouteCompatibility.CompatibilityResult tooFar = RouteCompatibility.evaluate(line(), rickshawAt("D", 0),
                List.of(), "X", "S", 1, 3, 750, 500);
        assertFalse(tooFar.isCompatible());
    }

    @Test
    void driverDestinationModeOnlyAcceptsRidesGoingThatWay() {
        Rickshaw heading = rickshawAt("S", 0);
        heading.setTargetDestinationId("D");
        RouteCompatibility.CompatibilityResult along = RouteCompatibility.evaluate(line(), heading, List.of(),
                "B", "C", 1);
        assertTrue(along.isCompatible());
        assertEquals(0.0, along.getDetourMeters(), 1e-9);
        RouteCompatibility.CompatibilityResult away = RouteCompatibility.evaluate(line(), heading, List.of(),
                "A", "X", 1);
        assertFalse(away.isCompatible()); // 1200 m off the driver's way
    }

    @Test
    void plannerAlwaysPicksUpBeforeDroppingOff() {
        List<RideRequest> active = List.of(ride(1, "D", "S", 1, RideStatus.ACCEPTED),
                ride(2, "B", "C", 1, RideStatus.ACCEPTED));
        RouteCompatibility.Plan plan = RouteCompatibility.planForRides(line(), "A", active, 750);
        assertNotNull(plan);
        assertEquals(4, plan.getStops().size());
        for (int rideId : new int[]{1, 2}) {
            int pickup = -1;
            int drop = -1;
            for (int i = 0; i < plan.getStops().size(); i++) {
                RouteCompatibility.Stop s = plan.getStops().get(i);
                if (s.getRideId() == rideId) {
                    if (s.getType() == RouteCompatibility.StopType.PICKUP) {
                        pickup = i;
                    } else {
                        drop = i;
                    }
                }
            }
            assertTrue(pickup >= 0 && pickup < drop, "ride " + rideId);
        }
    }
}
