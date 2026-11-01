package model;

/**
 * Ride lifecycle state machine:
 * PENDING (Pending) -> MATCHED (Accepted) -> IN_PROGRESS (Started) -> COMPLETED (Completed)
 * PENDING or MATCHED -> CANCELLED (Cancelled)
 */
public enum RideStatus {
    PENDING,
    MATCHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canMoveTo(RideStatus next) {
        switch (this) {
            case PENDING:
                return next == MATCHED || next == CANCELLED;
            case MATCHED:
                return next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS:
                return next == COMPLETED;
            default:
                return false; // COMPLETED and CANCELLED are final
        }
    }

    /** True while the ride is holding a rickshaw. */
    public boolean isActive() {
        return this == MATCHED || this == IN_PROGRESS;
    }

    /** User-friendly lifecycle label: Pending -> Accepted -> Started -> Completed. */
    public String getDisplayLabel() {
        switch (this) {
            case PENDING:
                return "Pending";
            case MATCHED:
                return "Accepted (Matched)";
            case IN_PROGRESS:
                return "Started (In Progress)";
            case COMPLETED:
                return "Completed";
            case CANCELLED:
                return "Cancelled";
            default:
                return name();
        }
    }
}
