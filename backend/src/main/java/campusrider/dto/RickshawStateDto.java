package campusrider.dto;

public record RickshawStateDto(String id, String plateNumber, String vehicleType, boolean online,
                               LocationDto location, LocationDto targetDestination, int maxSeats,
                               int bookedSeats, int blockedSeats, int availableSeats,
                               RickshawPositionDto position) {
}
