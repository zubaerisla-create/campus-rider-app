package ui;

import service.RideService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * The main application window with Passenger App, Driver App, Route Finder,
 * and System Overview tabs, plus a toolbar to open side-by-side Passenger & Driver windows.
 */
public class MainWindow extends JFrame {
    private final RideService rideService;
    private PassengerWindow passengerWindow;
    private DriverWindow driverWindow;

    public MainWindow(RideService rideService) {
        super("Smart Campus Ride-Sharing & Live Route Optimizer (Passenger • Driver • Live Map)");
        this.rideService = rideService;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildTopToolbar(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Passenger App (Live Map)", new PassengerPanel(rideService));
        tabs.addTab("Driver App (Accept/Reject & GPS)", new DriverPanel(rideService));
        tabs.addTab("Route Finder", new RoutePanel(rideService.getRouteService()));
        tabs.addTab("Rides and Rickshaws", new RidePanel(rideService));
        add(tabs, BorderLayout.CENTER);

        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private JPanel buildTopToolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        bar.setBackground(new Color(44, 62, 80));

        JLabel title = new JLabel("JU Smart Campus Ride-Sharing System — Passenger + Driver + Live GPS + Shared Seats (Up to 3)");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton openSideBySideBtn = new JButton("Open Passenger & Driver Side-by-Side");
        JButton openDriverBtn = new JButton("Open Driver Window");
        openSideBySideBtn.addActionListener(e -> openSideBySideWindows());
        openDriverBtn.addActionListener(e -> openDriverWindowNextToMain());

        buttons.add(openDriverBtn);
        buttons.add(openSideBySideBtn);

        bar.add(title, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    /** Positions this MainWindow on the left and opens DriverWindow on the right side-by-side. */
    public void openDriverWindowNextToMain() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int halfW = Math.max(640, bounds.width / 2);
        int h = Math.max(700, bounds.height - 20);

        setBounds(bounds.x, bounds.y, halfW, h);
        if (driverWindow == null || !driverWindow.isDisplayable()) {
            driverWindow = new DriverWindow(rideService);
        }
        driverWindow.setBounds(bounds.x + halfW, bounds.y, bounds.width - halfW, h);
        driverWindow.setVisible(true);
        driverWindow.toFront();
    }

    /** Opens standalone PassengerWindow and DriverWindow side-by-side. */
    public void openSideBySideWindows() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int halfW = Math.max(640, bounds.width / 2);
        int h = Math.max(700, bounds.height - 20);

        if (passengerWindow == null || !passengerWindow.isDisplayable()) {
            passengerWindow = new PassengerWindow(rideService);
        }
        if (driverWindow == null || !driverWindow.isDisplayable()) {
            driverWindow = new DriverWindow(rideService);
        }
        passengerWindow.setBounds(bounds.x, bounds.y, halfW, h);
        driverWindow.setBounds(bounds.x + halfW, bounds.y, bounds.width - halfW, h);
        passengerWindow.setVisible(true);
        driverWindow.setVisible(true);
        passengerWindow.toFront();
        driverWindow.toFront();
    }
}
