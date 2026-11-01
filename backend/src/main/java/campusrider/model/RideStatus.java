package campusrider.model;

/**
 * The states of a ride. The allowed transitions between them are defined in
 * {@link campusrider.algorithms.RideStateMachine}:
 *
 *   PENDING -> ACCEPTED -> STARTED -> COMPLETED
 *   PENDING -> REJECTED            (no suitable driver accepted)
 *   PENDING | ACCEPTED -> CANCELLED (passenger cancelled)
 */
public enum RideStatus {
    PENDING("Finding your rickshaw"),
    ACCEPTED("Driver on the way"),
    STARTED("On trip"),
    COMPLETED("Completed"),
    REJECTED("No rickshaw available"),
    CANCELLED("Cancelled");

    private final String label;

    RideStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True while the ride is holding seats on a rickshaw. */
    public boolean isActive() {
        return this == ACCEPTED || this == STARTED;
    }

    /** True while the passenger still has an open ride (searching, assigned or on the trip). */
    public boolean isOpen() {
        return this == PENDING || isActive();
    }

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    /**
     * Parses a status from CSV. The desktop version stored MATCHED and
     * IN_PROGRESS, which map to ACCEPTED and STARTED.
     */
    public static RideStatus fromCsv(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        switch (v) {
            case "MATCHED":
                return ACCEPTED;
            case "IN_PROGRESS":
                return STARTED;
            default:
                return RideStatus.valueOf(v);
        }
    }
}
