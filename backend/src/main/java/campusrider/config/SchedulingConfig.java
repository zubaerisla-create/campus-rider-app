package campusrider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the simulated-drive ticker and the offer-timeout checks. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
