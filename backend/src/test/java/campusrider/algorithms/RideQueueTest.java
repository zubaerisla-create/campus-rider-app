package campusrider.algorithms;

import campusrider.model.PassengerType;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RideQueueTest {

    static RideRequest request(int id, PassengerType type, boolean emergency) {
        return new RideRequest(id, "P" + id, "Passenger " + id, type, "A", "B", 10, 1, emergency,
                RideStatus.PENDING, id);
    }

    private static List<Integer> pollAll(RideQueue queue) {
        List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            order.add(queue.poll().getId());
        }
        return order;
    }

    @Test
    void normalRequestsAreServedFirstComeFirstServed() {
        RideQueue queue = new RideQueue();
        queue.add(request(1, PassengerType.STUDENT, false));
        queue.add(request(2, PassengerType.STAFF, false));
        queue.add(request(3, PassengerType.STUDENT, false));
        assertEquals(List.of(1, 2, 3), pollAll(queue));
    }

    @Test
    void emergencyRequestsJumpAheadOfEveryNormalRequest() {
        RideQueue queue = new RideQueue();
        queue.add(request(1, PassengerType.STUDENT, false));
        queue.add(request(2, PassengerType.STAFF, false));
        queue.add(request(3, PassengerType.STUDENT, true));        // student flagged emergency
        queue.add(request(4, PassengerType.EMERGENCY, false));     // emergency account
        queue.add(request(5, PassengerType.STUDENT, false));
        assertEquals(2, queue.emergencySize());
        // Emergencies first (earliest first among equal priority), then FIFO for the rest.
        assertEquals(List.of(3, 4, 1, 2, 5), pollAll(queue));
    }

    @Test
    void previewAndDrainKeepProcessingOrder() {
        RideQueue queue = new RideQueue();
        queue.add(request(1, PassengerType.STUDENT, false));
        queue.add(request(2, PassengerType.EMERGENCY, false));
        assertEquals(List.of(2, 1), queue.inProcessingOrder().stream().map(RideRequest::getId).toList());
        assertEquals(2, queue.size());
        assertEquals(List.of(2, 1), queue.drainInOrder().stream().map(RideRequest::getId).toList());
        assertEquals(0, queue.size());
    }

    @Test
    void sameRequestIsNeverQueuedTwiceAndRemovedRequestsAreGone() {
        RideQueue queue = new RideQueue();
        RideRequest r = request(1, PassengerType.STUDENT, false);
        queue.add(r);
        assertFalse(queue.add(r));
        assertEquals(1, queue.size());
        queue.remove(r);
        assertNull(queue.poll());
    }
}
