package campusrider.dto;

import jakarta.validation.constraints.NotNull;

public record AutoDriveRequest(@NotNull Boolean enabled) {
}
