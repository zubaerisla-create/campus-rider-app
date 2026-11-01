package campusrider.exception;

import org.springframework.http.HttpStatus;

/** A requested resource (ride, location, account) does not exist. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
