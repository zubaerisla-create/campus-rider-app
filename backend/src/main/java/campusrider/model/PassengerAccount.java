package campusrider.model;

/**
 * A registered passenger account stored in data/passengers.csv.
 * The password is only ever kept as a BCrypt hash.
 */
public class PassengerAccount {
    private final String id;
    private final String username;
    private String passwordHash;
    private String fullName;
    private final PassengerType passengerType;
    private String phone;

    public PassengerAccount(String id, String username, String passwordHash,
                            String fullName, PassengerType passengerType, String phone) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.passengerType = passengerType == null ? PassengerType.STUDENT : passengerType;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public PassengerType getPassengerType() {
        return passengerType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone;
    }

    @Override
    public String toString() {
        return fullName + " (@" + username + " - " + passengerType + ")";
    }
}
