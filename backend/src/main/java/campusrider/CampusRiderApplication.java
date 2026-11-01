package campusrider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Campus Rider: JU campus rickshaw ride-sharing. REST API + WebSocket, CSV storage. */
/* No in-memory default user: accounts come only from passengers.csv / drivers.csv. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CampusRiderApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusRiderApplication.class, args);
    }
}
