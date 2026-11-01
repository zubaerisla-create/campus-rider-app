package campusrider.algorithms;

/** A road from one location to a neighbouring location. The weight is the distance in meters. */
public class Edge {
    private final String toId;
    private final double distanceMeters;
    private final String roadName;

    public Edge(String toId, double distanceMeters, String roadName) {
        this.toId = toId;
        this.distanceMeters = distanceMeters;
        this.roadName = roadName;
    }

    public String getToId() {
        return toId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getRoadName() {
        return roadName;
    }
}
