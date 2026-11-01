package campusrider.model;

/**
 * A registered driver account stored in data/drivers.csv.
 * Each driver account is linked to exactly one campus Rickshaw.
 * The password is only ever kept as a BCrypt hash.
 */
public class DriverAccount {
    private final String id;
    private final String username;
    private String passwordHash;
    private String driverName;
    private final String rickshawId;
    private String phone;

    public DriverAccount(String id, String username, String passwordHash,
                         String driverName, String rickshawId, String phone) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.driverName = driverName;
        this.rickshawId = rickshawId;
        this.phone = phone == null ? "" : phone;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone;
    }

    @Override
    public String toString() {
        return driverName + " (" + rickshawId + " | @" + username + ")";
    }
}
