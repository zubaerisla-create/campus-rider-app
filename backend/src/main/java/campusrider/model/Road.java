package campusrider.model;

/** A two-way road between two locations, as read from roads.csv. */
public class Road {
    private final String fromId;
    private final String toId;
    private final double distanceMeters;
    private final String name;

    public Road(String fromId, String toId, double distanceMeters, String name) {
        this.fromId = fromId;
        this.toId = toId;
        this.distanceMeters = distanceMeters;
        this.name = name;
    }

    public String getFromId() {
        return fromId;
    }

    public String getToId() {
        return toId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getName() {
        return name;
    }
}
