package campusrider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PassengerRegisterRequest(
        @NotBlank @Pattern(regexp = Validation.USERNAME, message = Validation.USERNAME_MESSAGE) String username,
        @NotBlank @Size(min = 6, max = 128, message = "Password must be 6-128 characters") String password,
        @NotBlank(message = "Enter your full name") @Size(max = 60) @Pattern(regexp = Validation.NAME,
                message = Validation.NAME_MESSAGE) String fullName,
        @NotBlank @Pattern(regexp = Validation.PHONE, message = Validation.PHONE_MESSAGE) String phone,
        @Pattern(regexp = "(?i)STUDENT|STAFF", message = "must be STUDENT or STAFF") String passengerType) {
}
