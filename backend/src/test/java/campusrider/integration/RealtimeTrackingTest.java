package campusrider.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** WebSocket /ws/tracking: authenticated, origin-checked, pushes ride updates and shared positions. */
class RealtimeTrackingTest {
    static final String ORIGIN = "http://localhost:5173";

    @TempDir
    Path dir;
    AppHarness app;

    @AfterEach
    void stop() {
        if (app != null) {
            app.close();
        }
    }

    /** Collects every text frame as JSON. */
    static final class Listener implements WebSocket.Listener {
        final LinkedBlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                try {
                    events.add(AppHarness.JSON.readTree(partial.toString()));
                } catch (Exception ignored) {
                    // not JSON
                }
                partial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        JsonNode await(Predicate<JsonNode> match) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                JsonNode e = events.poll(200, TimeUnit.MILLISECONDS);
                if (e != null && match.test(e)) {
                    return e;
                }
            }
            return null;
        }
    }

    WebSocket connect(HttpClient http, Listener listener, String origin) {
        return http.newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + app.port + "/ws/tracking"), listener).join();
    }

    static boolean positionOf(JsonNode e, String rickshawId, String locationId) {
        if (!"positions".equals(e.path("type").asText())) {
            return false;
        }
        for (JsonNode r : e.get("rickshaws")) {
            if (r.get("id").asText().equals(rickshawId) && r.get("locationId").asText().equals(locationId)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void sharedPassengersReceiveRideUpdatesAndTheSameLivePosition() throws Exception {
        app = AppHarness.start(dir);
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        AppHarness.Client nila = app.login("passenger", "nila", "1234");
        AppHarness.Client mokbul = app.login("driver", "mokbul", "1234");
        Listener tl = new Listener();
        Listener nl = new Listener();
        Listener dl = new Listener();
        connect(tanvir.http, tl, ORIGIN);
        connect(nila.http, nl, ORIGIN);
        connect(mokbul.http, dl, ORIGIN);
        assertNotNull(tl.await(e -> "hello".equals(e.path("type").asText())));

        int r1 = tanvir.post("/api/rides/request", RideFlowTest.trip("bottola", "dairy_gate", 1)).json().get("id").asInt();
        assertNotNull(dl.await(e -> "driver_update".equals(e.path("type").asText()) && e.path("rideId").asInt() == r1));
        mokbul.post("/api/rides/" + r1 + "/accept");
        assertNotNull(tl.await(e -> "ride_update".equals(e.path("type").asText())
                && "ACCEPTED".equals(e.path("status").asText())));

        // Nila picks Mokbul's (already occupied) rickshaw from the options list to share it.
        int r2 = nila.post("/api/rides/request", "{\"pickupId\":\"rafiq_jabbar_hall\",\"destinationId\":\"dairy_gate\","
                + "\"seats\":1,\"preferredRickshawId\":\"JU-RICK-102\"}").json().get("id").asInt();
        assertEquals(200, mokbul.post("/api/rides/" + r2 + "/accept").status());
        // Tanvir is told that a co-passenger joined.
        assertNotNull(tl.await(e -> "ride_update".equals(e.path("type").asText()) && e.path("rideId").asInt() == r1
                && e.path("message").asText().contains("Nila")));

        mokbul.post("/api/rides/" + r1 + "/start");
        assertEquals(200, mokbul.post("/api/drivers/move").status()); // bottola -> rafiq_jabbar_hall
        assertNotNull(tl.await(e -> positionOf(e, "JU-RICK-102", "rafiq_jabbar_hall")));
        // Nila hears that the rickshaw arrived at her pickup, then sees the same position as Tanvir.
        assertNotNull(nl.await(e -> "ride_update".equals(e.path("type").asText())
                && e.path("message").asText().contains("arrived")));
        assertNotNull(nl.await(e -> positionOf(e, "JU-RICK-102", "rafiq_jabbar_hall")));
    }

    @Test
    void anonymousAndForeignOriginConnectionsAreRefused() {
        app = AppHarness.start(dir);
        HttpClient anonymous = HttpClient.newHttpClient();
        assertThrows(CompletionException.class, () -> connect(anonymous, new Listener(), ORIGIN));
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        assertThrows(CompletionException.class, () -> connect(tanvir.http, new Listener(), "https://evil.example"));
    }
}
