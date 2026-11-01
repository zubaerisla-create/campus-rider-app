package model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A passenger's ride request. The id grows by one for every new request,
 * so a smaller id always means an earlier request.
 */
public class RideRequest {
    private final int id;
    private String passengerId;
    private final String passengerName;
    private final PassengerType passengerType;
    private final String pickupId;
    private final String destinationId;
    private final double distanceMeters;
    private final int seatsRequested;
    private RideStatus status;
    private String rickshawId;         // assigned rickshaw once accepted/matched
    private String offeredRickshawId;  // recommended/targeted rickshaw while PENDING
    private double detourMeters;       // route compatibility detour overhead (for shared rides)
    private final Set<String> rejectedByRickshaws = new LinkedHashSet<>();

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId) {
        this(id, passengerName, passengerType, pickupId, destinationId, distanceMeters, status, rickshawId, 1);
    }

    public RideRequest(int id, String passengerName, PassengerType passengerType, String pickupId,
                       String destinationId, double distanceMeters, RideStatus status, String rickshawId,
                       int seatsRequested) {
        this.id = id;
        this.passengerId = "";
        this.passengerName = passengerName;
        this.passengerType = passengerType;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.distanceMeters = distanceMeters;
        this.status = status;
        this.rickshawId = rickshawId == null ? "" : rickshawId;
        this.offeredRickshawId = "";
        this.detourMeters = 0.0;
        this.seatsRequested = Math.max(1, Math.min(Rickshaw.DEFAULT_MAX_SEATS, seatsRequested));
    }

    public int getId() {
        return id;
    }

    public String getPassengerId() {
        return passengerId == null ? "" : passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId == null ? "" : passengerId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public PassengerType getPassengerType() {
        return passengerType;
    }

    public String getPickupId() {
        return pickupId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public int getSeatsRequested() {
        return seatsRequested;
    }

    public RideStatus getStatus() {
        return status;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public void setRickshawId(String rickshawId) {
        this.rickshawId = rickshawId == null ? "" : rickshawId;
    }

    public String getOfferedRickshawId() {
        return offeredRickshawId == null ? "" : offeredRickshawId;
    }

    public void setOfferedRickshawId(String offeredRickshawId) {
        this.offeredRickshawId = offeredRickshawId == null ? "" : offeredRickshawId;
    }

    public double getDetourMeters() {
        return detourMeters;
    }

    public void setDetourMeters(double detourMeters) {
        this.detourMeters = Math.max(0.0, detourMeters);
    }

    public void addRejectedBy(String driverRickshawId) {
        if (driverRickshawId != null && !driverRickshawId.isBlank()) {
            rejectedByRickshaws.add(driverRickshawId);
        }
    }

    public boolean isRejectedBy(String driverRickshawId) {
        return rejectedByRickshaws.contains(driverRickshawId);
    }

    public Set<String> getRejectedByRickshaws() {
        return Collections.unmodifiableSet(rejectedByRickshaws);
    }

    /** Changes the status only if the lifecycle allows it. */
    public void moveTo(RideStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ride #" + id + " cannot change from " + status + " to " + next);
        }
        status = next;
    }
}
