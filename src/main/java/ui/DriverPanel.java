package ui;

import dsa.QueuePolicy;
import dsa.RouteResult;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import service.RideService;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;

/**
 * Driver Application View:
 * - Select active Rickshaw / Driver
 * - See current Location & Seat Availability (up to 3 seats shared)
 * - View Pending Ride Requests in Queue / Priority order with Dijkstra distance to Pickup
 * - Accept or Reject Ride Requests
 * - See Route to Passenger Pickup & Destination on 2D Map + Text
 * - Step-by-step or Live Animated GPS movement along the route
 */
public class DriverPanel extends JPanel {
    private static final String[] PENDING_COLUMNS = {
            "Ride #", "Passenger", "Priority", "Seats", "Pickup", "Destination", "To Pickup (m)", "Trip (m)", "Seat Status"
    };
    private static final String[] ACTIVE_COLUMNS = {
            "Ride #", "Passenger", "Seats", "Pickup", "Destination", "Status"
    };

    private final RideService rideService;
    private final RouteService routeService;

    // Package-private for testing
    final JComboBox<String> driverSelector = new JComboBox<>();
    final JLabel driverStatusLabel = new JLabel();
    final JLabel seatBadgeLabel = new JLabel();
    final JComboBox<QueuePolicy> policyBox = new JComboBox<>(QueuePolicy.values());

    final DefaultTableModel pendingTableModel = new DefaultTableModel(PENDING_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable pendingTable = new JTable(pendingTableModel);
    final JButton acceptRideButton = new JButton("Accept Selected Request");
    final JButton rejectRideButton = new JButton("Reject / Skip Request");
    final JButton autoMatchButton = new JButton("Auto-Match Nearest Rickshaw");

    final DefaultTableModel activeTableModel = new DefaultTableModel(ACTIVE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable activeTable = new JTable(activeTableModel);
    final JButton stepGpsButton = new JButton("Drive Next Stop (Step)");
    final JButton liveGpsToggleButton = new JButton("Start Live GPS Auto-Drive");
    final JButton startRideButton = new JButton("Pickup / Start Ride");
    final JButton completeRideButton = new JButton("Complete Ride");

    final JTextArea routeInstructionsArea = new JTextArea(8, 36);
    final CampusMapCanvas driverMapCanvas;
    final MessageLabel message = new MessageLabel();

    private final Timer liveGpsTimer;
    private boolean liveGpsRunning = false;

    public DriverPanel(RideService rideService) {
        super(new BorderLayout(8, 8));
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        this.driverMapCanvas = new CampusMapCanvas(rideService);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Rickshaw r : rideService.getRickshaws()) {
            driverSelector.addItem(r.getId());
        }
        policyBox.setSelectedItem(rideService.getQueuePolicy());

        liveGpsTimer = new Timer(120, e -> onLiveGpsTick());

        add(buildDriverHeader(), BorderLayout.NORTH);
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftRequestsAndActivePanel(), buildRightNavigationPanel());
        centerSplit.setResizeWeight(0.50);
        add(centerSplit, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        wireEvents();
        rideService.addChangeListener(this::refresh);
        refresh();
    }

    private JPanel buildDriverHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(39, 174, 96), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        header.setBackground(new Color(234, 250, 241));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(new JLabel("Active Driver / Rickshaw:"));
        driverSelector.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        left.add(driverSelector);
        driverStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        left.add(driverStatusLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        seatBadgeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        seatBadgeLabel.setForeground(new Color(20, 90, 50));
        right.add(seatBadgeLabel);
        right.add(new JLabel(" | Queue Policy:"));
        right.add(policyBox);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildLeftRequestsAndActivePanel() {
        JPanel left = new JPanel(new BorderLayout(6, 6));

        // 1. Incoming Pending Requests
        JPanel pendingPanel = new JPanel(new BorderLayout(4, 4));
        pendingPanel.setBorder(BorderFactory.createTitledBorder(
                "1. Incoming Ride Requests (Priority / FIFO Queue — Accept or Reject)"));
        pendingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setPreferredSize(new Dimension(480, 180));

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
                "2. Passengers Currently Assigned to This Rickshaw (Shared up to 3 Seats)"));
        activeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane activeScroll = new JScrollPane(activeTable);
        activeScroll.setPreferredSize(new Dimension(480, 120));

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
        routeTextPanel.setBorder(BorderFactory.createTitledBorder("3. Driver Route to Passenger Pickup & Destination (Dijkstra)"));
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
        driverSelector.addActionListener(e -> refresh());
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

        // If a pending request is selected, preview route to that passenger's pickup + trip
        int pendingRow = pendingTable.getSelectedRow();
        if (pendingRow >= 0 && pendingRow < pendingTableModel.getRowCount()) {
            int rideId = (Integer) pendingTableModel.getValueAt(pendingRow, 0);
            RideRequest req = rideService.getRide(rideId);
            if (req != null) {
                RouteResult toPickup = routeService.findRoute(rickshaw.getLocationId(), req.getPickupId());
                RouteResult trip = routeService.findRoute(req.getPickupId(), req.getDestinationId());
                driverMapCanvas.setApproachRoute(toPickup.isFound() ? toPickup.getPath() : List.of());
                driverMapCanvas.setHighlightedRoute(trip.isFound() ? trip.getPath() : List.of(),
                        req.getPickupId(), req.getDestinationId());

                StringBuilder sb = new StringBuilder();
                sb.append("=== PREVIEW FOR PENDING REQUEST #").append(req.getId())
                        .append(" (").append(req.getPassengerName()).append(", ")
                        .append(req.getSeatsRequested()).append(" seat(s)) ===\n");
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

        // Otherwise show active navigation route for this driver's assigned rides
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
        String rickshawId = selectedRickshawId();
        Rickshaw rickshaw = rickshawId == null ? null : rideService.getRickshaw(rickshawId);

        if (rickshaw != null) {
            driverStatusLabel.setText(String.format(Locale.ROOT,
                    "%s | At: %s",
                    rickshaw.getDriverName(),
                    routeService.getLocationName(rickshaw.getLocationId())));
            seatBadgeLabel.setText(String.format(Locale.ROOT,
                    "Seats: %d/%d Free (%d Occupied)",
                    rickshaw.getAvailableSeats(), rickshaw.getMaxSeats(), rickshaw.getOccupiedSeats()));
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
            String seatStatus = "Available";
            if (rickshaw != null) {
                RouteResult r = routeService.findRoute(rickshaw.getLocationId(), req.getPickupId());
                toPickup = r.isFound() ? r.getTotalDistanceMeters() : -1;
                if (req.isRejectedBy(rickshaw.getId())) {
                    seatStatus = "Rejected by you";
                } else if (!rickshaw.hasSeatsFor(req.getSeatsRequested())) {
                    seatStatus = "Needs " + req.getSeatsRequested() + " seat(s) (Full)";
                } else if (rickshaw.getOccupiedSeats() > 0) {
                    seatStatus = "Can Share (" + rickshaw.getAvailableSeats() + " free)";
                } else {
                    seatStatus = "Ready (" + rickshaw.getAvailableSeats() + " free)";
                }
            }
            pendingTableModel.addRow(new Object[]{
                    req.getId(),
                    req.getPassengerName(),
                    req.getPassengerType(),
                    req.getSeatsRequested(),
                    routeService.getLocationName(req.getPickupId()),
                    routeService.getLocationName(req.getDestinationId()),
                    toPickup >= 0 ? String.format(Locale.ROOT, "%.1f", toPickup) : "N/A",
                    String.format(Locale.ROOT, "%.1f", req.getDistanceMeters()),
                    seatStatus
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
                        active.getStatus()
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
