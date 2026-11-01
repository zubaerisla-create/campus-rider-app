package campusrider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** newPassword is optional; when given, currentPassword must match. */
public record UpdatePassengerProfileRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = Validation.NAME, message = Validation.NAME_MESSAGE) String fullName,
        @NotBlank @Pattern(regexp = Validation.PHONE, message = Validation.PHONE_MESSAGE) String phone,
        @Size(max = 128) String currentPassword,
        @Size(max = 128) String newPassword) {
}
