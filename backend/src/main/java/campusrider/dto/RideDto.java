package campusrider.dto;

import java.util.List;
import java.util.Set;

/** A ride as seen by its passenger or its driver. */
public record RideDto(
        int id,
        String status,
        String statusLabel,
        String statusNote,
        LocationDto pickup,
        LocationDto destination,
        double distanceMeters,
        int seats,
        int fare,
        boolean emergency,
        boolean shared,
        double detourMeters,
        long requestedAt,
        long updatedAt,
        PersonDto passenger,
        DriverInfoDto driver,
        String offeredTo,
        RickshawPositionDto rickshaw,
        List<String> approachPath,
        List<String> tripPath,
        double metersToPickup,
        double metersToDropoff,
        List<CoPassengerDto> coPassengers,
        boolean arrivedAtPickup,
        boolean atDestination,
        boolean cancellable,
        Set<String> nextStates) {
}
