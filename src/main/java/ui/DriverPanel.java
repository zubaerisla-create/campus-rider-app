package ui;

import dsa.QueuePolicy;
import dsa.RouteCompatibility;
import dsa.RouteResult;
import model.DriverAccount;
import model.Location;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import service.RideService;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

/**
 * Driver Dashboard:
 * - Driver Account Registration & Login (saved in data/drivers.csv & data/rickshaws.csv)
 * - Set Rickshaw Current Location, Target Destination, and Available Seats (0-3)
 * - View Incoming Ride Requests (Queue / Priority Queue) with Dijkstra distance & Route Detour
 * - Accept or Reject Ride Requests (Reject automatically forwards to next compatible Rickshaw!)
 * - Move Rickshaw (Step-by-step button or Live GPS Auto-Drive Timer) updating Passenger Window live
 */
public class DriverPanel extends JPanel {
    private static final String[] PENDING_COLUMNS = {
            "Ride #", "Passenger", "Priority", "Seats", "Pickup", "Destination", "To Pickup (m)", "Detour (m)", "Match / Compatibility"
    };
    private static final String[] ACTIVE_COLUMNS = {
            "Ride #", "Passenger", "Seats", "Pickup", "Destination", "Status"
    };

    private final RideService rideService;
    private final RouteService routeService;

    // Driver login / registration controls
    final JTextField driverUserField = new JTextField("karim", 7);
    final JPasswordField driverPassField = new JPasswordField("1234", 5);
    final JButton driverLoginButton = new JButton("Driver Login");
    final JButton driverRegisterButton = new JButton("Register Driver");

    // Package-private for testing
    final JComboBox<String> driverSelector = new JComboBox<>();
    final JLabel driverStatusLabel = new JLabel();
    final JLabel seatBadgeLabel = new JLabel();
    final JComboBox<QueuePolicy> policyBox = new JComboBox<>(QueuePolicy.values());

    // Driver Rickshaw settings: Current Location, Target Destination, Available Seats
    final JComboBox<Location> driverLocationBox;
    final JComboBox<String> driverTargetDestBox = new JComboBox<>();
    final JComboBox<Integer> driverAvailableSeatsBox = new JComboBox<>(new Integer[]{0, 1, 2, 3});
    final JButton updateRickshawStateButton = new JButton("Update Rickshaw Status");

    final DefaultTableModel pendingTableModel = new DefaultTableModel(PENDING_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable pendingTable = new JTable(pendingTableModel);
    final JButton acceptRideButton = new JButton("Accept Selected Request");
    final JButton rejectRideButton = new JButton("Reject (Find Next Rickshaw)");
    final JButton autoMatchButton = new JButton("Auto-Match Nearest Rickshaw");

    final DefaultTableModel activeTableModel = new DefaultTableModel(ACTIVE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable activeTable = new JTable(activeTableModel);
    final JButton stepGpsButton = new JButton("Move Rickshaw (Next Stop)");
    final JButton liveGpsToggleButton = new JButton("Start Live GPS Auto-Drive");
    final JButton startRideButton = new JButton("Pickup / Start Ride");
    final JButton completeRideButton = new JButton("Complete Ride");

    final JTextArea routeInstructionsArea = new JTextArea(8, 36);
    final CampusMapCanvas driverMapCanvas;
    final MessageLabel message = new MessageLabel();

    private final Timer liveGpsTimer;
    private boolean liveGpsRunning = false;
    private boolean syncingSelector = false;

    public DriverPanel(RideService rideService) {
        super(new BorderLayout(6, 6));
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        this.driverMapCanvas = new CampusMapCanvas(rideService);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        Location[] locations = routeService.getLocationsSorted().toArray(new Location[0]);
        driverLocationBox = new JComboBox<>(locations);
        driverTargetDestBox.addItem("Any / No Fixed Destination");
        for (Location loc : locations) {
            driverTargetDestBox.addItem(loc.getId() + " - " + loc.getName());
        }

        for (Rickshaw r : rideService.getRickshaws()) {
            driverSelector.addItem(r.getId());
        }
        policyBox.setSelectedItem(rideService.getQueuePolicy());

        liveGpsTimer = new Timer(120, e -> onLiveGpsTick());

        add(buildNorthContainer(), BorderLayout.NORTH);
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftRequestsAndActivePanel(), buildRightNavigationPanel());
        centerSplit.setResizeWeight(0.52);
        add(centerSplit, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        wireEvents();
        rideService.addChangeListener(this::refresh);
        refresh();
    }

    private JPanel buildNorthContainer() {
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(buildDriverHeader(), BorderLayout.NORTH);
        north.add(buildRickshawConfigBar(), BorderLayout.SOUTH);
        return north;
    }

    private JPanel buildDriverHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(39, 174, 96), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        header.setBackground(new Color(234, 250, 241));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(new JLabel("Driver / Rickshaw:"));
        driverSelector.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        left.add(driverSelector);
        driverStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        left.add(driverStatusLabel);
        seatBadgeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        seatBadgeLabel.setForeground(new Color(20, 90, 50));
        left.add(seatBadgeLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(new JLabel("User:"));
        right.add(driverUserField);
        right.add(new JLabel("Pass:"));
        right.add(driverPassField);
        right.add(driverLoginButton);
        right.add(driverRegisterButton);
        right.add(new JLabel("| Queue:"));
        right.add(policyBox);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRickshawConfigBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(22, 160, 133), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        bar.setBackground(new Color(232, 248, 245));

        bar.add(new JLabel("Set Current Location:"));
        bar.add(driverLocationBox);
        bar.add(new JLabel("Target Destination:"));
        bar.add(driverTargetDestBox);
        bar.add(new JLabel("Available Seats:"));
        bar.add(driverAvailableSeatsBox);
        bar.add(updateRickshawStateButton);
        return bar;
    }

    private JPanel buildLeftRequestsAndActivePanel() {
        JPanel left = new JPanel(new BorderLayout(6, 6));

        // 1. Incoming Pending Requests
        JPanel pendingPanel = new JPanel(new BorderLayout(4, 4));
        pendingPanel.setBorder(BorderFactory.createTitledBorder(
                "1. Incoming Ride Requests (Accept / Reject — Reject Automatically Finds Next Rickshaw)"));
        pendingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setPreferredSize(new Dimension(500, 175));

        JPanel pendingButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        acceptRideButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        pendingButtons.add(acceptRideButton);
        pendingButtons.add(rejectRideButton);
        pendingButtons.add(autoMatchButton);

        pendingPanel.add(pendingScroll, BorderLayout.CENTER);
        pendingPanel.add(pendingButtons, BorderLayout.SOUTH);

        // 2. Active Passengers on This Rickshaw (Shared Seats up to 3)
        JPanel activePanel = new JPanel(new BorderLayout(4, 4));
        activePanel.setBorder(BorderFactory.createTitledBorder(
                "2. Active Passengers on This Rickshaw (Seat Sharing up to 3 Passengers & Live Move)"));
        activeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane activeScroll = new JScrollPane(activeTable);
        activeScroll.setPreferredSize(new Dimension(500, 115));

        JPanel activeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        stepGpsButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        liveGpsToggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        activeButtons.add(stepGpsButton);
        activeButtons.add(liveGpsToggleButton);
        activeButtons.add(startRideButton);
        activeButtons.add(completeRideButton);

        activePanel.add(activeScroll, BorderLayout.CENTER);
        activePanel.add(activeButtons, BorderLayout.SOUTH);

        // 3. Route to Passenger & Turn-by-Turn Instructions
        JPanel routeTextPanel = new JPanel(new BorderLayout(4, 4));
        routeTextPanel.setBorder(BorderFactory.createTitledBorder(
                "3. Driver Route to Passenger Pickup & Destination (Dijkstra Shortest Path)"));
        routeInstructionsArea.setEditable(false);
        routeInstructionsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        routeTextPanel.add(new JScrollPane(routeInstructionsArea), BorderLayout.CENTER);

        JSplitPane topMiddle = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pendingPanel, activePanel);
        topMiddle.setResizeWeight(0.55);

        JSplitPane fullLeft = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topMiddle, routeTextPanel);
        fullLeft.setResizeWeight(0.68);

        left.add(fullLeft, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildRightNavigationPanel() {
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("4. Driver Live Navigation Map (Orange = To Pickup, Teal = Trip Route)"));
        right.add(driverMapCanvas, BorderLayout.CENTER);
        return right;
    }

    private void wireEvents() {
        driverSelector.addActionListener(e -> {
            if (syncingSelector) {
                return;
            }
            String rickshawId = selectedRickshawId();
            Rickshaw r = rickshawId == null ? null : rideService.getRickshaw(rickshawId);
            if (r != null) {
                syncRickshawControlsFromModel(r);
                DriverAccount d = rideService.getDriverForRickshaw(rickshawId);
                if (d != null) {
                    driverUserField.setText(d.getUsername());
                    driverPassField.setText(d.getPassword());
                }
            }
            refresh();
        });

        driverLoginButton.addActionListener(e -> {
            try {
                DriverAccount d = rideService.loginDriver(
                        driverUserField.getText(), new String(driverPassField.getPassword()));
                driverSelector.setSelectedItem(d.getRickshawId());
                message.showSuccess("Driver logged in: " + d.getDriverName() + " (" + d.getRickshawId() + ").");
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        driverRegisterButton.addActionListener(e -> showRegisterDriverDialog());

        updateRickshawStateButton.addActionListener(e -> {
            String rickshawId = selectedRickshawId();
            if (rickshawId == null) {
                return;
            }
            Location loc = (Location) driverLocationBox.getSelectedItem();
            String destItem = (String) driverTargetDestBox.getSelectedItem();
            String targetDestId = "";
            if (destItem != null && destItem.contains(" - ")) {
                targetDestId = destItem.substring(0, destItem.indexOf(" - ")).trim();
            }
            Integer freeSeats = (Integer) driverAvailableSeatsBox.getSelectedItem();
            try {
                String msg = rideService.updateDriverRickshawState(
                        rickshawId,
                        loc == null ? null : loc.getId(),
                        targetDestId,
                        freeSeats == null ? 3 : freeSeats);
                message.showSuccess(msg);
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        policyBox.addActionListener(e -> {
            QueuePolicy p = (QueuePolicy) policyBox.getSelectedItem();
            if (p != null && p != rideService.getQueuePolicy()) {
                rideService.setQueuePolicy(p);
                message.showSuccess("Queue policy changed to " + p + ".");
            }
        });

        acceptRideButton.addActionListener(e -> acceptSelectedPendingRide());
        rejectRideButton.addActionListener(e -> rejectSelectedPendingRide());
        autoMatchButton.addActionListener(e -> {
            try {
                String msg = rideService.matchNextRequest();
                message.showSuccess(msg);
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        stepGpsButton.addActionListener(e -> {
            String rickshawId = selectedRickshawId();
            if (rickshawId == null) {
                return;
            }
            try {
                String msg = rideService.stepRickshawMovement(rickshawId);
                message.showSuccess(msg);
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        liveGpsToggleButton.addActionListener(e -> toggleLiveGps());

        startRideButton.addActionListener(e -> {
            int row = activeTable.getSelectedRow();
            if (row < 0 && activeTableModel.getRowCount() > 0) {
                row = 0;
            }
            if (row < 0) {
                message.showError("No active ride on this rickshaw to start.");
                return;
            }
            int rideId = (Integer) activeTableModel.getValueAt(row, 0);
            try {
                message.showSuccess(rideService.startRide(rideId));
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        completeRideButton.addActionListener(e -> {
            int row = activeTable.getSelectedRow();
            if (row < 0 && activeTableModel.getRowCount() > 0) {
                row = 0;
            }
            if (row < 0) {
                message.showError("No active ride on this rickshaw to complete.");
                return;
            }
            int rideId = (Integer) activeTableModel.getValueAt(row, 0);
            try {
                message.showSuccess(rideService.completeRide(rideId));
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDriverRouteView();
            }
        });
    }

    private void showRegisterDriverDialog() {
        JTextField uField = new JTextField(12);
        JPasswordField pField = new JPasswordField("1234", 12);
        JTextField nameField = new JTextField(14);
        Location[] locs = routeService.getLocationsSorted().toArray(new Location[0]);
        JComboBox<Location> startLocBox = new JComboBox<>(locs);
        JTextField phoneField = new JTextField("018", 12);

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Driver Username:"));
        panel.add(uField);
        panel.add(new JLabel("Password:"));
        panel.add(pField);
        panel.add(new JLabel("Driver Full Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Starting Stand / Location:"));
        panel.add(startLocBox);
        panel.add(new JLabel("Phone:"));
        panel.add(phoneField);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Register New Driver & Rickshaw (Saved to drivers.csv & rickshaws.csv)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            try {
                Location start = (Location) startLocBox.getSelectedItem();
                DriverAccount created = rideService.registerDriver(
                        uField.getText(),
                        new String(pField.getPassword()),
                        nameField.getText(),
                        start == null ? null : start.getId(),
                        phoneField.getText()
                );
                driverSelector.setSelectedItem(created.getRickshawId());
                message.showSuccess("Registered Driver " + created.getDriverName()
                        + " with Rickshaw " + created.getRickshawId() + "!");
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        }
    }

    private void syncRickshawControlsFromModel(Rickshaw r) {
        for (int i = 0; i < driverLocationBox.getItemCount(); i++) {
            if (driverLocationBox.getItemAt(i).getId().equals(r.getLocationId())) {
                driverLocationBox.setSelectedIndex(i);
                break;
            }
        }
        if (r.getTargetDestinationId().isEmpty()) {
            driverTargetDestBox.setSelectedIndex(0);
        } else {
            for (int i = 1; i < driverTargetDestBox.getItemCount(); i++) {
                if (driverTargetDestBox.getItemAt(i).startsWith(r.getTargetDestinationId() + " - ")) {
                    driverTargetDestBox.setSelectedIndex(i);
                    break;
                }
            }
        }
        driverAvailableSeatsBox.setSelectedItem(r.getAvailableSeats());
    }

    private String selectedRickshawId() {
        return (String) driverSelector.getSelectedItem();
    }

    private void acceptSelectedPendingRide() {
        String rickshawId = selectedRickshawId();
        if (rickshawId == null) {
            message.showError("Select a driver/rickshaw first.");
            return;
        }
        int row = pendingTable.getSelectedRow();
        if (row < 0 && pendingTableModel.getRowCount() > 0) {
            row = 0;
        }
        if (row < 0) {
            message.showError("No pending ride request selected to accept.");
            return;
        }
        int rideId = (Integer) pendingTableModel.getValueAt(row, 0);
        try {
            String msg = rideService.driverAcceptRide(rickshawId, rideId);
            message.showSuccess(msg);
        } catch (RuntimeException ex) {
            message.showError(ex.getMessage());
        }
    }

    private void rejectSelectedPendingRide() {
        String rickshawId = selectedRickshawId();
        if (rickshawId == null) {
            message.showError("Select a driver/rickshaw first.");
            return;
        }
        int row = pendingTable.getSelectedRow();
        if (row < 0 && pendingTableModel.getRowCount() > 0) {
            row = 0;
        }
        if (row < 0) {
            message.showError("No pending ride request selected to reject.");
            return;
        }
        int rideId = (Integer) pendingTableModel.getValueAt(row, 0);
        try {
            String msg = rideService.driverRejectRide(rickshawId, rideId);
            message.showSuccess(msg);
        } catch (RuntimeException ex) {
            message.showError(ex.getMessage());
        }
    }

    private void toggleLiveGps() {
        if (liveGpsRunning) {
            liveGpsTimer.stop();
            liveGpsRunning = false;
            liveGpsToggleButton.setText("Start Live GPS Auto-Drive");
            message.showSuccess("Live GPS simulation paused.");
        } else {
            liveGpsRunning = true;
            liveGpsToggleButton.setText("Pause Live GPS Auto-Drive");
            liveGpsTimer.start();
            message.showSuccess("Live GPS Auto-Drive started! Rickshaws with active rides are moving on the map.");
        }
    }

    private void onLiveGpsTick() {
        boolean anyMoved = false;
        for (Rickshaw r : rideService.getRickshaws()) {
            if (!rideService.getActiveRidesForRickshaw(r.getId()).isEmpty()) {
                boolean moved = rideService.tickSmoothAnimation(r.getId(), 0.25);
                anyMoved = anyMoved || moved;
            }
        }
        if (!anyMoved) {
            liveGpsTimer.stop();
            liveGpsRunning = false;
            liveGpsToggleButton.setText("Start Live GPS Auto-Drive");
            message.showSuccess("All active rides reached their destinations! Live GPS Auto-Drive finished.");
        }
    }

    private void updateDriverRouteView() {
        String rickshawId = selectedRickshawId();
        Rickshaw rickshaw = rickshawId == null ? null : rideService.getRickshaw(rickshawId);
        if (rickshaw == null) {
            return;
        }
        driverMapCanvas.setHighlightedRickshawId(rickshawId);

        int pendingRow = pendingTable.getSelectedRow();
        if (pendingRow >= 0 && pendingRow < pendingTableModel.getRowCount()) {
            int rideId = (Integer) pendingTableModel.getValueAt(pendingRow, 0);
            RideRequest req = rideService.getRide(rideId);
            if (req != null) {
                RouteResult toPickup = routeService.findRoute(rickshaw.getLocationId(), req.getPickupId());
                RouteResult trip = routeService.findRoute(req.getPickupId(), req.getDestinationId());
                RouteCompatibility.CompatibilityResult compat = RouteCompatibility.evaluate(
                        routeService.getGraph(), rickshaw, rideService.getActiveRidesForRickshaw(rickshawId),
                        req.getPickupId(), req.getDestinationId(), req.getSeatsRequested());
                driverMapCanvas.setApproachRoute(toPickup.isFound() ? toPickup.getPath() : List.of());
                driverMapCanvas.setHighlightedRoute(trip.isFound() ? trip.getPath() : List.of(),
                        req.getPickupId(), req.getDestinationId());

                StringBuilder sb = new StringBuilder();
                sb.append("=== PREVIEW FOR PENDING REQUEST #").append(req.getId())
                        .append(" (").append(req.getPassengerName()).append(", ")
                        .append(req.getSeatsRequested()).append(" seat(s)) ===\n");
                sb.append("Compatibility & Detour: ").append(compat.getSummary()).append("\n");
                sb.append("Driver Current Position: ").append(routeService.getLocationName(rickshaw.getLocationId())).append("\n");
                sb.append("1) Route to Passenger Pickup (").append(routeService.getLocationName(req.getPickupId())).append("):\n");
                sb.append(routeService.describeRoute(toPickup)).append("\n");
                sb.append("2) Passenger Trip Route (to ").append(routeService.getLocationName(req.getDestinationId())).append("):\n");
                sb.append(routeService.describeRoute(trip));
                routeInstructionsArea.setText(sb.toString());
                routeInstructionsArea.setCaretPosition(0);
                return;
            }
        }

        List<RideRequest> active = rideService.getActiveRidesForRickshaw(rickshawId);
        if (!active.isEmpty()) {
            RideRequest primary = active.get(0);
            if (primary.getStatus() == RideStatus.MATCHED) {
                RouteResult toPickup = routeService.findRoute(rickshaw.getLocationId(), primary.getPickupId());
                RouteResult trip = routeService.findRoute(primary.getPickupId(), primary.getDestinationId());
                driverMapCanvas.setApproachRoute(toPickup.isFound() ? toPickup.getPath() : List.of());
                driverMapCanvas.setHighlightedRoute(trip.isFound() ? trip.getPath() : List.of(),
                        primary.getPickupId(), primary.getDestinationId());
                routeInstructionsArea.setText("ACTIVE PICKUP NAVIGATION for Ride #" + primary.getId()
                        + " (" + primary.getPassengerName() + "):\n"
                        + routeService.describeRoute(toPickup));
            } else {
                RouteResult toDest = routeService.findRoute(rickshaw.getLocationId(), primary.getDestinationId());
                driverMapCanvas.setApproachRoute(List.of());
                driverMapCanvas.setHighlightedRoute(toDest.isFound() ? toDest.getPath() : List.of(),
                        rickshaw.getLocationId(), primary.getDestinationId());
                routeInstructionsArea.setText("ACTIVE TRIP NAVIGATION to Destination for Ride #" + primary.getId()
                        + " (" + primary.getPassengerName() + "):\n"
                        + routeService.describeRoute(toDest));
            }
            routeInstructionsArea.setCaretPosition(0);
        } else {
            driverMapCanvas.setApproachRoute(List.of());
            driverMapCanvas.setHighlightedRoute(List.of(), null, null);
            routeInstructionsArea.setText(rickshaw.getDriverName() + " (" + rickshaw.getId() + ") is standing at "
                    + routeService.getLocationName(rickshaw.getLocationId()) + ".\n"
                    + "Available Seats: " + rickshaw.getAvailableSeats() + " / " + rickshaw.getMaxSeats() + ".\n"
                    + "Select a Pending Ride Request above to inspect the route and press 'Accept Selected Request'.");
        }
    }

    public void refresh() {
        syncingSelector = true;
        try {
            String prev = selectedRickshawId();
            driverSelector.removeAllItems();
            for (Rickshaw r : rideService.getRickshaws()) {
                driverSelector.addItem(r.getId());
            }
            if (prev != null) {
                driverSelector.setSelectedItem(prev);
            }
        } finally {
            syncingSelector = false;
        }

        String rickshawId = selectedRickshawId();
        Rickshaw rickshaw = rickshawId == null ? null : rideService.getRickshaw(rickshawId);

        if (rickshaw != null) {
            driverStatusLabel.setText(String.format(Locale.ROOT,
                    "%s | At: %s",
                    rickshaw.getDriverName(),
                    routeService.getLocationName(rickshaw.getLocationId())));
            seatBadgeLabel.setText(String.format(Locale.ROOT,
                    "| Seats: %d/%d Free",
                    rickshaw.getAvailableSeats(), rickshaw.getMaxSeats()));
            syncRickshawControlsFromModel(rickshaw);
        }

        if (policyBox.getSelectedItem() != rideService.getQueuePolicy()) {
            policyBox.setSelectedItem(rideService.getQueuePolicy());
        }

        Integer selectedPendingId = null;
        if (pendingTable.getSelectedRow() >= 0) {
            selectedPendingId = (Integer) pendingTableModel.getValueAt(pendingTable.getSelectedRow(), 0);
        }

        pendingTableModel.setRowCount(0);
        for (RideRequest req : rideService.getPendingInOrder()) {
            double toPickup = 0.0;
            double detour = 0.0;
            String matchStatus = "Available";
            if (rickshaw != null) {
                RouteCompatibility.CompatibilityResult compat = RouteCompatibility.evaluate(
                        routeService.getGraph(), rickshaw, rideService.getActiveRidesForRickshaw(rickshaw.getId()),
                        req.getPickupId(), req.getDestinationId(), req.getSeatsRequested());
                toPickup = compat.getDistanceToPickupMeters();
                detour = compat.getDetourMeters();
                if (req.isRejectedBy(rickshaw.getId())) {
                    matchStatus = "Rejected by you";
                } else if (!rickshaw.hasSeatsFor(req.getSeatsRequested())) {
                    matchStatus = "Needs " + req.getSeatsRequested() + " seat(s) (Full)";
                } else if (rickshaw.getId().equals(req.getOfferedRickshawId())) {
                    matchStatus = "★ BEST MATCH FOR YOU (" + compat.getSummary() + ")";
                } else {
                    matchStatus = compat.getSummary();
                }
            }
            pendingTableModel.addRow(new Object[]{
                    req.getId(),
                    req.getPassengerName(),
                    req.getPassengerType(),
                    req.getSeatsRequested(),
                    routeService.getLocationName(req.getPickupId()),
                    routeService.getLocationName(req.getDestinationId()),
                    Double.isInfinite(toPickup) ? "N/A" : String.format(Locale.ROOT, "%.1f", toPickup),
                    Double.isInfinite(detour) ? "N/A" : String.format(Locale.ROOT, "+%.0f", detour),
                    matchStatus
            });
        }

        if (selectedPendingId != null) {
            for (int r = 0; r < pendingTableModel.getRowCount(); r++) {
                if (selectedPendingId.equals(pendingTableModel.getValueAt(r, 0))) {
                    pendingTable.setRowSelectionInterval(r, r);
                    break;
                }
            }
        } else if (pendingTableModel.getRowCount() > 0) {
            pendingTable.setRowSelectionInterval(0, 0);
        }

        activeTableModel.setRowCount(0);
        if (rickshaw != null) {
            for (RideRequest active : rideService.getActiveRidesForRickshaw(rickshaw.getId())) {
                activeTableModel.addRow(new Object[]{
                        active.getId(),
                        active.getPassengerName(),
                        active.getSeatsRequested() + " seat(s)",
                        routeService.getLocationName(active.getPickupId()),
                        routeService.getLocationName(active.getDestinationId()),
                        active.getStatus().getDisplayLabel()
                });
            }
        }
        if (activeTableModel.getRowCount() > 0 && activeTable.getSelectedRow() < 0) {
            activeTable.setRowSelectionInterval(0, 0);
        }

        updateDriverRouteView();
        driverMapCanvas.repaint();
    }
}
