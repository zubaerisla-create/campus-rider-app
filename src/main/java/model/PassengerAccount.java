package model;

/**
 * A registered passenger account stored in data/passengers.csv.
 */
public class PassengerAccount {
    private final String id;
    private final String username;
    private final String password;
    private final String fullName;
    private final PassengerType passengerType;
    private final String phone;

    public PassengerAccount(String id, String username, String password,
                            String fullName, PassengerType passengerType, String phone) {
        this.id = id;
        this.username = username;
        this.password = password;
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

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public PassengerType getPassengerType() {
        return passengerType;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public String toString() {
        return fullName + " (@" + username + " - " + passengerType + ")";
    }
}
