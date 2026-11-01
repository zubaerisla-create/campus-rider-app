package ui;

import service.RideService;

import javax.swing.JFrame;

/** Standalone Driver Application Window for side-by-side desktop usage. */
public class DriverWindow extends JFrame {
    private final DriverPanel driverPanel;

    public DriverWindow(RideService rideService) {
        super("Campus Ride — Driver Application (Incoming Requests • Accept/Reject • Live GPS)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.driverPanel = new DriverPanel(rideService);
        add(driverPanel);
        setSize(860, 740);
    }

    public DriverPanel getDriverPanel() {
        return driverPanel;
    }
}
