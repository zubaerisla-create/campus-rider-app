package campusrider.dto;

import java.util.List;

/**
 * Everything the driver dashboard shows.
 *
 * @param waitingAtStop true when the rickshaw is parked at its next stop and needs Start / Complete
 * @param nextStop      the stop the rickshaw is driving to (null when idle)
 */
public record DriverDashboardDto(RickshawStateDto rickshaw, List<OfferDto> offers, List<RideDto> activeRides,
                                 List<StopDto> stops, List<String> plannedPath, StopDto nextStop,
                                 boolean waitingAtStop, int offerTimeoutSeconds) {
}
