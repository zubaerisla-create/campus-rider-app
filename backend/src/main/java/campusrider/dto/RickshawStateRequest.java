package campusrider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Driver dashboard: where the rickshaw is, where the driver is heading, and how many seats are offered. */
public record RickshawStateRequest(
        @NotBlank @Size(max = 64) String currentLocationId,
        @Size(max = 64) String targetDestinationId,
        @Min(0) @Max(3) int availableSeats) {
}
