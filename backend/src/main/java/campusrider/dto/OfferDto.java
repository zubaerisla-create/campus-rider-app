package campusrider.dto;

/** An incoming ride request offered to a driver. */
public record OfferDto(RideDto ride, double distanceToPickupMeters, double detourMeters, boolean shared,
                       long expiresAt, String summary) {
}
