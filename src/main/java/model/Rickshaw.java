package model;

/**
 * A campus rickshaw. Supports up to {@link #DEFAULT_MAX_SEATS} passenger seats
 * for shared campus rides, while maintaining overall availability status.
 */
public class Rickshaw {
    public static final int DEFAULT_MAX_SEATS = 3;

    private final String id;
    private final String driverName;
    private String locationId;
    private boolean available;
    private final int maxSeats;
    private int occupiedSeats;

    // Live tracking state: when moving between two nodes on the map
    private String movingFromId;
    private String movingToId;
    private double segmentProgress; // 0.0 to 1.0 along (movingFromId -> movingToId)

    public Rickshaw(String id, String driverName, String locationId, boolean available) {
        this(id, driverName, locationId, available, DEFAULT_MAX_SEATS, available ? 0 : 1);
    }

    public Rickshaw(String id, String driverName, String locationId, boolean available, int maxSeats, int occupiedSeats) {
        this.id = id;
        this.driverName = driverName;
        this.locationId = locationId;
        this.available = available;
        this.maxSeats = Math.max(1, maxSeats);
        this.occupiedSeats = Math.max(0, Math.min(this.maxSeats, occupiedSeats));
        this.movingFromId = locationId;
        this.movingToId = locationId;
        this.segmentProgress = 0.0;
    }

    public String getId() {
        return id;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
        this.movingFromId = locationId;
        this.movingToId = locationId;
        this.segmentProgress = 0.0;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
        if (available) {
            this.occupiedSeats = 0;
        } else if (this.occupiedSeats == 0) {
            this.occupiedSeats = 1;
        }
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public int getOccupiedSeats() {
        return occupiedSeats;
    }

    public int getAvailableSeats() {
        return Math.max(0, maxSeats - occupiedSeats);
    }

    public void setOccupiedSeats(int occupiedSeats) {
        this.occupiedSeats = Math.max(0, Math.min(maxSeats, occupiedSeats));
        this.available = (this.occupiedSeats == 0);
    }

    public boolean hasSeatsFor(int seats) {
        return seats > 0 && getAvailableSeats() >= seats;
    }

    public String getMovingFromId() {
        return movingFromId == null ? locationId : movingFromId;
    }

    public String getMovingToId() {
        return movingToId == null ? locationId : movingToId;
    }

    public double getSegmentProgress() {
        return segmentProgress;
    }

    public boolean isMoving() {
        return movingFromId != null && movingToId != null
                && !movingFromId.equals(movingToId)
                && segmentProgress > 0.0 && segmentProgress < 1.0;
    }

    public void setLiveMovement(String fromId, String toId, double progress) {
        this.movingFromId = fromId;
        this.movingToId = toId;
        this.segmentProgress = Math.max(0.0, Math.min(1.0, progress));
    }

    @Override
    public String toString() {
        return id + " - " + driverName + " (" + getAvailableSeats() + "/" + maxSeats + " seats free)";
    }
}
