package campusrider.dto;

/** A stop in a rickshaw's plan (driver view). */
public record StopDto(int rideId, String type, String locationId, String locationName, String passengerName,
                      int seats) {
}
