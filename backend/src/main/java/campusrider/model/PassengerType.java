package campusrider.model;

/**
 * Type of passenger. A smaller priority number is served first
 * when the PRIORITY queue policy is active.
 */
public enum PassengerType {
    EMERGENCY(1),
    STAFF(2),
    STUDENT(3);

    private final int priority;

    PassengerType(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
