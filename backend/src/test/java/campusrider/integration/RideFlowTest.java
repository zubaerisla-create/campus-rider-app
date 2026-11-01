package campusrider.integration;

import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import campusrider.model.Rickshaw;
import campusrider.repository.RickshawRepository;
import campusrider.repository.RideRepository;
import campusrider.service.RideService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end ride workflows over real HTTP. Demo rickshaws start at:
 * 101 karim @ dairy_gate, 102 mokbul @ bottola, 103 salam @ shaheed_minar,
 * 104 rahim @ prantik_gate, 105 jamal @ mmh_hall.
 */
class RideFlowTest {
    static final Map<String, String> DRIVER_OF = Map.of("JU-RICK-101", "karim", "JU-RICK-102", "mokbul",
            "JU-RICK-103", "salam", "JU-RICK-104", "rahim", "JU-RICK-105", "jamal");

    @TempDir
    Path dir;
    AppHarness app;

    @AfterEach
    void stop() {
        if (app != null) {
            app.close();
        }
    }

    static String trip(String from, String to, int seats) {
        return "{\"pickupId\":\"" + from + "\",\"destinationId\":\"" + to + "\",\"seats\":" + seats + "}";
    }

    RideRequest ride(int id) {
        return app.bean(RideRepository.class).findById(id).orElseThrow();
    }

    Rickshaw rickshaw(String id) {
        return app.bean(RickshawRepository.class).findById(id).orElseThrow();
    }

    void onlyOnline(String... ids) {
        List<String> keep = List.of(ids);
        for (Map.Entry<String, String> e : DRIVER_OF.entrySet()) {
            if (!keep.contains(e.getKey())) {
                assertEquals(200, app.login("driver", e.getValue(), "1234")
                        .put("/api/drivers/availability", "{\"online\":false}").status());
            }
        }
    }

    @Test
    void fullLifecycleRequestAcceptStartDriveCompleteIsPersisted() throws Exception {
        app = AppHarness.start(dir);
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");

        JsonNode options = tanvir.get("/api/rides/available?pickup=bottola&destination=dairy_gate&seats=1").json();
        assertEquals("JU-RICK-102", options.get(0).get("rickshawId").asText()); // already at Bottola

        AppHarness.Response created = tanvir.post("/api/rides/request", trip("bottola", "dairy_gate", 1));
        assertEquals(201, created.status());
        int id = created.json().get("id").asInt();
        assertEquals("PENDING", created.json().get("status").asText());
        assertEquals("JU-RICK-102", ride(id).getOfferedRickshawId());
        assertEquals(409, tanvir.post("/api/rides/request", trip("bottola", "cse_building", 1)).status());

        AppHarness.Client karim = app.login("driver", "karim", "1234");
        assertEquals(409, karim.post("/api/rides/" + id + "/accept").status()); // not offered to karim
        assertEquals(403, karim.get("/api/rides/" + id).status());

        AppHarness.Client mokbul = app.login("driver", "mokbul", "1234");
        assertEquals(1, mokbul.get("/api/drivers/requests").json().size());
        assertEquals("ACCEPTED", mokbul.post("/api/rides/" + id + "/accept").json().get("status").asText());
        assertEquals(2, rickshaw("JU-RICK-102").getAvailableSeats());
        JsonNode view = tanvir.get("/api/passengers/rides/current").json();
        assertEquals("Mokbul Hossain", view.get("driver").get("name").asText());
        assertTrue(view.get("arrivedAtPickup").asBoolean());

        assertEquals(409, mokbul.post("/api/rides/" + id + "/complete").status()); // ACCEPTED -> COMPLETED invalid
        assertEquals("STARTED", mokbul.post("/api/rides/" + id + "/start").json().get("status").asText());
        assertEquals(409, tanvir.post("/api/rides/" + id + "/cancel").status()); // cannot cancel a started trip

        // Drive along the Dijkstra path until the drop-off blocks further moves.
        int moves = 0;
        while (mokbul.post("/api/drivers/move").status() == 200) {
            moves++;
            assertTrue(moves < 20);
        }
        assertEquals("dairy_gate", rickshaw("JU-RICK-102").getLocationId());
        assertTrue(tanvir.get("/api/passengers/rides/current").json().get("atDestination").asBoolean());

        JsonNode done = mokbul.post("/api/rides/" + id + "/complete").json();
        assertEquals("COMPLETED", done.get("status").asText());
        assertEquals(3, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals(204, tanvir.get("/api/passengers/rides/current").status());
        assertEquals("COMPLETED", tanvir.get("/api/passengers/rides").json().get(0).get("status").asText());
        assertEquals(1, mokbul.get("/api/drivers/rides").json().size());

        String rides = Files.readString(dir.resolve("rides.csv"));
        assertTrue(rides.contains(",COMPLETED,JU-RICK-102,"));
        assertTrue(Files.readString(dir.resolve("ride_passengers.csv")).contains("P-101,Tanvir Ahmed,JU-RICK-102"));
        assertTrue(Files.readString(dir.resolve("rickshaws.csv")).contains("JU-RICK-102,Mokbul Hossain,dairy_gate"));
    }

    @Test
    void rejectionForwardsToTheNextBestRickshawUntilNoneRemain() {
        app = AppHarness.start(dir);
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        int id = tanvir.post("/api/rides/request", trip("bottola", "dairy_gate", 1)).json().get("id").asInt();

        List<String> offeredTo = new ArrayList<>();
        while (ride(id).getStatus() == RideStatus.PENDING) {
            String rickshawId = ride(id).getOfferedRickshawId();
            assertTrue(!rickshawId.isEmpty() && !offeredTo.contains(rickshawId), "re-offered " + rickshawId);
            offeredTo.add(rickshawId);
            assertEquals(200, app.login("driver", DRIVER_OF.get(rickshawId), "1234")
                    .post("/api/rides/" + id + "/reject").status());
        }
        assertEquals("JU-RICK-102", offeredTo.get(0));
        assertEquals(5, offeredTo.size());
        assertEquals(RideStatus.REJECTED, ride(id).getStatus());
        assertEquals("REJECTED", tanvir.get("/api/rides/" + id).json().get("status").asText());
        assertEquals(409, tanvir.post("/api/rides/" + id + "/cancel").status()); // final state
    }

    @Test
    void threePassengersShareOneRickshawAndAFourthMustWait() {
        app = AppHarness.start(dir);
        onlyOnline("JU-RICK-102");
        AppHarness.Client mokbul = app.login("driver", "mokbul", "1234");

        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        int r1 = tanvir.post("/api/rides/request", trip("bottola", "dairy_gate", 2)).json().get("id").asInt();
        mokbul.post("/api/rides/" + r1 + "/accept");

        AppHarness.Client nila = app.login("passenger", "nila", "1234");
        JsonNode option = nila.get("/api/rides/available?pickup=rafiq_jabbar_hall&destination=dairy_gate&seats=1")
                .json().get(0);
        assertTrue(option.get("shared").asBoolean());
        assertTrue(option.get("compatible").asBoolean());
        int r2 = nila.post("/api/rides/request", trip("rafiq_jabbar_hall", "dairy_gate", 1)).json().get("id").asInt();
        assertEquals("JU-RICK-102", ride(r2).getOfferedRickshawId());
        JsonNode accepted = mokbul.post("/api/rides/" + r2 + "/accept").json();
        assertTrue(accepted.get("shared").asBoolean());
        assertEquals(0, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals(3, rickshaw("JU-RICK-102").getBookedSeats());
        assertTrue(ride(r2).getDetourMeters() > 0); // picking Nila up adds distance to Tanvir's trip

        // Both passengers see each other (first names only) and the same rickshaw position.
        JsonNode tv = tanvir.get("/api/passengers/rides/current").json();
        JsonNode nv = nila.get("/api/passengers/rides/current").json();
        assertEquals("Nila", tv.get("coPassengers").get(0).get("firstName").asText());
        assertEquals(tv.get("rickshaw").get("x").asDouble(), nv.get("rickshaw").get("x").asDouble());

        // The rickshaw is full: Rafiq's request is not offered to it and waits in the queue.
        AppHarness.Client rafiq = app.login("passenger", "rafiq", "1234");
        int r3 = rafiq.post("/api/rides/request", trip("bottola", "central_library", 1)).json().get("id").asInt();
        assertEquals(RideStatus.PENDING, ride(r3).getStatus());
        assertEquals("", ride(r3).getOfferedRickshawId());
        assertTrue(app.bean(RideService.class).queuedRideIds().contains(r3));

        // Driver cannot offer more seats than are free.
        assertEquals(400, mokbul.put("/api/drivers/rickshaw",
                "{\"currentLocationId\":\"bottola\",\"targetDestinationId\":\"\",\"availableSeats\":1}").status());
        // Cannot go offline with passengers.
        assertEquals(409, mokbul.put("/api/drivers/availability", "{\"online\":false}").status());

        // Tanvir cancels: two seats free up and the queued request is dispatched to the rickshaw.
        assertEquals("CANCELLED", tanvir.post("/api/rides/" + r1 + "/cancel").json().get("status").asText());
        assertEquals(2, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals("JU-RICK-102", ride(r3).getOfferedRickshawId());
    }

    @Test
    void concurrentBookingsAndAcceptsNeverOverbookARickshaw() throws Exception {
        app = AppHarness.start(dir);
        onlyOnline("JU-RICK-102");
        int n = 8;
        List<AppHarness.Client> riders = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            AppHarness.Client c = app.client();
            assertEquals(201, c.post("/api/auth/passenger/register", "{\"username\":\"rider" + i
                    + "\",\"password\":\"secret1\",\"fullName\":\"Rider " + (char) ('A' + i)
                    + "\",\"phone\":\"0171100" + String.format("%04d", i) + "\"}").status());
            riders.add(c);
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (AppHarness.Client c : riders) {
            results.add(pool.submit(() -> {
                go.await();
                return c.post("/api/rides/request", trip("bottola", "dairy_gate", 1)).json().get("id").asInt();
            }));
        }
        go.countDown();
        List<Integer> ids = new ArrayList<>();
        for (Future<Integer> f : results) {
            ids.add(f.get(30, TimeUnit.SECONDS));
        }
        long offered = ids.stream().filter(id -> ride(id).getOfferedRickshawId().equals("JU-RICK-102")).count();
        assertEquals(3, offered); // seats are reserved for outstanding offers: never more than 3

        AppHarness.Client mokbul = app.login("driver", "mokbul", "1234");
        CountDownLatch acceptGo = new CountDownLatch(1);
        List<Future<Integer>> accepts = new ArrayList<>();
        for (int id : ids) {
            accepts.add(pool.submit(() -> {
                acceptGo.await();
                return mokbul.post("/api/rides/" + id + "/accept").status();
            }));
        }
        acceptGo.countDown();
        int ok = 0;
        for (Future<Integer> f : accepts) {
            if (f.get(30, TimeUnit.SECONDS) == 200) {
                ok++;
            }
        }
        pool.shutdown();
        assertEquals(3, ok);
        assertEquals(3, rickshaw("JU-RICK-102").getBookedSeats());
        assertEquals(0, rickshaw("JU-RICK-102").getAvailableSeats());
        long active = app.bean(RideRepository.class).findActiveByRickshaw("JU-RICK-102").size();
        assertEquals(3, active);
    }

    @Test
    void emergencyRequestIsDispatchedBeforeEarlierNormalRequests() {
        app = AppHarness.start(dir);
        onlyOnline("JU-RICK-101");
        AppHarness.Client karim = app.login("driver", "karim", "1234");
        assertEquals(200, karim.put("/api/drivers/availability", "{\"online\":false}").status());

        int normal = app.login("passenger", "tanvir", "1234")
                .post("/api/rides/request", trip("dairy_gate", "shaheed_minar", 1)).json().get("id").asInt();
        int normal2 = app.login("passenger", "nila", "1234")
                .post("/api/rides/request", trip("dairy_gate", "central_library", 1)).json().get("id").asInt();
        int emergency = app.login("passenger", "emergency", "1234")
                .post("/api/rides/request", trip("dairy_gate", "medical_centre", 1)).json().get("id").asInt();
        assertEquals(List.of(emergency, normal, normal2), app.bean(RideService.class).queuedRideIds());

        // Karim offers a single seat, then comes online: that seat must go to the emergency request.
        assertEquals(200, karim.put("/api/drivers/rickshaw",
                "{\"currentLocationId\":\"dairy_gate\",\"targetDestinationId\":\"\",\"availableSeats\":1}").status());
        assertEquals(200, karim.put("/api/drivers/availability", "{\"online\":true}").status());
        assertEquals("JU-RICK-101", ride(emergency).getOfferedRickshawId());
        assertTrue(ride(emergency).isEmergency());
        assertEquals("", ride(normal).getOfferedRickshawId());
        assertEquals(List.of(normal, normal2), app.bean(RideService.class).queuedRideIds());
    }

    @Test
    void passengersCanOnlyCancelTheirOwnEligibleRides() {
        app = AppHarness.start(dir);
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        int id = tanvir.post("/api/rides/request", trip("bottola", "dairy_gate", 1)).json().get("id").asInt();
        AppHarness.Client nila = app.login("passenger", "nila", "1234");
        assertEquals(403, nila.post("/api/rides/" + id + "/cancel").status());
        assertEquals(403, nila.get("/api/rides/" + id).status());
        assertEquals(404, tanvir.get("/api/rides/999").status());

        app.login("driver", "mokbul", "1234").post("/api/rides/" + id + "/accept");
        assertEquals(2, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals("CANCELLED", tanvir.post("/api/rides/" + id + "/cancel").json().get("status").asText());
        assertEquals(3, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals(409, tanvir.post("/api/rides/" + id + "/cancel").status());
    }

    @Test
    void unansweredOfferTimesOutAndMovesToAnotherDriver() throws Exception {
        app = AppHarness.start(dir, "campusrider.offer-timeout-seconds=1");
        int id = app.login("passenger", "tanvir", "1234")
                .post("/api/rides/request", trip("bottola", "dairy_gate", 1)).json().get("id").asInt();
        String first = ride(id).getOfferedRickshawId();
        long deadline = System.currentTimeMillis() + 10_000;
        while (ride(id).getOfferedRickshawId().equals(first) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertNotEquals(first, ride(id).getOfferedRickshawId());
        assertTrue(ride(id).isRejectedBy(first));
    }

    @Test
    void ridesSeatsAndAccountsSurviveARestart() {
        app = AppHarness.start(dir);
        AppHarness.Client tanvir = app.login("passenger", "tanvir", "1234");
        int id = tanvir.post("/api/rides/request", trip("bottola", "dairy_gate", 2)).json().get("id").asInt();
        app.login("driver", "mokbul", "1234").post("/api/rides/" + id + "/accept");
        AppHarness.Client c = app.client();
        c.post("/api/auth/passenger/register",
                "{\"username\":\"newbie\",\"password\":\"secret1\",\"fullName\":\"New Bie\",\"phone\":\"01911000000\"}");
        app.close();

        app = AppHarness.start(dir);
        assertEquals(RideStatus.ACCEPTED, ride(id).getStatus());
        assertEquals(1, rickshaw("JU-RICK-102").getAvailableSeats());
        assertEquals("ACCEPTED", app.login("passenger", "tanvir", "1234")
                .get("/api/passengers/rides/current").json().get("status").asText());
        app.login("passenger", "newbie", "secret1");
    }

    @Test
    void autoDriveMovesAlongRoadsAndStopsAtThePickup() throws Exception {
        app = AppHarness.start(dir, "campusrider.simulation-speed-mps=3000", "campusrider.simulation-tick-ms=50");
        // Salam (shaheed_minar) is the nearest free rickshaw to Central Library.
        AppHarness.Client nila = app.login("passenger", "nila", "1234");
        int id = nila.post("/api/rides/request", trip("central_library", "cse_building", 1)).json().get("id").asInt();
        String rickshawId = ride(id).getOfferedRickshawId();
        AppHarness.Client driver = app.login("driver", DRIVER_OF.get(rickshawId), "1234");
        driver.post("/api/rides/" + id + "/accept");
        assertEquals(200, driver.put("/api/drivers/auto-drive", "{\"enabled\":true}").status());

        long deadline = System.currentTimeMillis() + 15_000;
        while (!nila.get("/api/passengers/rides/current").json().get("arrivedAtPickup").asBoolean()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals("central_library", rickshaw(rickshawId).getLocationId());
        Thread.sleep(300);
        assertEquals("central_library", rickshaw(rickshawId).getLocationId()); // waits for the driver
        driver.post("/api/rides/" + id + "/start");
        deadline = System.currentTimeMillis() + 15_000;
        while (!nila.get("/api/passengers/rides/current").json().get("atDestination").asBoolean()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals("cse_building", rickshaw(rickshawId).getLocationId());
        assertEquals("COMPLETED", driver.post("/api/rides/" + id + "/complete").json().get("status").asText());
    }
}
