package campusrider.storage;

import campusrider.model.RideRequest;
import campusrider.model.RideStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvStorageTest {

    @TempDir
    Path dir;

    @Test
    void missingFilesAreCreatedFromTheBundledSampleData() throws Exception {
        CsvStorage storage = new CsvStorage(dir);
        assertEquals(25, storage.loadLocations().size());
        assertEquals(33, storage.loadRoads().size());
        assertEquals(5, storage.loadRickshaws().size());
        assertEquals(4, storage.loadPassengers().size());
        assertEquals(5, storage.loadDrivers().size());
        assertTrue(storage.loadRides().isEmpty());
        for (String f : List.of("locations.csv", "roads.csv", "rickshaws.csv", "rides.csv", "passengers.csv", "drivers.csv")) {
            assertTrue(Files.exists(dir.resolve(f)), f);
        }
        assertTrue(storage.loadLocations().get(0).hasCoordinates());
    }

    @Test
    void legacyDesktopFilesStillLoad() throws Exception {
        Files.createDirectories(dir);
        // Desktop version: 8-column header, optional seats + offered rickshaw columns, MATCHED status.
        Files.writeString(dir.resolve("rides.csv"), String.join("\n",
                "id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId",
                "1,Rafiq,STUDENT,bottola,dairy_gate,500.0,MATCHED,JU-RICK-101",
                "2,Nila,STAFF,bottola,dairy_gate,500.0,PENDING,,2,JU-RICK-102",
                "3,Mim,STUDENT,bottola,dairy_gate,500.0,IN_PROGRESS,JU-RICK-103,1"));
        Files.writeString(dir.resolve("passengers.csv"), "id,username,password,fullName,passengerType,phone\n"
                + "P-101,tanvir,1234,Tanvir Ahmed,STUDENT,01711000001\n");
        Files.writeString(dir.resolve("rickshaws.csv"), "id,driverName,locationId,status\n"
                + "JU-RICK-101,Karim,dairy_gate,BUSY\n");
        CsvStorage storage = new CsvStorage(dir);
        List<RideRequest> rides = storage.loadRides();
        assertEquals(3, rides.size());
        assertEquals(RideStatus.ACCEPTED, rides.get(0).getStatus());
        assertEquals(2, rides.get(1).getSeatsRequested());
        assertEquals("JU-RICK-102", rides.get(1).getOfferedRickshawId());
        assertEquals(RideStatus.STARTED, rides.get(2).getStatus());
        assertEquals("1234", storage.loadPassengers().get(0).getPasswordHash()); // migrated later by AccountService
        assertTrue(storage.loadRickshaws().get(0).isOnline());
    }

    @Test
    void malformedAndDuplicateRowsAreSkippedAndTheOriginalIsBackedUp() throws Exception {
        CsvStorage storage = new CsvStorage(dir);
        storage.loadRides();
        String text = String.join("\n",
                "id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId",
                "1,Rafiq,STUDENT,bottola,dairy_gate,500.0,PENDING,",
                "not,a,valid,row",
                "2,Nila,ALIEN,bottola,dairy_gate,500.0,PENDING,",
                "3,Tania,STUDENT,bottola,dairy_gate,abc,PENDING,",
                "1,Duplicate,STUDENT,bottola,dairy_gate,1.0,PENDING,",
                "",
                "6,Rana,STAFF,bottola,dairy_gate,100.0,PENDING,");
        Files.writeString(dir.resolve("rides.csv"), text);
        List<RideRequest> rides = storage.loadRides();
        assertEquals(List.of(1, 6), rides.stream().map(RideRequest::getId).toList());
        assertEquals(text, Files.readString(dir.resolve("rides.csv.bak")));
        assertFalse(storage.getWarnings().isEmpty());
    }

    @Test
    void valuesWithCommasAndQuotesRoundTripThroughTheFiles() throws Exception {
        CsvStorage storage = new CsvStorage(dir);
        RideRequest ride = new RideRequest(1, "P-1", "Rahman, \"Bappy\"", campusrider.model.PassengerType.STUDENT,
                "bottola", "dairy_gate", 12.5, 1, false, RideStatus.PENDING, 99);
        ride.setStatusNote("note, with comma");
        storage.saveRides(List.of(ride));
        RideRequest back = storage.loadRides().get(0);
        assertEquals("Rahman, \"Bappy\"", back.getPassengerName());
        assertEquals("note, with comma", back.getStatusNote());
        assertFalse(Files.exists(dir.resolve("rides.csv.tmp")));
    }

    @Test
    void aSecondServerCannotLockTheSameDataFolder() throws Exception {
        try (CsvStorage first = new CsvStorage(dir)) {
            first.lockDataDirectory();
            CsvStorage second = new CsvStorage(dir);
            assertThrows(IllegalStateException.class, second::lockDataDirectory);
        }
        try (CsvStorage again = new CsvStorage(dir)) {
            again.lockDataDirectory(); // released on close
        }
    }
}
