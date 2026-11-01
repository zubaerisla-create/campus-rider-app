package campusrider.dto;

/** Another passenger sharing the rickshaw. Only a first name is shared, never contact details. */
public record CoPassengerDto(String firstName, int seats, String pickupName, String destinationName,
                             String status) {
}
