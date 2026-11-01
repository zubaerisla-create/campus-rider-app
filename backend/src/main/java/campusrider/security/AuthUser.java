package campusrider.security;

import java.io.Serializable;

/**
 * The principal stored in the HTTP session after login. It holds no password
 * or hash, only who the user is and which role they signed in with.
 */
public record AuthUser(Role role, String id, String username) implements Serializable {

    public enum Role {
        PASSENGER, DRIVER;

        public String authority() {
            return "ROLE_" + name();
        }
    }
}
