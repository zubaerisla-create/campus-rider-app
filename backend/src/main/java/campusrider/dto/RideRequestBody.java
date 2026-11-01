package campusrider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/rides/request. preferredRickshawId is optional (empty = best match automatically). */
public record RideRequestBody(
        @NotBlank(message = "Choose a pickup point") @Size(max = 64) String pickupId,
        @NotBlank(message = "Choose a destination") @Size(max = 64) String destinationId,
        @Min(value = 1, message = "At least 1 seat") @Max(value = 3, message = "A rickshaw has at most 3 seats") int seats,
        boolean emergency,
        @Size(max = 32) String preferredRickshawId) {
}
