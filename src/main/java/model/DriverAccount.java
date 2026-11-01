package model;

/**
 * A registered driver account stored in data/drivers.csv.
 * Each driver account is linked to a campus Rickshaw.
 */
public class DriverAccount {
    private final String id;
    private final String username;
    private final String password;
    private final String driverName;
    private final String rickshawId;
    private final String phone;

    public DriverAccount(String id, String username, String password,
                         String driverName, String rickshawId, String phone) {
        this.id = id;
        this.username = username;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getRickshawId() {
        return rickshawId;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public String toString() {
        return driverName + " (" + rickshawId + " | @" + username + ")";
    }
}
