package campusrider.dto;

import java.util.List;

/** Everything the web map needs to draw the campus graph. */
public record CampusMapDto(List<LocationDto> locations, List<RoadDto> roads, double maxDetourMeters) {
}
