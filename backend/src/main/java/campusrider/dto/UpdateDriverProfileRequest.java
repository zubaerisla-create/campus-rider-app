package campusrider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** newPassword is optional; when given, currentPassword must match. */
public record UpdateDriverProfileRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = Validation.NAME, message = Validation.NAME_MESSAGE) String driverName,
        @NotBlank @Pattern(regexp = Validation.PHONE, message = Validation.PHONE_MESSAGE) String phone,
        @NotBlank @Pattern(regexp = Validation.PLATE, message = Validation.PLATE_MESSAGE) String plateNumber,
        @Pattern(regexp = "(?i)ELECTRIC|PEDAL", message = "must be ELECTRIC or PEDAL") String vehicleType,
        @Size(max = 128) String currentPassword,
        @Size(max = 128) String newPassword) {
}
