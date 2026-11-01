package campusrider.algorithms;

import java.util.List;

/** Result of a shortest path search: the list of location ids and the total distance. */
public class RouteResult {
    private final boolean found;
    private final List<String> path;
    private final double totalDistanceMeters;
    private final String message;

    private RouteResult(boolean found, List<String> path, double totalDistanceMeters, String message) {
        this.found = found;
        this.path = List.copyOf(path);
        this.totalDistanceMeters = totalDistanceMeters;
        this.message = message;
    }

    public static RouteResult found(List<String> path, double totalDistanceMeters) {
        return new RouteResult(true, path, totalDistanceMeters, "Shortest route found");
    }

    public static RouteResult notFound(String message) {
        return new RouteResult(false, List.of(), -1, message);
    }

    public boolean isFound() {
        return found;
    }

    public List<String> getPath() {
        return path;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public String getMessage() {
        return message;
    }
}
