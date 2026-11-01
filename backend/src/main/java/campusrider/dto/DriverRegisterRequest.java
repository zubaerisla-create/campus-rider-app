package campusrider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DriverRegisterRequest(
        @NotBlank @Pattern(regexp = Validation.USERNAME, message = Validation.USERNAME_MESSAGE) String username,
        @NotBlank @Size(min = 6, max = 128, message = "Password must be 6-128 characters") String password,
        @NotBlank(message = "Enter your full name") @Size(max = 60) @Pattern(regexp = Validation.NAME,
                message = Validation.NAME_MESSAGE) String driverName,
        @NotBlank @Pattern(regexp = Validation.PHONE, message = Validation.PHONE_MESSAGE) String phone,
        @NotBlank(message = "Choose your starting stand") String startLocationId,
        @NotBlank(message = "Enter the rickshaw plate number") @Pattern(regexp = Validation.PLATE,
                message = Validation.PLATE_MESSAGE) String plateNumber,
        @Pattern(regexp = "(?i)ELECTRIC|PEDAL", message = "must be ELECTRIC or PEDAL") String vehicleType) {
}
