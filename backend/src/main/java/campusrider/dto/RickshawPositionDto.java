package campusrider.dto;

/** Simulated position of a rickshaw on the campus graph (x,y interpolated along the current road). */
public record RickshawPositionDto(String id, String locationId, String movingToId, double progress,
                                  double x, double y, boolean online, int availableSeats, int maxSeats,
                                  boolean autoDrive) {
}
