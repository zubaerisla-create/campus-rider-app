package ui;

import dsa.RouteResult;
import model.Location;
import model.PassengerType;
import model.RideRequest;
import model.RideStatus;
import model.Rickshaw;
import service.RideService;
import service.RouteService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;

/**
 * Passenger Application View:
 * - Trie location search + Pickup/Destination selection
 * - Seat selection (1, 2, or 3 seats for shared rickshaw rides)
 * - Live Tracking status banner + progress bar (approaching pickup / in progress / completed)
 * - 2D Live Campus Map Canvas showing shortest route & live rickshaw positions
 */
public class PassengerPanel extends JPanel {
    private static final String[] MY_RIDES_COLUMNS = {
            "Ride #", "Passenger", "Priority", "Seats", "Pickup", "Destination", "Dist (m)", "Status", "Driver / Rickshaw"
    };

    private final RideService rideService;
    private final RouteService routeService;

    // Package-private for testing
    final JTextField passengerNameField = new JTextField(14);
    final JComboBox<PassengerType> passengerTypeBox = new JComboBox<>(PassengerType.values());
    final JComboBox<Integer> seatsBox = new JComboBox<>(new Integer[]{1, 2, 3});
    final JComboBox<Location> pickupBox;
    final JComboBox<Location> destinationBox;
    final JTextField searchField = new JTextField(14);
    final DefaultListModel<Location> trieMatchesModel = new DefaultListModel<>();
    final JList<Location> trieMatchesList = new JList<>(trieMatchesModel);
    final JButton setPickupBtn = new JButton("Set Pickup");
    final JButton setDestBtn = new JButton("Set Dest");
    final JLabel routePreviewLabel = new JLabel("Route distance: --");
    final JButton requestRideButton = new JButton("Request Ride Now");
    final JButton cancelRideButton = new JButton("Cancel Selected Ride");

    // Live tracking components
    final JLabel liveTrackingBanner = new JLabel("No active ride selected. Create or select a ride to track live.");
    final JProgressBar rideProgressBar = new JProgressBar(0, 100);
    final CampusMapCanvas mapCanvas;
    final DefaultTableModel ridesTableModel = new DefaultTableModel(MY_RIDES_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable ridesTable = new JTable(ridesTableModel);
    final DefaultListModel<String> notificationModel = new DefaultListModel<>();
    final MessageLabel message = new MessageLabel();

    public PassengerPanel(RideService rideService) {
        super(new BorderLayout(8, 8));
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        this.mapCanvas = new CampusMapCanvas(rideService);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        Location[] locations = routeService.getLocationsSorted().toArray(new Location[0]);
        pickupBox = new JComboBox<>(locations);
        destinationBox = new JComboBox<>(locations);
        if (locations.length > 1) {
            destinationBox.setSelectedIndex(1);
        }

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftBookingPanel(), buildRightTrackingPanel());
        mainSplit.setResizeWeight(0.36);

        add(buildLiveStatusHeader(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        wireEvents();
        rideService.addChangeListener(this::refresh);
        updateRoutePreview();
        refresh();
    }

    private JPanel buildLiveStatusHeader() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 128, 185), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        panel.setBackground(new Color(235, 245, 251));

        liveTrackingBanner.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        liveTrackingBanner.setForeground(new Color(21, 67, 96));

        rideProgressBar.setStringPainted(true);
        rideProgressBar.setString("Idle");
        rideProgressBar.setPreferredSize(new Dimension(220, 20));

        panel.add(liveTrackingBanner, BorderLayout.CENTER);
        panel.add(rideProgressBar, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildLeftBookingPanel() {
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setPreferredSize(new Dimension(370, 560));

        // 1. Booking Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("1. Book a Campus Rickshaw (Passenger)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        addFormRow(form, c, 0, "Passenger Name:", passengerNameField);
        addFormRow(form, c, 1, "Priority Type:", passengerTypeBox);
        addFormRow(form, c, 2, "Seats Needed (1-3):", seatsBox);
        addFormRow(form, c, 3, "Pickup Point:", pickupBox);
        addFormRow(form, c, 4, "Destination:", destinationBox);

        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        routePreviewLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        routePreviewLabel.setForeground(new Color(22, 160, 133));
        form.add(routePreviewLabel, c);

        c.gridy = 6;
        requestRideButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        form.add(requestRideButton, c);

        // 2. Quick Trie Search Box
        JPanel triePanel = new JPanel(new BorderLayout(4, 4));
        triePanel.setBorder(BorderFactory.createTitledBorder("Quick Location Search (Trie)"));
        JPanel searchRow = new JPanel(new BorderLayout(4, 4));
        searchRow.add(new JLabel("Type keyword/alias: "), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        triePanel.add(searchRow, BorderLayout.NORTH);

        trieMatchesList.setVisibleRowCount(4);
        trieMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        triePanel.add(new JScrollPane(trieMatchesList), BorderLayout.CENTER);

        JPanel trieBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        trieBtns.add(setPickupBtn);
        trieBtns.add(setDestBtn);
        triePanel.add(trieBtns, BorderLayout.SOUTH);

        // 3. Live Notification Feed
        JPanel notifPanel = new JPanel(new BorderLayout(4, 4));
        notifPanel.setBorder(BorderFactory.createTitledBorder("Live Passenger Notifications"));
        JList<String> notifList = new JList<>(notificationModel);
        notifList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        notifPanel.add(new JScrollPane(notifList), BorderLayout.CENTER);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, triePanel, notifPanel);
        bottomSplit.setResizeWeight(0.45);

        left.add(form, BorderLayout.NORTH);
        left.add(bottomSplit, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildRightTrackingPanel() {
        JPanel right = new JPanel(new BorderLayout(6, 6));

        JPanel mapWrapper = new JPanel(new BorderLayout());
        mapWrapper.setBorder(BorderFactory.createTitledBorder("2. Live Rickshaw GPS & Dijkstra Shortest Route Map"));
        mapWrapper.add(mapCanvas, BorderLayout.CENTER);

        JPanel ridesWrapper = new JPanel(new BorderLayout(4, 4));
        ridesWrapper.setBorder(BorderFactory.createTitledBorder("3. My Ride Requests & Live Status"));
        ridesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tableScroll = new JScrollPane(ridesTable);
        tableScroll.setPreferredSize(new Dimension(500, 150));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(cancelRideButton);
        actionRow.add(new JLabel("  (Tip: Select any ride in the table to track its Rickshaw & Route on the map)"));

        ridesWrapper.add(tableScroll, BorderLayout.CENTER);
        ridesWrapper.add(actionRow, BorderLayout.SOUTH);

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapWrapper, ridesWrapper);
        verticalSplit.setResizeWeight(0.68);
        right.add(verticalSplit, BorderLayout.CENTER);
        return right;
    }

    private static void addFormRow(JPanel form, GridBagConstraints c, int row, String label, Component comp) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(comp, c);
    }

    private void wireEvents() {
        pickupBox.addActionListener(e -> updateRoutePreview());
        destinationBox.addActionListener(e -> updateRoutePreview());

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateTrieSearch();
            }

            public void removeUpdate(DocumentEvent e) {
                updateTrieSearch();
            }

            public void changedUpdate(DocumentEvent e) {
                updateTrieSearch();
            }
        });

        setPickupBtn.addActionListener(e -> {
            Location loc = trieMatchesList.getSelectedValue();
            if (loc != null) {
                pickupBox.setSelectedItem(loc);
                message.showSuccess("Pickup set to " + loc.getName());
            } else {
                message.showError("Select a location from the search list first.");
            }
        });

        setDestBtn.addActionListener(e -> {
            Location loc = trieMatchesList.getSelectedValue();
            if (loc != null) {
                destinationBox.setSelectedItem(loc);
                message.showSuccess("Destination set to " + loc.getName());
            } else {
                message.showError("Select a location from the search list first.");
            }
        });

        requestRideButton.addActionListener(e -> createPassengerRide());
        cancelRideButton.addActionListener(e -> cancelSelectedRide());

        ridesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateLiveTrackingForSelectedRide();
            }
        });
    }

    private void updateTrieSearch() {
        String q = searchField.getText();
        trieMatchesModel.clear();
        List<Location> results = routeService.searchLocations(q);
        for (Location loc : results) {
            trieMatchesModel.addElement(loc);
        }
        if (!results.isEmpty()) {
            trieMatchesList.setSelectedIndex(0);
        }
    }

    private void updateRoutePreview() {
        Location from = (Location) pickupBox.getSelectedItem();
        Location to = (Location) destinationBox.getSelectedItem();
        if (from == null || to == null) {
            return;
        }
        if (from.getId().equals(to.getId())) {
            routePreviewLabel.setText("Pickup and destination are the same.");
            mapCanvas.setHighlightedRoute(List.of(from.getId()), from.getId(), to.getId());
            return;
        }
        RouteResult route = routeService.findRoute(from.getId(), to.getId());
        if (route.isFound()) {
            routePreviewLabel.setText(String.format(Locale.ROOT,
                    "Shortest Route: %.1f m (%d stops via Dijkstra)",
                    route.getTotalDistanceMeters(), route.getPath().size()));
            if (ridesTable.getSelectedRow() < 0) {
                mapCanvas.setHighlightedRoute(route.getPath(), from.getId(), to.getId());
                mapCanvas.setApproachRoute(List.of());
            }
        }
    }

    private void createPassengerRide() {
        try {
            Location pickup = (Location) pickupBox.getSelectedItem();
            Location dest = (Location) destinationBox.getSelectedItem();
            int seats = (Integer) seatsBox.getSelectedItem();
            RideRequest created = rideService.createRequest(
                    passengerNameField.getText(),
                    (PassengerType) passengerTypeBox.getSelectedItem(),
                    pickup == null ? null : pickup.getId(),
                    dest == null ? null : dest.getId(),
                    seats
            );
            passengerNameField.setText("");
            message.showSuccess(String.format(Locale.ROOT,
                    "Ride #%d requested (%d seat%s, %.1f m)! Waiting for a Driver to accept.",
                    created.getId(), created.getSeatsRequested(),
                    created.getSeatsRequested() > 1 ? "s" : "", created.getDistanceMeters()));
            selectRideInTable(created.getId());
        } catch (RuntimeException ex) {
            message.showError(ex.getMessage());
        }
    }

    private void cancelSelectedRide() {
        int row = ridesTable.getSelectedRow();
        if (row < 0) {
            message.showError("Select a ride in the table first to cancel.");
            return;
        }
        int rideId = (Integer) ridesTableModel.getValueAt(row, 0);
        try {
            String msg = rideService.cancelRide(rideId);
            message.showSuccess(msg);
        } catch (RuntimeException ex) {
            message.showError(ex.getMessage());
        }
    }

    private void selectRideInTable(int rideId) {
        for (int r = 0; r < ridesTableModel.getRowCount(); r++) {
            if (Integer.valueOf(rideId).equals(ridesTableModel.getValueAt(r, 0))) {
                ridesTable.setRowSelectionInterval(r, r);
                break;
            }
        }
    }

    private void updateLiveTrackingForSelectedRide() {
        int row = ridesTable.getSelectedRow();
        if (row < 0) {
            if (ridesTableModel.getRowCount() > 0) {
                ridesTable.setRowSelectionInterval(ridesTableModel.getRowCount() - 1, ridesTableModel.getRowCount() - 1);
                return;
            }
            liveTrackingBanner.setText("No ride selected. Book a ride above to see Live Rickshaw Tracking.");
            rideProgressBar.setValue(0);
            rideProgressBar.setString("Idle");
            updateRoutePreview();
            return;
        }

        int rideId = (Integer) ridesTableModel.getValueAt(row, 0);
        RideRequest ride = rideService.getRide(rideId);
        if (ride == null) {
            return;
        }

        RouteResult tripRoute = routeService.findRoute(ride.getPickupId(), ride.getDestinationId());
        mapCanvas.setHighlightedRoute(tripRoute.getPath(), ride.getPickupId(), ride.getDestinationId());
        mapCanvas.setHighlightedRickshawId(ride.getRickshawId().isEmpty() ? null : ride.getRickshawId());

        if (ride.getStatus() == RideStatus.PENDING) {
            mapCanvas.setApproachRoute(List.of());
            liveTrackingBanner.setText(String.format(Locale.ROOT,
                    "Ride #%d (%s, %d seat): PENDING — Waiting for Driver to Accept...",
                    ride.getId(), ride.getPassengerName(), ride.getSeatsRequested()));
            rideProgressBar.setValue(15);
            rideProgressBar.setString("Waiting for Driver...");
        } else if (ride.getStatus() == RideStatus.MATCHED) {
            Rickshaw r = rideService.getRickshaw(ride.getRickshawId());
            RouteResult approach = r != null
                    ? routeService.findRoute(r.getLocationId(), ride.getPickupId())
                    : null;
            mapCanvas.setApproachRoute(approach != null && approach.isFound() ? approach.getPath() : List.of());
            double distToPickup = approach != null && approach.isFound() ? approach.getTotalDistanceMeters() : 0.0;
            String locName = r != null ? routeService.getLocationName(r.getLocationId()) : "";
            liveTrackingBanner.setText(String.format(Locale.ROOT,
                    "DRIVER ACCEPTED! %s (%s) is at %s (%.0f m away) — Approaching Pickup (%s)",
                    r != null ? r.getDriverName() : "", ride.getRickshawId(),
                    locName, distToPickup, routeService.getLocationName(ride.getPickupId())));
            rideProgressBar.setValue(45);
            rideProgressBar.setString(distToPickup == 0 ? "At Pickup!" : String.format(Locale.ROOT, "Approaching (%.0fm)", distToPickup));
        } else if (ride.getStatus() == RideStatus.IN_PROGRESS) {
            mapCanvas.setApproachRoute(List.of());
            Rickshaw r = rideService.getRickshaw(ride.getRickshawId());
            RouteResult rem = r != null
                    ? routeService.findRoute(r.getLocationId(), ride.getDestinationId())
                    : null;
            double remDist = rem != null && rem.isFound() ? rem.getTotalDistanceMeters() : 0.0;
            int pct = ride.getDistanceMeters() > 0
                    ? (int) Math.round(50 + 50 * Math.max(0.0, 1.0 - (remDist / ride.getDistanceMeters())))
                    : 75;
            String locName = r != null ? routeService.getLocationName(r.getLocationId()) : "";
            liveTrackingBanner.setText(String.format(Locale.ROOT,
                    "RIDE IN PROGRESS: %s is at %s -> Heading to %s (%.0f m remaining)",
                    ride.getRickshawId(), locName,
                    routeService.getLocationName(ride.getDestinationId()), remDist));
            rideProgressBar.setValue(Math.min(95, Math.max(50, pct)));
            rideProgressBar.setString("On Trip (" + Math.min(95, Math.max(50, pct)) + "%)");
        } else if (ride.getStatus() == RideStatus.COMPLETED) {
            mapCanvas.setApproachRoute(List.of());
            liveTrackingBanner.setText(String.format(Locale.ROOT,
                    "RIDE #%d COMPLETED! Arrived safely at %s with %s.",
                    ride.getId(), routeService.getLocationName(ride.getDestinationId()), ride.getRickshawId()));
            rideProgressBar.setValue(100);
            rideProgressBar.setString("Completed (100%)");
        } else if (ride.getStatus() == RideStatus.CANCELLED) {
            mapCanvas.setApproachRoute(List.of());
            liveTrackingBanner.setText("Ride #" + ride.getId() + " was CANCELLED.");
            rideProgressBar.setValue(0);
            rideProgressBar.setString("Cancelled");
        }
    }

    public void refresh() {
        Integer selectedRideId = null;
        if (ridesTable.getSelectedRow() >= 0) {
            selectedRideId = (Integer) ridesTableModel.getValueAt(ridesTable.getSelectedRow(), 0);
        }

        ridesTableModel.setRowCount(0);
        for (RideRequest r : rideService.getRides()) {
            Rickshaw assigned = r.getRickshawId().isEmpty() ? null : rideService.getRickshaw(r.getRickshawId());
            String driverInfo = assigned == null ? "Waiting..." : assigned.getId() + " (" + assigned.getDriverName() + ")";
            ridesTableModel.addRow(new Object[]{
                    r.getId(),
                    r.getPassengerName(),
                    r.getPassengerType(),
                    r.getSeatsRequested() + " seat(s)",
                    routeService.getLocationName(r.getPickupId()),
                    routeService.getLocationName(r.getDestinationId()),
                    String.format(Locale.ROOT, "%.1f", r.getDistanceMeters()),
                    r.getStatus(),
                    driverInfo
            });
        }

        if (selectedRideId != null) {
            selectRideInTable(selectedRideId);
        } else if (ridesTableModel.getRowCount() > 0) {
            ridesTable.setRowSelectionInterval(ridesTableModel.getRowCount() - 1, ridesTableModel.getRowCount() - 1);
        }

        notificationModel.clear();
        for (String note : rideService.getNotifications()) {
            notificationModel.addElement(note);
        }
        if (notificationModel.isEmpty()) {
            notificationModel.addElement("Ready. Create a ride request to see live updates here.");
        }

        updateLiveTrackingForSelectedRide();
        mapCanvas.repaint();
    }
}
