package campusrider.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthAndSecurityTest {

    @TempDir
    static Path dir;
    static AppHarness app;

    @BeforeAll
    static void start() {
        app = AppHarness.start(dir);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @Test
    void demoAccountsLogInWithTheDemoPasswordAndPasswordsAreStoredAsBcrypt() throws Exception {
        for (String p : new String[]{"tanvir", "nila", "rafiq", "emergency"}) {
            assertNotNull(app.login("passenger", p, "1234"));
        }
        for (String d : new String[]{"karim", "mokbul", "salam", "rahim", "jamal"}) {
            assertNotNull(app.login("driver", d, "1234"));
        }
        String passengers = Files.readString(dir.resolve("passengers.csv"));
        String drivers = Files.readString(dir.resolve("drivers.csv"));
        assertFalse(passengers.contains(",1234,"));
        assertFalse(drivers.contains(",1234,"));
        assertTrue(passengers.contains("$2a$10$"));
    }

    @Test
    void wrongPasswordUnknownUserAndWrongRoleAreRejected() {
        AppHarness.Client c = app.client();
        assertEquals(401, c.post("/api/auth/login", "{\"role\":\"passenger\",\"username\":\"tanvir\",\"password\":\"x\"}").status());
        assertEquals(401, c.post("/api/auth/login", "{\"role\":\"passenger\",\"username\":\"ghost\",\"password\":\"1234\"}").status());
        // tanvir is a passenger, not a driver
        assertEquals(401, c.post("/api/auth/login", "{\"role\":\"driver\",\"username\":\"tanvir\",\"password\":\"1234\"}").status());
        assertEquals(400, c.post("/api/auth/login", "{\"role\":\"admin\",\"username\":\"tanvir\",\"password\":\"1234\"}").status());
    }

    @Test
    void repeatedFailuresLockTheAccountTemporarily() {
        AppHarness.Client c = app.client();
        String bad = "{\"role\":\"driver\",\"username\":\"guesser\",\"password\":\"wrong\"}";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, c.post("/api/auth/login", bad).status());
        }
        assertEquals(429, c.post("/api/auth/login", bad).status());
    }

    @Test
    void stateChangingRequestsNeedTheCsrfToken() {
        AppHarness.Client c = app.client();
        c.sendCsrf = false;
        assertEquals(403, c.post("/api/auth/login", "{\"role\":\"passenger\",\"username\":\"tanvir\",\"password\":\"1234\"}").status());
    }

    @Test
    void registrationValidatesInputHashesPasswordAndSignsIn() throws Exception {
        AppHarness.Client c = app.client();
        AppHarness.Response bad = c.post("/api/auth/passenger/register",
                "{\"username\":\"a\",\"password\":\"123\",\"fullName\":\"\",\"phone\":\"12\"}");
        assertEquals(400, bad.status());
        assertNotNull(bad.json().get("fieldErrors"));

        AppHarness.Response ok = c.post("/api/auth/passenger/register",
                "{\"username\":\"Sadia_Islam\",\"password\":\"secret99\",\"fullName\":\"Sadia Islam\",\"phone\":\"01911222333\",\"passengerType\":\"STUDENT\"}");
        assertEquals(201, ok.status());
        assertEquals("sadia_islam", ok.json().get("username").asText());
        assertNull(ok.json().get("password"));
        assertNull(ok.json().get("passwordHash"));
        assertEquals(200, c.get("/api/passengers/profile").status());

        String csv = Files.readString(dir.resolve("passengers.csv"));
        assertTrue(csv.contains("sadia_islam"));
        assertFalse(csv.contains("secret99"));

        AppHarness.Response dup = app.client().post("/api/auth/passenger/register",
                "{\"username\":\"sadia_islam\",\"password\":\"secret99\",\"fullName\":\"Other\",\"phone\":\"01911222334\"}");
        assertEquals(409, dup.status());
    }

    @Test
    void driverRegistrationCreatesAnOfflineRickshaw() {
        AppHarness.Client c = app.client();
        AppHarness.Response ok = c.post("/api/auth/driver/register",
                "{\"username\":\"rubel\",\"password\":\"drive123\",\"driverName\":\"Rubel Mia\",\"phone\":\"01811999888\","
                        + "\"startLocationId\":\"bottola\",\"plateNumber\":\"dhaka-sa-12-9999\",\"vehicleType\":\"PEDAL\"}");
        assertEquals(201, ok.status());
        assertEquals("DRIVER", ok.json().get("role").asText());
        AppHarness.Response dash = c.get("/api/drivers/dashboard");
        assertEquals(200, dash.status());
        assertFalse(dash.json().get("rickshaw").get("online").asBoolean());
        assertEquals("DHAKA-SA-12-9999", dash.json().get("rickshaw").get("plateNumber").asText());
        assertEquals("bottola", dash.json().get("rickshaw").get("location").get("id").asText());
    }

    @Test
    void usernamesAreUniqueAcrossPassengerAndDriverAccounts() {
        // Existing passenger name cannot be reused for a driver, and vice versa.
        assertEquals(409, app.client().post("/api/auth/driver/register",
                "{\"username\":\"tanvir\",\"password\":\"drive123\",\"driverName\":\"Tanvir Driver\",\"phone\":\"01811999001\","
                        + "\"startLocationId\":\"bottola\",\"plateNumber\":\"DHAKA-1\"}").status());
        assertEquals(409, app.client().post("/api/auth/passenger/register",
                "{\"username\":\"KARIM\",\"password\":\"secret99\",\"fullName\":\"Karim Rider\",\"phone\":\"01911222001\"}").status());
    }

    @Test
    void newPassengerAndDriverCanLogInAgainAndOnlyUseTheirOwnRoleFeatures() throws Exception {
        assertEquals(201, app.client().post("/api/auth/passenger/register",
                "{\"username\":\"mitu\",\"password\":\"secret99\",\"fullName\":\"Mitu Akter\",\"phone\":\"01911222002\"}").status());
        assertEquals(201, app.client().post("/api/auth/driver/register",
                "{\"username\":\"babul\",\"password\":\"drive123\",\"driverName\":\"Babul Mia\",\"phone\":\"01811999002\","
                        + "\"startLocationId\":\"central_library\",\"plateNumber\":\"DHAKA-SA-12-0002\"}").status());

        // Stored in the role's own CSV file with a BCrypt hash.
        assertTrue(Files.readString(dir.resolve("passengers.csv")).contains(",mitu,$2a$10$"));
        assertTrue(Files.readString(dir.resolve("drivers.csv")).contains(",babul,$2a$10$"));
        assertTrue(Files.readString(dir.resolve("rickshaws.csv")).contains("Babul Mia,central_library"));

        // Logging in with the wrong account type fails; the right one gives the right role.
        assertEquals(401, app.client().post("/api/auth/login",
                "{\"role\":\"driver\",\"username\":\"mitu\",\"password\":\"secret99\"}").status());
        AppHarness.Client mitu = app.login("passenger", "mitu", "secret99");
        AppHarness.Client babul = app.login("driver", "babul", "drive123");
        assertEquals("PASSENGER", mitu.get("/api/auth/me").json().get("role").asText());
        assertEquals("DRIVER", babul.get("/api/auth/me").json().get("role").asText());
        assertEquals(403, mitu.get("/api/drivers/dashboard").status());
        assertEquals(403, babul.post("/api/rides/request",
                "{\"pickupId\":\"central_library\",\"destinationId\":\"cse_building\",\"seats\":1}").status());

        // The new driver goes online and is the only free rickshaw at Central Library:
        // the new passenger's request is offered to them, they accept, start and manage it.
        assertEquals(200, babul.put("/api/drivers/availability", "{\"online\":true}").status());
        AppHarness.Response ride = mitu.post("/api/rides/request", "{\"pickupId\":\"central_library\","
                + "\"destinationId\":\"cse_building\",\"seats\":1,\"preferredRickshawId\":\""
                + babul.get("/api/auth/me").json().get("rickshawId").asText() + "\"}");
        assertEquals(201, ride.status());
        int id = ride.json().get("id").asInt();
        assertEquals(1, babul.get("/api/drivers/requests").json().size());
        assertEquals("ACCEPTED", babul.post("/api/rides/" + id + "/accept").json().get("status").asText());
        assertEquals(1, babul.get("/api/drivers/dashboard").json().get("activeRides").size());
        assertEquals("STARTED", babul.post("/api/rides/" + id + "/start").json().get("status").asText());
        assertEquals("STARTED", mitu.get("/api/passengers/rides/current").json().get("status").asText());
    }

    @Test
    void rolesAreEnforced() {
        AppHarness.Client anon = app.client();
        assertEquals(401, anon.get("/api/passengers/profile").status());
        assertEquals(401, anon.get("/api/drivers/dashboard").status());
        assertEquals(200, anon.get("/api/locations").status());
        assertEquals(200, anon.get("/api/map").status());

        AppHarness.Client passenger = app.login("passenger", "nila", "1234");
        assertEquals(403, passenger.get("/api/drivers/dashboard").status());
        assertEquals(403, passenger.post("/api/rides/1/accept").status());
        assertEquals(403, passenger.put("/api/drivers/availability", "{\"online\":false}").status());

        AppHarness.Client driver = app.login("driver", "salam", "1234");
        assertEquals(403, driver.get("/api/passengers/profile").status());
        assertEquals(403, driver.post("/api/rides/request",
                "{\"pickupId\":\"bottola\",\"destinationId\":\"dairy_gate\",\"seats\":1}").status());
        assertEquals(403, driver.get("/api/rides/available?pickup=bottola&destination=dairy_gate").status());
    }

    @Test
    void logoutEndsTheSessionAndLoginRotatesTheSessionId() {
        AppHarness.Client c = app.client();
        c.get("/api/auth/csrf");
        c.get("/api/auth/me");
        String before = c.cookie("CR_SESSION");
        c.post("/api/auth/login", "{\"role\":\"passenger\",\"username\":\"rafiq\",\"password\":\"1234\"}");
        String after = c.cookie("CR_SESSION");
        assertNotNull(after);
        assertNotEquals(before, after);
        assertEquals("rafiq", c.get("/api/auth/me").json().get("username").asText());
        assertEquals(204, c.post("/api/auth/logout").status());
        assertEquals(204, c.get("/api/auth/me").status());
        assertEquals(401, c.get("/api/passengers/profile").status());
    }

    @Test
    void profileUpdateRequiresCurrentPasswordToChangeIt() {
        AppHarness.Client c = app.login("passenger", "emergency", "1234");
        assertEquals(400, c.put("/api/passengers/profile",
                "{\"fullName\":\"JU Medical Emergency\",\"phone\":\"01711000999\",\"currentPassword\":\"bad\",\"newPassword\":\"newpass1\"}").status());
        AppHarness.Response ok = c.put("/api/passengers/profile",
                "{\"fullName\":\"JU Medical Emergency Unit\",\"phone\":\"01711000998\"}");
        assertEquals(200, ok.status());
        assertEquals("JU Medical Emergency Unit", ok.json().get("fullName").asText());
    }
}
