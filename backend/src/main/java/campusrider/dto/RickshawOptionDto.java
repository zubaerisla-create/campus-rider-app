package campusrider.dto;

/** One row of the "available rickshaws" list for a trip, after compatibility evaluation and merge sort. */
public record RickshawOptionDto(String rickshawId, String driverName, String plateNumber, String vehicleType,
                                String locationId, String locationName, double distanceToPickupMeters,
                                int pickupEtaMinutes, int availableSeats, int maxSeats, int passengersOnBoard,
                                boolean shared, boolean compatible, boolean pickupOnRoute, double detourMeters,
                                double score, String summary, int fare, int rank) {
}
