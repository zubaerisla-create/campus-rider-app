package campusrider.config;

import campusrider.tracking.TrackingWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the real-time tracking endpoint ws://host/ws/tracking.
 * Only the configured browser origins may open it (protects against cross-site
 * WebSocket hijacking with the session cookie), and Spring Security requires login.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TrackingWebSocketHandler handler;
    private final AppProperties properties;

    public WebSocketConfig(TrackingWebSocketHandler handler, AppProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/tracking")
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
}
