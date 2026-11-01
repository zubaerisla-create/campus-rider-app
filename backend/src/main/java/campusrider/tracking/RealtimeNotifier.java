package campusrider.tracking;

import campusrider.repository.DriverRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes real-time events to connected browsers over the /ws/tracking WebSocket.
 *
 * Sessions are grouped by user key ("PASSENGER:P-101", "DRIVER:D-101"), so an event
 * reaches every tab that user has open. Event types:
 *   ride_update   - a ride the passenger is on changed (accepted, arrived, started, ...)
 *   driver_update - the driver has a new request or one of their rides changed
 *   positions     - simulated rickshaw positions on the campus graph (sent to everyone)
 * Each session is wrapped in ConcurrentWebSocketSessionDecorator, because a raw
 * WebSocketSession must not be written from two threads at once.
 */
@Component
public class RealtimeNotifier {
    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final DriverRepository drivers;

    public RealtimeNotifier(ObjectMapper mapper, DriverRepository drivers) {
        this.mapper = mapper;
        this.drivers = drivers;
    }

    public static String passengerKey(String passengerId) {
        return "PASSENGER:" + passengerId;
    }

    public static String driverKey(String driverId) {
        return "DRIVER:" + driverId;
    }

    void register(String userKey, WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 2000, 256 * 1024);
        decorated.put(session.getId(), safe);
        sessionsByUser.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    void unregister(String userKey, WebSocketSession session) {
        WebSocketSession safe = decorated.remove(session.getId());
        Set<WebSocketSession> set = sessionsByUser.get(userKey);
        if (set != null && safe != null) {
            set.remove(safe);
        }
    }

    public int connectionCount() {
        return decorated.size();
    }

    public void toPassenger(String passengerId, String type, Map<String, Object> data) {
        if (passengerId != null && !passengerId.isEmpty()) {
            send(passengerKey(passengerId), event(type, data));
        }
    }

    public void toDriverOfRickshaw(String rickshawId, String type, Map<String, Object> data) {
        drivers.findByRickshawId(rickshawId).ifPresent(d -> send(driverKey(d.getId()), event(type, data)));
    }

    public void broadcast(String type, Map<String, Object> data) {
        String json = event(type, data);
        if (json == null) {
            return;
        }
        for (WebSocketSession s : decorated.values()) {
            write(s, json);
        }
    }

    private String event(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("at", System.currentTimeMillis());
        payload.putAll(data);
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise {} event: {}", type, e.getMessage());
            return null;
        }
    }

    private void send(String userKey, String json) {
        if (json == null) {
            return;
        }
        for (WebSocketSession s : sessionsByUser.getOrDefault(userKey, Set.of())) {
            write(s, json);
        }
    }

    private void write(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            log.debug("WebSocket send failed for {}: {}", session.getId(), e.getMessage());
        }
    }
}
