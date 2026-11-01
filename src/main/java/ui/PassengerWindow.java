package ui;

import service.RideService;

import javax.swing.JFrame;

/** Standalone Passenger Application Window for side-by-side desktop usage. */
public class PassengerWindow extends JFrame {
    private final PassengerPanel passengerPanel;

    public PassengerWindow(RideService rideService) {
        super("Campus Ride — Passenger Application (Book Ride • Seat Sharing • Live Tracking)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.passengerPanel = new PassengerPanel(rideService);
        add(passengerPanel);
        setSize(860, 740);
    }

    public PassengerPanel getPassengerPanel() {
        return passengerPanel;
    }
}
