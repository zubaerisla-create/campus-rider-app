package ui;

import dsa.RickshawSorter;
import dsa.RouteCompatibility;
import dsa.RouteResult;
import model.Location;
import model.PassengerAccount;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;

/**
 * Passenger Dashboard:
 * - Passenger Account Registration & Login (saved in data/passengers.csv)
 * - Trie Location Search + Pickup & Destination selection
 * - Seat selection (1, 2, or 3 seats for shared rickshaw rides)
 * - Available & Compatible Rickshaws table sorted via Merge Sort (Distance / Compatibility / Free Seats)
 * - Send Request to Best Compatible Rickshaw or Selected Rickshaw
 * - Simulated Live Tracking Banner + Progress Bar + 2D Campus Map
 */
public class PassengerPanel extends JPanel {
    private static final String[] AVAILABLE_RICKSHAWS_COLUMNS = {
            "Rickshaw", "Driver", "At Location", "Free Seats", "To Pickup (m)", "Detour (m)", "Route Compatibility"
    };
    private static final String[] MY_RIDES_COLUMNS = {
            "Ride #", "Passenger", "Priority", "Seats", "Pickup", "Destination", "Dist (m)", "Status", "Rickshaw / Offer"
    };

    private final RideService rideService;
    private final RouteService routeService;

    // Account login / registration controls
    final JComboBox<PassengerAccount> accountSelectorBox = new JComboBox<>();
    final JTextField loginUserField = new JTextField(8);
    final JPasswordField loginPassField = new JPasswordField(6);
    final JButton loginButton = new JButton("Login");
    final JButton registerButton = new JButton("Register Passenger");
    final JLabel loggedInBadge = new JLabel("Logged in: Guest");

    // Booking form controls (package-private for testing)
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
    final JButton requestRideButton = new JButton("Request Ride (Auto Best Match)");
    final JButton requestSelectedRickshawButton = new JButton("Send Request to Selected Rickshaw");
    final JButton cancelRideButton = new JButton("Cancel Selected Ride");

    // Available Rickshaws sorting & table
    final JComboBox<RickshawSorter.SortCriterion> sortCriterionBox =
            new JComboBox<>(RickshawSorter.SortCriterion.values());
    final JButton findNearbyButton = new JButton("Find Nearby Rickshaws");
    final DefaultTableModel availableRickshawsModel = new DefaultTableModel(AVAILABLE_RICKSHAWS_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    final JTable availableRickshawsTable = new JTable(availableRickshawsModel);

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

    private boolean syncingAccountBox = false;

    public PassengerPanel(RideService rideService) {
        super(new BorderLayout(6, 6));
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        this.mapCanvas = new CampusMapCanvas(rideService);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        Location[] locations = routeService.getLocationsSorted().toArray(new Location[0]);
        pickupBox = new JComboBox<>(locations);
        destinationBox = new JComboBox<>(locations);
        if (locations.length > 1) {
            destinationBox.setSelectedIndex(1);
        }

        PassengerAccount initial = rideService.getLoggedInPassenger();
        if (initial != null) {
            passengerNameField.setText(initial.getFullName());
            passengerTypeBox.setSelectedItem(initial.getPassengerType());
        }

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftBookingPanel(), buildRightTrackingPanel());
        mainSplit.setResizeWeight(0.37);

        add(buildNorthContainer(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        wireEvents();
        rideService.addChangeListener(this::refresh);
        updateRoutePreview();
        refresh();
    }

    private JPanel buildNorthContainer() {
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(buildAccountHeader(), BorderLayout.NORTH);
        north.add(buildLiveStatusHeader(), BorderLayout.SOUTH);
        return north;
    }

    private JPanel buildAccountHeader() {
        JPanel bar = new JPanel(new BorderLayout(6, 2));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(142, 68, 173), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        bar.setBackground(new Color(245, 238, 248));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(new JLabel("Passenger Account:"));
        left.add(accountSelectorBox);
        loggedInBadge.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        loggedInBadge.setForeground(new Color(91, 44, 111));
        left.add(loggedInBadge);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(new JLabel("User:"));
        loginUserField.setText("tanvir");
        loginPassField.setText("1234");
        right.add(loginUserField);
        right.add(new JLabel("Pass:"));
        right.add(loginPassField);
        right.add(loginButton);
        right.add(registerButton);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildLiveStatusHeader() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 128, 185), 1, true),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
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
        left.setPreferredSize(new Dimension(390, 560));

        // 1. Booking Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("1. Book a Campus Rickshaw (Seat Sharing & Route Match)"));
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
        requestRideButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        form.add(requestRideButton, c);

        c.gridy = 7;
        form.add(requestSelectedRickshawButton, c);

        // 2. Quick Trie Search Box
        JPanel triePanel = new JPanel(new BorderLayout(4, 4));
        triePanel.setBorder(BorderFactory.createTitledBorder("Quick Location Search (Trie Prefix Tree)"));
        JPanel searchRow = new JPanel(new BorderLayout(4, 4));
        searchRow.add(new JLabel("Type keyword/alias: "), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        triePanel.add(searchRow, BorderLayout.NORTH);

        trieMatchesList.setVisibleRowCount(3);
        trieMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        triePanel.add(new JScrollPane(trieMatchesList), BorderLayout.CENTER);

        JPanel trieBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        trieBtns.add(setPickupBtn);
        trieBtns.add(setDestBtn);
        triePanel.add(trieBtns, BorderLayout.SOUTH);

        // 3. Live Notification Feed
        JPanel notifPanel = new JPanel(new BorderLayout(4, 4));
        notifPanel.setBorder(BorderFactory.createTitledBorder("Live Passenger & Matching Events"));
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
        mapWrapper.setBorder(BorderFactory.createTitledBorder("2. Simulated Live GPS Tracking & Dijkstra Shortest Route Map"));
        mapWrapper.add(mapCanvas, BorderLayout.CENTER);

        // Available Rickshaws panel (with Merge Sort options)
        JPanel rickshawsWrapper = new JPanel(new BorderLayout(4, 4));
        rickshawsWrapper.setBorder(BorderFactory.createTitledBorder(
                "3. Available Rickshaws with Free Seats (Sorted via Merge Sort & Route Compatibility)"));
        JPanel sortBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sortBar.add(new JLabel("Sort Available Rickshaws by:"));
        sortBar.add(sortCriterionBox);
        sortBar.add(findNearbyButton);
        availableRickshawsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane availScroll = new JScrollPane(availableRickshawsTable);
        availScroll.setPreferredSize(new Dimension(500, 105));
        rickshawsWrapper.add(sortBar, BorderLayout.NORTH);
        rickshawsWrapper.add(availScroll, BorderLayout.CENTER);

        // My Rides table
        JPanel ridesWrapper = new JPanel(new BorderLayout(4, 4));
        ridesWrapper.setBorder(BorderFactory.createTitledBorder(
                "4. Ride Requests & Lifecycle (Pending -> Accepted -> Started -> Completed)"));
        ridesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tableScroll = new JScrollPane(ridesTable);
        tableScroll.setPreferredSize(new Dimension(500, 115));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(cancelRideButton);
        actionRow.add(new JLabel("  (Select a ride to see live Rickshaw movement on the map)"));

        ridesWrapper.add(tableScroll, BorderLayout.CENTER);
        ridesWrapper.add(actionRow, BorderLayout.SOUTH);

        JSplitPane tablesSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rickshawsWrapper, ridesWrapper);
        tablesSplit.setResizeWeight(0.48);

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapWrapper, tablesSplit);
        verticalSplit.setResizeWeight(0.55);
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
        accountSelectorBox.addActionListener(e -> {
            if (syncingAccountBox) {
                return;
            }
            PassengerAccount selected = (PassengerAccount) accountSelectorBox.getSelectedItem();
            if (selected != null) {
                loginUserField.setText(selected.getUsername());
                loginPassField.setText(selected.getPassword());
                rideService.loginPassenger(selected.getUsername(), selected.getPassword());
                passengerNameField.setText(selected.getFullName());
                passengerTypeBox.setSelectedItem(selected.getPassengerType());
                message.showSuccess("Switched to Passenger account: " + selected.getFullName());
            }
        });

        loginButton.addActionListener(e -> {
            try {
                PassengerAccount acc = rideService.loginPassenger(
                        loginUserField.getText(), new String(loginPassField.getPassword()));
                passengerNameField.setText(acc.getFullName());
                passengerTypeBox.setSelectedItem(acc.getPassengerType());
                message.showSuccess("Logged in as " + acc.getFullName() + " (" + acc.getPassengerType() + ").");
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        });

        registerButton.addActionListener(e -> showRegisterPassengerDialog());

        pickupBox.addActionListener(e -> {
            updateRoutePreview();
            refreshAvailableRickshawsTable();
        });
        destinationBox.addActionListener(e -> {
            updateRoutePreview();
            refreshAvailableRickshawsTable();
        });
        seatsBox.addActionListener(e -> refreshAvailableRickshawsTable());
        sortCriterionBox.addActionListener(e -> refreshAvailableRickshawsTable());
        findNearbyButton.addActionListener(e -> {
            refreshAvailableRickshawsTable();
            message.showSuccess("Sorted available rickshaws using Merge Sort (" + sortCriterionBox.getSelectedItem() + ").");
        });

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

        requestRideButton.addActionListener(e -> createPassengerRide(null));
        requestSelectedRickshawButton.addActionListener(e -> {
            int row = availableRickshawsTable.getSelectedRow();
            if (row < 0) {
                message.showError("Select an available rickshaw from Table 3 first, or use 'Request Ride (Auto Best Match)'.");
                return;
            }
            String rickshawId = (String) availableRickshawsModel.getValueAt(row, 0);
            createPassengerRide(rickshawId);
        });
        cancelRideButton.addActionListener(e -> cancelSelectedRide());

        ridesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateLiveTrackingForSelectedRide();
            }
        });
    }

    private void showRegisterPassengerDialog() {
        JTextField uField = new JTextField(12);
        JPasswordField pField = new JPasswordField("1234", 12);
        JTextField nameField = new JTextField(14);
        JComboBox<PassengerType> typeField = new JComboBox<>(PassengerType.values());
        JTextField phoneField = new JTextField("017", 12);

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("Username:"));
        panel.add(uField);
        panel.add(new JLabel("Password:"));
        panel.add(pField);
        panel.add(new JLabel("Full Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Passenger Type:"));
        panel.add(typeField);
        panel.add(new JLabel("Phone:"));
        panel.add(phoneField);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Register New Passenger Account (Saved to passengers.csv)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            try {
                PassengerAccount created = rideService.registerPassenger(
                        uField.getText(),
                        new String(pField.getPassword()),
                        nameField.getText(),
                        (PassengerType) typeField.getSelectedItem(),
                        phoneField.getText()
                );
                passengerNameField.setText(created.getFullName());
                passengerTypeBox.setSelectedItem(created.getPassengerType());
                message.showSuccess("Registered & logged in as " + created.getFullName() + " (@" + created.getUsername() + ").");
            } catch (RuntimeException ex) {
                message.showError(ex.getMessage());
            }
        }
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

    private void refreshAvailableRickshawsTable() {
        Location from = (Location) pickupBox.getSelectedItem();
        Location to = (Location) destinationBox.getSelectedItem();
        Integer seats = (Integer) seatsBox.getSelectedItem();
        RickshawSorter.SortCriterion sort = (RickshawSorter.SortCriterion) sortCriterionBox.getSelectedItem();
        if (from == null || to == null || seats == null) {
            return;
        }
        availableRickshawsModel.setRowCount(0);
        List<RouteCompatibility.CompatibilityResult> sorted = rideService.findSortedCompatibleRickshaws(
                from.getId(), to.getId(), seats, sort);
        for (RouteCompatibility.CompatibilityResult item : sorted) {
            Rickshaw r = item.getRickshaw();
            availableRickshawsModel.addRow(new Object[]{
                    r.getId(),
                    r.getDriverName(),
                    routeService.getLocationName(r.getLocationId()),
                    r.getAvailableSeats() + "/" + r.getMaxSeats() + " free",
                    Double.isInfinite(item.getDistanceToPickupMeters()) ? "N/A"
                            : String.format(Locale.ROOT, "%.1f", item.getDistanceToPickupMeters()),
                    Double.isInfinite(item.getDetourMeters()) ? "N/A"
                            : String.format(Locale.ROOT, "+%.0f", item.getDetourMeters()),
                    item.getSummary()
            });
        }
        if (availableRickshawsModel.getRowCount() > 0 && availableRickshawsTable.getSelectedRow() < 0) {
            availableRickshawsTable.setRowSelectionInterval(0, 0);
        }
    }

    private void createPassengerRide(String preferredRickshawId) {
        try {
            Location pickup = (Location) pickupBox.getSelectedItem();
            Location dest = (Location) destinationBox.getSelectedItem();
            int seats = (Integer) seatsBox.getSelectedItem();
            String name = passengerNameField.getText();
            RideRequest created = rideService.createRequest(
                    name,
                    (PassengerType) passengerTypeBox.getSelectedItem(),
                    pickup == null ? null : pickup.getId(),
                    dest == null ? null : dest.getId(),
                    seats,
                    preferredRickshawId
            );
            passengerNameField.setText("");
            String targetNote = created.getOfferedRickshawId().isEmpty()
                    ? "Queued for next available rickshaw"
                    : "Sent to " + created.getOfferedRickshawId();
            message.showSuccess(String.format(Locale.ROOT,
                    "Ride #%d requested (%d seat%s, %.1f m)! %s.",
                    created.getId(), created.getSeatsRequested(),
                    created.getSeatsRequested() > 1 ? "s" : "", created.getDistanceMeters(), targetNote));
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
        String trackRickshawId = !ride.getRickshawId().isEmpty() ? ride.getRickshawId() : ride.getOfferedRickshawId();
        mapCanvas.setHighlightedRickshawId(trackRickshawId.isEmpty() ? null : trackRickshawId);

        if (ride.getStatus() == RideStatus.PENDING) {
            mapCanvas.setApproachRoute(List.of());
            String offerText = ride.getOfferedRickshawId().isEmpty()
                    ? "Waiting for any free Rickshaw"
                    : "Offered to " + ride.getOfferedRickshawId() + " (Waiting for Driver Accept)";
            liveTrackingBanner.setText(String.format(Locale.ROOT,
                    "Ride #%d (%s, %d seat): PENDING — %s",
                    ride.getId(), ride.getPassengerName(), ride.getSeatsRequested(), offerText));
            rideProgressBar.setValue(15);
            rideProgressBar.setString("Pending...");
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
            rideProgressBar.setString(distToPickup == 0 ? "At Pickup!" : String.format(Locale.ROOT, "Accepted (%.0fm)", distToPickup));
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
                    "RIDE IN PROGRESS (Started): %s is at %s -> Heading to %s (%.0f m remaining)",
                    ride.getRickshawId(), locName,
                    routeService.getLocationName(ride.getDestinationId()), remDist));
            rideProgressBar.setValue(Math.min(95, Math.max(50, pct)));
            rideProgressBar.setString("Started (" + Math.min(95, Math.max(50, pct)) + "%)");
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
        syncingAccountBox = true;
        try {
            accountSelectorBox.removeAllItems();
            for (PassengerAccount p : rideService.getPassengers()) {
                accountSelectorBox.addItem(p);
            }
            PassengerAccount current = rideService.getLoggedInPassenger();
            if (current != null) {
                accountSelectorBox.setSelectedItem(current);
                loggedInBadge.setText("Active: " + current.getFullName() + " (" + current.getPassengerType() + ")");
            } else {
                loggedInBadge.setText("Active: Guest");
            }
        } finally {
            syncingAccountBox = false;
        }

        refreshAvailableRickshawsTable();

        Integer selectedRideId = null;
        if (ridesTable.getSelectedRow() >= 0) {
            selectedRideId = (Integer) ridesTableModel.getValueAt(ridesTable.getSelectedRow(), 0);
        }

        ridesTableModel.setRowCount(0);
        for (RideRequest r : rideService.getRides()) {
            Rickshaw assigned = r.getRickshawId().isEmpty() ? null : rideService.getRickshaw(r.getRickshawId());
            String driverInfo;
            if (assigned != null) {
                driverInfo = assigned.getId() + " (" + assigned.getDriverName() + ")";
            } else if (!r.getOfferedRickshawId().isEmpty()) {
                driverInfo = "Offered -> " + r.getOfferedRickshawId();
            } else {
                driverInfo = "Waiting...";
            }
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
