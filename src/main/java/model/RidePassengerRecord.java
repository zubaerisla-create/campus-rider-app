package model;

/**
 * Represents a passenger-to-rickshaw seat-sharing assignment stored in data/ride_passengers.csv.
 */
public class RidePassengerRecord {
    private final int rideId;
    private final String passengerId;
    private final String passengerName;
    private final String rickshawId;
    private final String pickupId;
    private final String destinationId;
    private final int seatsBooked;
    private final double detourMeters;
    private final RideStatus status;

    public RidePassengerRecord(int rideId, String passengerId, String passengerName, String rickshawId,
                               String pickupId, String destinationId, int seatsBooked,
                               double detourMeters, RideStatus status) {
        this.rideId = rideId;
        this.passengerId = passengerId == null ? "" : passengerId;
        this.passengerName = passengerName == null ? "" : passengerName;
        this.rickshawId = rickshawId == null ? "" : rickshawId;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.seatsBooked = seatsBooked;
        this.detourMeters = detourMeters;
        this.status = status;
    }

    public int getRideId() {
        return rideId;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public String getPickupId() {
        return pickupId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getSeatsBooked() {
        return seatsBooked;
    }

    public double getDetourMeters() {
        return detourMeters;
    }

    public RideStatus getStatus() {
        return status;
    }
}
