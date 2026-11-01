package campusrider.model;

/**
 * A campus rickshaw with up to {@link #DEFAULT_MAX_SEATS} passenger seats.
 *
 * Seats:
 *   bookedSeats  - seats held by ACCEPTED / STARTED rides (rebuilt from rides, never set by hand)
 *   blockedSeats - seats the driver is not offering (set from the driver dashboard)
 *   available    = maxSeats - bookedSeats - blockedSeats
 *
 * Position (simulated tracking on the campus graph):
 *   locationId     - the last campus vertex the rickshaw reached
 *   movingToId     - the neighbouring vertex it is driving towards ("" when parked)
 *   segmentProgress- 0.0 .. 1.0 along the road locationId -> movingToId
 */
public class Rickshaw {
    public static final int DEFAULT_MAX_SEATS = 3;

    private final String id;
    private String driverName;
    private String locationId;
    private String targetDestinationId;
    private boolean online;
    private final int maxSeats;
    private int bookedSeats;
    private int blockedSeats;
    private String plateNumber;
    private String vehicleType;

    private String movingToId = "";
    private double segmentProgress;
    private boolean autoDrive;

    public Rickshaw(String id, String driverName, String locationId, boolean online) {
        this(id, driverName, locationId, online, DEFAULT_MAX_SEATS);
    }

    public Rickshaw(String id, String driverName, String locationId, boolean online, int maxSeats) {
        this.id = id;
        this.driverName = driverName;
        this.locationId = locationId;
        this.online = online;
        this.maxSeats = Math.max(1, Math.min(DEFAULT_MAX_SEATS, maxSeats));
        this.targetDestinationId = "";
        this.plateNumber = "";
        this.vehicleType = "ELECTRIC";
    }

    public String getId() {
        return id;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getLocationId() {
        return locationId;
    }

    /** Parks the rickshaw exactly on a vertex (clears any movement in progress). */
    public void setLocationId(String locationId) {
        this.locationId = locationId;
        this.movingToId = "";
        this.segmentProgress = 0.0;
    }

    public String getTargetDestinationId() {
        return targetDestinationId == null ? "" : targetDestinationId;
    }

    public void setTargetDestinationId(String targetDestinationId) {
        this.targetDestinationId = targetDestinationId == null ? "" : targetDestinationId;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public int getBookedSeats() {
        return bookedSeats;
    }

    public void setBookedSeats(int bookedSeats) {
        this.bookedSeats = Math.max(0, Math.min(maxSeats, bookedSeats));
        // A driver can never block seats that passengers now hold.
        this.blockedSeats = Math.min(blockedSeats, maxSeats - this.bookedSeats);
    }

    public int getBlockedSeats() {
        return blockedSeats;
    }

    public void setBlockedSeats(int blockedSeats) {
        this.blockedSeats = Math.max(0, Math.min(maxSeats - bookedSeats, blockedSeats));
    }

    public int getAvailableSeats() {
        return Math.max(0, maxSeats - bookedSeats - blockedSeats);
    }

    public boolean hasSeatsFor(int seats) {
        return seats > 0 && getAvailableSeats() >= seats;
    }

    /** Online and has at least one free seat. */
    public boolean isAvailable() {
        return online && getAvailableSeats() > 0;
    }

    public String getPlateNumber() {
        return plateNumber == null ? "" : plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber == null ? "" : plateNumber;
    }

    public String getVehicleType() {
        return vehicleType == null || vehicleType.isBlank() ? "ELECTRIC" : vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    // ------------------------------------------------------------ movement

    public String getMovingToId() {
        return movingToId == null ? "" : movingToId;
    }

    public double getSegmentProgress() {
        return segmentProgress;
    }

    public boolean isMoving() {
        return !getMovingToId().isEmpty();
    }

    public void setMovement(String toId, double progress) {
        this.movingToId = toId == null ? "" : toId;
        this.segmentProgress = Math.max(0.0, Math.min(1.0, progress));
    }

    /**
     * The vertex routing should start from. While driving along a road the
     * rickshaw always finishes that road, so plans start at the road's end.
     */
    public String getRoutingOriginId() {
        return isMoving() ? movingToId : locationId;
    }

    public boolean isAutoDrive() {
        return autoDrive;
    }

    public void setAutoDrive(boolean autoDrive) {
        this.autoDrive = autoDrive;
    }

    @Override
    public String toString() {
        return id + " - " + driverName + " (" + getAvailableSeats() + "/" + maxSeats + " seats free)";
    }
}
