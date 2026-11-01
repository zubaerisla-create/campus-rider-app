package campusrider.dto;

import java.util.List;

/** A Dijkstra shortest path with the trip estimate shown in the booking panel. */
public record RouteDto(String fromId, String toId, List<String> path, List<String> roadNames,
                       double distanceMeters, int estimatedMinutes, int fare, int sharedFare) {
}
