package campusrider.model;

import campusrider.algorithms.RideStateMachine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A passenger's ride request. The id grows by one for every new request,
 * so a smaller id always means an earlier request.
 *
 * While PENDING, {@code offeredRickshawId} is the driver the request is currently
 * sent to. Once ACCEPTED, {@code rickshawId} is the rickshaw carrying the passenger.
 */
public class RideRequest {
    private final int id;
    private final String passengerId;
    private final String passengerName;
    private final PassengerType passengerType;
    private final String pickupId;
    private final String destinationId;
    private final double distanceMeters;
    private final int seatsRequested;
    private final boolean emergency;
    private RideStatus status;
    private String rickshawId = "";
    private String offeredRickshawId = "";
    private String preferredRickshawId = "";
    private long offeredAt;
    private double detourMeters;
    private int fare;
    private boolean shared;
    private final long requestedAt;
    private long updatedAt;
    private String statusNote = "";
    private final Set<String> rejectedByRickshaws = new LinkedHashSet<>();

    public RideRequest(int id, String passengerId, String passengerName, PassengerType passengerType,
                       String pickupId, String destinationId, double distanceMeters, int seatsRequested,
                       boolean emergency, RideStatus status, long requestedAt) {
        this.id = id;
        this.passengerId = passengerId == null ? "" : passengerId;
        this.passengerName = passengerName;
        this.passengerType = passengerType == null ? PassengerType.STUDENT : passengerType;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.distanceMeters = distanceMeters;
        this.seatsRequested = Math.max(1, Math.min(Rickshaw.DEFAULT_MAX_SEATS, seatsRequested));
        this.emergency = emergency || this.passengerType == PassengerType.EMERGENCY;
        this.status = status;
        this.requestedAt = requestedAt;
        this.updatedAt = requestedAt;
    }

    public int getId() {
        return id;
    }

    public String getPassengerId() {
        return passengerId;
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

    public boolean isEmergency() {
        return emergency;
    }

    /** Priority used by the emergency PriorityQueue: smaller number is served first. */
    public int getPriority() {
        return emergency ? 0 : passengerType.getPriority();
    }

    public RideStatus getStatus() {
        return status;
    }

    /** Changes the status only if the {@link RideStateMachine} allows it. */
    public void moveTo(RideStatus next) {
        status = RideStateMachine.transition(id, status, next);
        touch();
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public void setRickshawId(String rickshawId) {
        this.rickshawId = rickshawId == null ? "" : rickshawId;
    }

    public String getOfferedRickshawId() {
        return offeredRickshawId;
    }

    public void setOfferedRickshawId(String offeredRickshawId) {
        this.offeredRickshawId = offeredRickshawId == null ? "" : offeredRickshawId;
    }

    public boolean hasOffer() {
        return !offeredRickshawId.isEmpty();
    }

    public String getPreferredRickshawId() {
        return preferredRickshawId;
    }

    public void setPreferredRickshawId(String preferredRickshawId) {
        this.preferredRickshawId = preferredRickshawId == null ? "" : preferredRickshawId;
    }

    public long getOfferedAt() {
        return offeredAt;
    }

    public void setOfferedAt(long offeredAt) {
        this.offeredAt = offeredAt;
    }

    public double getDetourMeters() {
        return detourMeters;
    }

    public void setDetourMeters(double detourMeters) {
        this.detourMeters = Math.max(0.0, detourMeters);
    }

    public int getFare() {
        return fare;
    }

    public void setFare(int fare) {
        this.fare = Math.max(0, fare);
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String getStatusNote() {
        return statusNote;
    }

    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote == null ? "" : statusNote;
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
}
