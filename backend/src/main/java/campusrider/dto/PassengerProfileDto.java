package campusrider.dto;

public record PassengerProfileDto(String id, String username, String fullName, String phone, String passengerType,
                                  int completedRides) {
}
