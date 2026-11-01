package campusrider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "(?i)passenger|driver", message = "must be passenger or driver") String role,
        @NotBlank(message = "Enter your username") @Size(max = 40) String username,
        @NotBlank(message = "Enter your password") @Size(max = 128) String password) {
}
