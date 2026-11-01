package campusrider.exception;

import org.springframework.http.HttpStatus;

/** The signed-in user may not act on this resource. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
