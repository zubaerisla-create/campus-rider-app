package campusrider.tracking;

import campusrider.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;

/**
 * /ws/tracking endpoint. The handshake is an ordinary HTTP request, so Spring
 * Security has already checked the session cookie; the authenticated user is
 * available as the session principal. Anonymous connections are closed.
 * The channel is server-to-browser only: incoming messages other than "ping" are ignored.
 */
@Component
public class TrackingWebSocketHandler extends TextWebSocketHandler {
    private final RealtimeNotifier notifier;

    public TrackingWebSocketHandler(RealtimeNotifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String key = userKey(session);
        if (key == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Not signed in"));
            return;
        }
        session.getAttributes().put("userKey", key);
        notifier.register(key, session);
        session.sendMessage(new TextMessage("{\"type\":\"hello\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object key = session.getAttributes().get("userKey");
        if (key != null) {
            notifier.unregister(key.toString(), session);
        }
    }

    private static String userKey(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthUser user) {
            return user.role() == AuthUser.Role.DRIVER
                    ? RealtimeNotifier.driverKey(user.id())
                    : RealtimeNotifier.passengerKey(user.id());
        }
        return null;
    }
}
