package storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.RideService;
import service.TestApp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvStorageTest {

    @TempDir
    Path dir;

    @Test
    void missingFilesAreCreatedFromSampleData() throws Exception {
        RideService service = TestApp.start(dir);
        for (String file : List.of("locations.csv", "roads.csv", "rickshaws.csv", "rides.csv",
                "passengers.csv", "drivers.csv", "ride_passengers.csv")) {
            assertTrue(Files.exists(dir.resolve(file)), file);
        }
        assertEquals(25, service.getRouteService().getGraph().getLocationCount());
        assertEquals(33, service.getRouteService().getGraph().getRoadCount());
        assertEquals(5, service.getRickshaws().size());
        assertEquals(4, service.getPassengers().size());
        assertEquals(5, service.getDrivers().size());
        assertTrue(service.getRides().isEmpty());
    }

    @Test
    void malformedRowsAreSkippedAndOriginalIsBackedUp() throws Exception {
        TestApp.start(dir); // create the normal files first
        Files.writeString(dir.resolve("rides.csv"), String.join("\n",
                "id,passengerName,passengerType,pickupId,destinationId,distanceMeters,status,rickshawId",
                "1,Rafiq,STUDENT,bottola,dairy_gate,500.0,PENDING,",
                "not,a,valid,row",
                "2,Nila,ALIEN,bottola,dairy_gate,500.0,PENDING,",
                "3,Tania,STUDENT,bottola,dairy_gate,abc,PENDING,",
                "4,Mim,STUDENT,nowhere,dairy_gate,100.0,PENDING,",
                "5,Ali,STAFF,bottola,dairy_gate,100.0,MATCHED,NO-SUCH-RICKSHAW",
                "",
                "6,Rana,STAFF,bottola,dairy_gate,100.0,PENDING,"));
        String original = Files.readString(dir.resolve("rides.csv"));

        RideService service = TestApp.start(dir);

        assertEquals(2, service.getRides().size()); // rows 1 and 6
        assertEquals(2, service.getPendingInOrder().size());
        assertEquals(original, Files.readString(dir.resolve("rides.csv.bak")));
    }

    @Test
    void emptyLocationsFileFallsBackToSampleWithoutOverwritingIt() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("locations.csv"), "");
        RideService service = TestApp.start(dir);
        assertEquals(25, service.getRouteService().getGraph().getLocationCount());
        assertEquals("", Files.readString(dir.resolve("locations.csv")));
    }

    @Test
    void invalidRoadsAreSkipped() throws Exception {
        TestApp.start(dir);
        Files.writeString(dir.resolve("roads.csv"), String.join("\n",
                "from,to,distanceMeters,roadName",
                "dairy_gate,shaheed_minar,116.6,Shaheed Minar Road",
                "dairy_gate,nowhere,50,Bad",
                "dairy_gate,bottola,-5,Negative",
                "dairy_gate,bottola,abc,Text"));
        RideService service = TestApp.start(dir);
        assertEquals(1, service.getRouteService().getGraph().getRoadCount());
    }
}
