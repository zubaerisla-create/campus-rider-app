package campusrider.dto;

/** Shared validation patterns for request bodies. */
public final class Validation {
    public static final String USERNAME = "^[a-zA-Z0-9_.]{3,20}$";
    public static final String USERNAME_MESSAGE = "Username must be 3-20 letters, digits, '_' or '.'";
    public static final String NAME = "^[\\p{L} .'-]{2,60}$";
    public static final String NAME_MESSAGE = "Name may contain letters, spaces, '.', ''' and '-' only";
    /** Bangladeshi mobile number, e.g. 01711000001. */
    public static final String PHONE = "^01[3-9][0-9]{8}$";
    public static final String PHONE_MESSAGE = "Enter an 11-digit mobile number like 01711000001";
    public static final String PLATE = "^[A-Za-z0-9 -]{3,20}$";
    public static final String PLATE_MESSAGE = "Plate number may contain letters, digits, spaces and '-'";

    private Validation() {
    }
}
