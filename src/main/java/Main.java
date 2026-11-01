import service.RideService;
import service.RouteService;
import storage.CsvStorage;
import ui.MainWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point. Loads the CSV files, builds the services and opens the window.
 *
 * Data folder: "data" in the current directory, or the folder given with
 *   java -Dcampusride.dataDir=/some/folder -jar ...
 */
public class Main {

    public static void main(String[] args) {
        Path dataDir = Paths.get(System.getProperty("campusride.dataDir", "data")).toAbsolutePath();
        System.out.println("Data folder: " + dataDir);

        RideService rideService;
        CsvStorage storage = new CsvStorage(dataDir);
        try {
            RouteService routeService = new RouteService(storage.loadLocations(), storage.loadRoads(),
                    storage.getWarnings());
            rideService = new RideService(storage, routeService);
            System.out.println("Loaded " + routeService.getGraph().getLocationCount() + " locations, "
                    + routeService.getGraph().getRoadCount() + " roads, "
                    + rideService.getRickshaws().size() + " rickshaws, "
                    + rideService.getRides().size() + " rides.");
        } catch (Exception e) {
            System.err.println("Could not load data: " + e.getMessage());
            showErrorAndExit("Could not load the data files from\n" + dataDir + "\n\n" + e.getMessage());
            return;
        }

        List<String> warnings = storage.getWarnings();
        for (String warning : warnings) {
            System.out.println("Warning: " + warning);
        }

        // Swing components must be created and used on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow(rideService);
            window.setVisible(true);
            window.openDriverWindowNextToMain();
            if (!warnings.isEmpty()) {
                JOptionPane.showMessageDialog(window, String.join("\n", warnings),
                        "Data file notes", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private static void showErrorAndExit(String text) {
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, text, "Startup error", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {
            // no display available; the error was already printed
        }
        System.exit(1);
    }
}
