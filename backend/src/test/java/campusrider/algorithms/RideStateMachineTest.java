package campusrider.algorithms;

import campusrider.exception.InvalidStateTransitionException;
import campusrider.model.PassengerType;
import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import org.junit.jupiter.api.Test;

import static campusrider.model.RideStatus.ACCEPTED;
import static campusrider.model.RideStatus.CANCELLED;
import static campusrider.model.RideStatus.COMPLETED;
import static campusrider.model.RideStatus.PENDING;
import static campusrider.model.RideStatus.REJECTED;
import static campusrider.model.RideStatus.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideStateMachineTest {

    @Test
    void happyPathIsPendingAcceptedStartedCompleted() {
        RideRequest ride = new RideRequest(1, "P-1", "A", PassengerType.STUDENT, "a", "b", 1, 1, false, PENDING, 0);
        ride.moveTo(ACCEPTED);
        ride.moveTo(STARTED);
        ride.moveTo(COMPLETED);
        assertEquals(COMPLETED, ride.getStatus());
    }

    @Test
    void onlyListedTransitionsAreAllowed() {
        assertTrue(RideStateMachine.canTransition(PENDING, REJECTED));
        assertTrue(RideStateMachine.canTransition(PENDING, CANCELLED));
        assertTrue(RideStateMachine.canTransition(ACCEPTED, CANCELLED));
        assertFalse(RideStateMachine.canTransition(PENDING, STARTED));
        assertFalse(RideStateMachine.canTransition(PENDING, COMPLETED));
        assertFalse(RideStateMachine.canTransition(ACCEPTED, COMPLETED));
        assertFalse(RideStateMachine.canTransition(STARTED, CANCELLED));
        for (RideStatus finalState : new RideStatus[]{COMPLETED, REJECTED, CANCELLED}) {
            for (RideStatus any : RideStatus.values()) {
                assertFalse(RideStateMachine.canTransition(finalState, any), finalState + " -> " + any);
            }
        }
    }

    @Test
    void invalidTransitionThrowsAndLeavesStatusUnchanged() {
        RideRequest ride = new RideRequest(7, "P-1", "A", PassengerType.STUDENT, "a", "b", 1, 1, false, PENDING, 0);
        assertThrows(InvalidStateTransitionException.class, () -> ride.moveTo(COMPLETED));
        assertEquals(PENDING, ride.getStatus());
    }

    @Test
    void legacyCsvStatusesMapToNewStates() {
        assertEquals(ACCEPTED, RideStatus.fromCsv("MATCHED"));
        assertEquals(STARTED, RideStatus.fromCsv("IN_PROGRESS"));
        assertEquals(REJECTED, RideStatus.fromCsv("rejected"));
    }
}
