package campusrider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Settings under "campusrider." in application.properties (all have defaults).
 *
 * @param dataDir              folder holding the CSV files (relative to the working directory)
 * @param maxDetourMeters      route-compatibility limit for shared rides
 * @param maxPickupMeters      a rickshaw farther than this from the pickup is not offered the ride
 * @param offerTimeoutSeconds  a driver who does not answer in time is treated as rejecting
 * @param searchTimeoutSeconds a request with no available rickshaw for this long becomes REJECTED
 * @param simulationSpeedMps   simulated rickshaw speed on the map (faster than real for demos)
 * @param simulationTickMs     how often the simulated drive advances and positions are pushed
 * @param allowedOrigins       browser origins allowed for CORS and the WebSocket handshake
 */
@ConfigurationProperties(prefix = "campusrider")
public record AppProperties(
        @DefaultValue("../data") String dataDir,
        @DefaultValue("750") double maxDetourMeters,
        @DefaultValue("2500") double maxPickupMeters,
        @DefaultValue("60") int offerTimeoutSeconds,
        @DefaultValue("180") int searchTimeoutSeconds,
        @DefaultValue("45") double simulationSpeedMps,
        @DefaultValue("500") long simulationTickMs,
        @DefaultValue("http://localhost:5173,http://127.0.0.1:5173") List<String> allowedOrigins) {
}
