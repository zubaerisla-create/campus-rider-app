package campusrider.exception;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state (no seats, already booked, ...). */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
