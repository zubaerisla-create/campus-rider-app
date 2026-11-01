package ui;

import dsa.QueuePolicy;
import model.Location;
import model.PassengerType;
import model.RideStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.RideService;
import service.TestApp;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clicks the real buttons (without showing a window) and checks the services react. */
class PanelTest {

    @TempDir
    Path dir;

    private static void select(JComboBox<Location> box, String id) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).getId().equals(id)) {
                box.setSelectedIndex(i);
                return;
            }
        }
        throw new IllegalArgumentException(id);
    }

    @Test
    void routePanelSearchAndFindRoute() throws Exception {
        RideService service = TestApp.start(dir);
        SwingUtilities.invokeAndWait(() -> {
            RoutePanel panel = new RoutePanel(service.getRouteService());

            panel.searchField.setText("gate"); // the document listener runs the trie search
            assertEquals(3, panel.matchesModel.size());
            panel.useAsStartButton.doClick();
            assertEquals("bishmile_gate", ((Location) panel.fromBox.getSelectedItem()).getId());

            panel.searchField.setText("tsc");
            assertEquals(1, panel.matchesModel.size());
            panel.useAsDestinationButton.doClick();
            assertEquals("central_cafeteria", ((Location) panel.toBox.getSelectedItem()).getId());

            panel.findRouteButton.doClick();
            assertFalse(panel.message.isError());
            String expected = service.getRouteService().describeRoute(
                    service.getRouteService().findRoute("bishmile_gate", "central_cafeteria"));
            assertEquals(expected, panel.resultArea.getText());
            assertTrue(panel.resultArea.getText().contains("Total distance"));

            panel.searchField.setText("zzz");
            assertEquals(0, panel.matchesModel.size());
            panel.useAsStartButton.doClick();
            assertTrue(panel.message.isError());
        });
    }

    @Test
    void ridePanelButtonsDriveTheRideLifecycle() throws Exception {
        RideService service = TestApp.start(dir);
        SwingUtilities.invokeAndWait(() -> {
            RidePanel panel = new RidePanel(service);

            // Empty name shows an error and creates nothing.
            panel.createButton.doClick();
            assertTrue(panel.message.isError());
            assertTrue(service.getRides().isEmpty());

            panel.nameField.setText("Rafiq");
            panel.typeBox.setSelectedItem(PassengerType.STAFF);
            select(panel.pickupBox, "bottola");
            select(panel.destinationBox, "dairy_gate");
            panel.createButton.doClick();
            assertFalse(panel.message.isError());
            assertEquals(1, service.getRides().size());
            assertEquals(1, panel.ridesModel.getRowCount());

            panel.matchButton.doClick();
            assertEquals(RideStatus.MATCHED, service.getRide(1).getStatus());
            assertEquals("BUSY", panel.rickshawsModel.getValueAt(1, 3)); // JU-RICK-102

            panel.ridesTable.setRowSelectionInterval(0, 0);
            panel.completeButton.doClick(); // MATCHED -> COMPLETED is not allowed
            assertTrue(panel.message.isError());
            panel.startButton.doClick();
            assertEquals(RideStatus.IN_PROGRESS, service.getRide(1).getStatus());
            panel.completeButton.doClick();
            assertEquals(RideStatus.COMPLETED, service.getRide(1).getStatus());
            assertEquals("COMPLETED", panel.ridesModel.getValueAt(0, 6).toString());

            panel.policyBox.setSelectedItem(QueuePolicy.PRIORITY);
            assertEquals(QueuePolicy.PRIORITY, service.getQueuePolicy());

            panel.ridesTable.clearSelection();
            panel.cancelButton.doClick();
            assertTrue(panel.message.isError()); // no ride selected
        });
    }

    @Test
    void passengerAndDriverPanelsSyncInRealTime() throws Exception {
        RideService service = TestApp.start(dir);
        SwingUtilities.invokeAndWait(() -> {
            PassengerPanel passengerPanel = new PassengerPanel(service);
            DriverPanel driverPanel = new DriverPanel(service);

            // Passenger requests a 2-seat ride from dairy_gate to shaheed_minar
            passengerPanel.passengerNameField.setText("Tanvir");
            passengerPanel.passengerTypeBox.setSelectedItem(PassengerType.EMERGENCY);
            passengerPanel.seatsBox.setSelectedItem(2);
            select(passengerPanel.pickupBox, "dairy_gate");
            select(passengerPanel.destinationBox, "shaheed_minar");
            passengerPanel.requestRideButton.doClick();

            assertFalse(passengerPanel.message.isError());
            assertEquals(1, driverPanel.pendingTableModel.getRowCount());

            // Driver JU-RICK-101 (at dairy_gate) accepts the ride from DriverPanel
            driverPanel.driverSelector.setSelectedItem("JU-RICK-101");
            driverPanel.acceptRideButton.doClick();
            assertFalse(driverPanel.message.isError());
            assertEquals(RideStatus.MATCHED, service.getRide(1).getStatus());
            assertEquals(1, service.getRickshaw("JU-RICK-101").getAvailableSeats()); // 3 - 2 = 1 seat left
            assertTrue(passengerPanel.liveTrackingBanner.getText().contains("DRIVER ACCEPTED"));

            // Driver steps GPS twice: 1) Pickup at dairy_gate -> IN_PROGRESS, 2) Arrive at shaheed_minar -> COMPLETED
            driverPanel.stepGpsButton.doClick();
            assertEquals(RideStatus.IN_PROGRESS, service.getRide(1).getStatus());
            assertTrue(passengerPanel.liveTrackingBanner.getText().contains("RIDE IN PROGRESS"));

            driverPanel.stepGpsButton.doClick();
            assertEquals(RideStatus.COMPLETED, service.getRide(1).getStatus());
            assertTrue(passengerPanel.liveTrackingBanner.getText().contains("COMPLETED"));
            assertEquals(3, service.getRickshaw("JU-RICK-101").getAvailableSeats());
        });
    }
}
