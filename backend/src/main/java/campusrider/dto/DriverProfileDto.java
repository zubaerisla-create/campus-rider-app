package campusrider.dto;

public record DriverProfileDto(String id, String username, String driverName, String phone, String rickshawId,
                               String plateNumber, String vehicleType, int completedRides) {
}
