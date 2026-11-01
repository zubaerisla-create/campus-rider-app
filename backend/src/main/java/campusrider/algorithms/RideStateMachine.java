package campusrider.algorithms;

import campusrider.exception.InvalidStateTransitionException;
import campusrider.model.RideStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Finite State Machine for the ride lifecycle.
 *
 *            accept           start           complete
 *   PENDING --------> ACCEPTED ------> STARTED ---------> COMPLETED
 *      |                  |
 *      | no driver        | passenger cancels
 *      v                  v
 *   REJECTED          CANCELLED  <---- (also from PENDING)
 *
 * The transition table is an EnumMap from a state to the set of states it may
 * move to. Every status change in the backend goes through {@link #transition},
 * so an invalid jump (for example PENDING -> COMPLETED, or anything leaving a
 * final state) is impossible.
 *
 * Time: O(1) per check (EnumSet lookup is a bit test).  Space: O(S^2) for S states.
 * Why: a ride has a small, fixed set of states, and an explicit table is easy to
 * read, test and explain - it is the classic use of a finite state machine.
 */
public final class RideStateMachine {

    private static final Map<RideStatus, Set<RideStatus>> TRANSITIONS = new EnumMap<>(RideStatus.class);

    static {
        TRANSITIONS.put(RideStatus.PENDING,
                EnumSet.of(RideStatus.ACCEPTED, RideStatus.REJECTED, RideStatus.CANCELLED));
        TRANSITIONS.put(RideStatus.ACCEPTED, EnumSet.of(RideStatus.STARTED, RideStatus.CANCELLED));
        TRANSITIONS.put(RideStatus.STARTED, EnumSet.of(RideStatus.COMPLETED));
        TRANSITIONS.put(RideStatus.COMPLETED, EnumSet.noneOf(RideStatus.class));
        TRANSITIONS.put(RideStatus.REJECTED, EnumSet.noneOf(RideStatus.class));
        TRANSITIONS.put(RideStatus.CANCELLED, EnumSet.noneOf(RideStatus.class));
    }

    private RideStateMachine() {
    }

    public static boolean canTransition(RideStatus from, RideStatus to) {
        return from != null && to != null && TRANSITIONS.get(from).contains(to);
    }

    /** Returns {@code to} if the move is allowed, otherwise throws. */
    public static RideStatus transition(int rideId, RideStatus from, RideStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(
                    "Ride #" + rideId + " cannot change from " + from + " to " + to + ".");
        }
        return to;
    }

    public static Set<RideStatus> nextStates(RideStatus from) {
        return Collections.unmodifiableSet(TRANSITIONS.get(from));
    }
}
