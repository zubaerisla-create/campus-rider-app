package campusrider.integration;

import campusrider.CampusRiderApplication;
import campusrider.storage.CsvStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts the real Spring Boot application on a random port with its own CSV data
 * folder, and provides an HTTP client that behaves like the browser: it keeps the
 * session cookie and sends the CSRF token header on every request.
 */
final class AppHarness implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();

    final ConfigurableApplicationContext context;
    final int port;

    private AppHarness(ConfigurableApplicationContext context) {
        this.context = context;
        this.port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    static AppHarness start(Path dataDir, String... extraProperties) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String p : List.of("server.port=0", "campusrider.data-dir=" + dataDir,
                "campusrider.offer-timeout-seconds=600", "campusrider.search-timeout-seconds=600",
                "logging.level.root=WARN")) {
            props.put(p.substring(0, p.indexOf('=')), p.substring(p.indexOf('=') + 1));
        }
        for (String p : extraProperties) {
            props.put(p.substring(0, p.indexOf('=')), p.substring(p.indexOf('=') + 1)); // test overrides win
        }
        // Command-line arguments override application.properties (".properties()" would only set defaults,
        // and the app would then use the real ../data folder).
        String[] args = props.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CampusRiderApplication.class).run(args);
        Path used = ctx.getBean(CsvStorage.class).getDataDir();
        if (!used.equals(dataDir.toAbsolutePath().normalize())) {
            ctx.close();
            throw new IllegalStateException("Test app is using " + used + " instead of " + dataDir);
        }
        return new AppHarness(ctx);
    }

    <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    Client client() {
        return new Client();
    }

    Client login(String role, String username, String password) {
        Client c = client();
        Response r = c.post("/api/auth/login",
                "{\"role\":\"" + role + "\",\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (r.status != 200) {
            throw new IllegalStateException("login failed for " + username + ": " + r.status + " " + r.body);
        }
        return c;
    }

    @Override
    public void close() {
        context.close();
    }

    record Response(int status, JsonNode body, String raw) {
        JsonNode json() {
            return body;
        }
    }

    final class Client {
        final CookieManager cookies = new CookieManager();
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        boolean sendCsrf = true;

        Response get(String path) {
            return send("GET", path, null);
        }

        Response post(String path, String json) {
            return send("POST", path, json);
        }

        Response post(String path) {
            return send("POST", path, null);
        }

        Response put(String path, String json) {
            return send("PUT", path, json);
        }

        String cookie(String name) {
            for (HttpCookie c : cookies.getCookieStore().getCookies()) {
                if (c.getName().equals(name)) {
                    return c.getValue();
                }
            }
            return null;
        }

        Response send(String method, String path, String json) {
            try {
                if (!method.equals("GET") && sendCsrf && cookie("XSRF-TOKEN") == null) {
                    send("GET", "/api/auth/csrf", null);
                }
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(json));
                if (json != null) {
                    b.header("Content-Type", "application/json");
                }
                if (sendCsrf && cookie("XSRF-TOKEN") != null) {
                    b.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
                }
                HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode node = res.body() == null || res.body().isBlank() ? null : JSON.readTree(res.body());
                return new Response(res.statusCode(), node, res.body());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
