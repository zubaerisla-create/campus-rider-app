package campusrider.dto;

/** The signed-in user, as returned by /api/auth/me and the login endpoints. No secrets. */
public record SessionUserDto(String role, String id, String username, String name, String phone,
                             String passengerType, String rickshawId) {
}
