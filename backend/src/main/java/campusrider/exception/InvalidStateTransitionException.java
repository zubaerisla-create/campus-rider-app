package campusrider.exception;

/** Thrown by the ride state machine when a status change is not allowed. */
public class InvalidStateTransitionException extends ConflictException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
