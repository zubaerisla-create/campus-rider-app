package ui;

import dsa.QueuePolicy;
import model.Location;
import model.PassengerType;
import model.RideRequest;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.function.Supplier;

/** Tab 2: create ride requests, manage the queue, match rickshaws, and update ride status. */
public class RidePanel extends JPanel {
    private static final String[] RIDE_COLUMNS =
            {"ID", "Passenger", "Type", "Pickup", "Destination", "Distance (m)", "Status", "Rickshaw"};
    private static final String[] RICKSHAW_COLUMNS = {"ID", "Driver", "Current Location", "Status"};

    private final RideService rideService;
    private final RouteService routeService;

    // Package-private so the UI tests can drive the panel.
    final JTextField nameField = new JTextField(15);
    final JComboBox<PassengerType> typeBox = new JComboBox<>(PassengerType.values());
    final JComboBox<Location> pickupBox;
    final JComboBox<Location> destinationBox;
    final JButton createButton = new JButton("Create Ride Request");

    final JComboBox<QueuePolicy> policyBox = new JComboBox<>(QueuePolicy.values());
    final JLabel policyLabel = new JLabel();
    final DefaultListModel<String> queueModel = new DefaultListModel<>();
    final JButton matchButton = new JButton("Match Next Request");

    final DefaultTableModel ridesModel = readOnlyModel(RIDE_COLUMNS);
    final JTable ridesTable = new JTable(ridesModel);
    final JButton startButton = new JButton("Start Ride");
    final JButton completeButton = new JButton("Complete Ride");
    final JButton cancelButton = new JButton("Cancel Ride");

    final DefaultTableModel rickshawsModel = readOnlyModel(RICKSHAW_COLUMNS);
    final JTable rickshawsTable = new JTable(rickshawsModel);
    final JLabel rickshawCountLabel = new JLabel();

    final MessageLabel message = new MessageLabel();

    public RidePanel(RideService rideService) {
        super(new BorderLayout(10, 10));
        this.rideService = rideService;
        this.routeService = rideService.getRouteService();
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Location[] locations = routeService.getLocationsSorted().toArray(new Location[0]);
        pickupBox = new JComboBox<>(locations);
        destinationBox = new JComboBox<>(locations);
        if (locations.length > 1) {
            destinationBox.setSelectedIndex(1);
        }
        policyBox.setSelectedItem(rideService.getQueuePolicy());

        JPanel top = new JPanel(new GridLayout(1, 2, 10, 10));
        top.add(buildRequestForm());
        top.add(buildQueuePanel());

        JSplitPane tables = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildRidesPanel(), buildRickshawsPanel());
        tables.setResizeWeight(0.65);

        add(top, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);

        createButton.addActionListener(e -> run(this::createRequest));
        matchButton.addActionListener(e -> run(rideService::matchNextRequest));
        startButton.addActionListener(e -> run(() -> rideService.startRide(selectedRideId())));
        completeButton.addActionListener(e -> run(() -> rideService.completeRide(selectedRideId())));
        cancelButton.addActionListener(e -> run(() -> rideService.cancelRide(selectedRideId())));
        policyBox.addActionListener(e -> run(this::changePolicy));

        rideService.addChangeListener(this::refresh);
        refresh();
    }

    // ------------------------------------------------------------ layout

    private JPanel buildRequestForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("New Ride Request"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, c, 0, "Passenger name:", nameField);
        addRow(form, c, 1, "Passenger type:", typeBox);
        addRow(form, c, 2, "Pickup:", pickupBox);
        addRow(form, c, 3, "Destination:", destinationBox);
        c.gridx = 1;
        c.gridy = 4;
        form.add(createButton, c);
        return form;
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private JPanel buildQueuePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Pending Requests Queue"));

        JPanel policyRow = new JPanel(new BorderLayout(5, 5));
        JPanel chooser = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chooser.add(new JLabel("Queue policy: "));
        chooser.add(policyBox);
        policyRow.add(chooser, BorderLayout.NORTH);
        policyRow.add(policyLabel, BorderLayout.SOUTH);

        JList<String> queueList = new JList<>(queueModel);
        JScrollPane scroll = new JScrollPane(queueList);
        scroll.setPreferredSize(new Dimension(300, 110));

        panel.add(policyRow, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(matchButton, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildRidesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Ride Requests and History"));
        ridesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(new JLabel("Selected ride:"));
        buttons.add(startButton);
        buttons.add(completeButton);
        buttons.add(cancelButton);

        panel.add(new JScrollPane(ridesTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildRickshawsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Rickshaws"));
        panel.add(rickshawCountLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(rickshawsTable), BorderLayout.CENTER);
        return panel;
    }

    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ----------------------------------------------------------- actions

    /** Runs one action, shows its result or error, and redraws the tables. */
    private void run(Supplier<String> action) {
        try {
            message.showSuccess(action.get());
        } catch (RuntimeException e) {
            message.showError(e.getMessage());
        }
        refresh();
    }

    private String createRequest() {
        Location pickup = (Location) pickupBox.getSelectedItem();
        Location destination = (Location) destinationBox.getSelectedItem();
        RideRequest ride = rideService.createRequest(nameField.getText(), (PassengerType) typeBox.getSelectedItem(),
                pickup == null ? null : pickup.getId(), destination == null ? null : destination.getId());
        nameField.setText("");
        return String.format(Locale.ROOT, "Request #%d created for %s (%.1f m). It is waiting in the queue.",
                ride.getId(), ride.getPassengerName(), ride.getDistanceMeters());
    }

    private String changePolicy() {
        QueuePolicy policy = (QueuePolicy) policyBox.getSelectedItem();
        rideService.setQueuePolicy(policy);
        return "Queue policy is now " + policy + ": " + policy.getDescription() + ".";
    }

    /** Id of the ride selected in the table. */
    private int selectedRideId() {
        int row = ridesTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalStateException("Select a ride in the table first.");
        }
        return (Integer) ridesModel.getValueAt(row, 0);
    }

    /** Reloads every table and list from the service, keeping the selected ride selected. */
    void refresh() {
        Integer selectedId = null;
        if (ridesTable.getSelectedRow() >= 0) {
            selectedId = (Integer) ridesModel.getValueAt(ridesTable.getSelectedRow(), 0);
        }

        ridesModel.setRowCount(0);
        for (RideRequest r : rideService.getRides()) {
            ridesModel.addRow(new Object[]{r.getId(), r.getPassengerName(), r.getPassengerType(),
                    routeService.getLocationName(r.getPickupId()), routeService.getLocationName(r.getDestinationId()),
                    String.format(Locale.ROOT, "%.1f", r.getDistanceMeters()), r.getStatus(), r.getRickshawId()});
        }
        if (selectedId != null) {
            for (int row = 0; row < ridesModel.getRowCount(); row++) {
                if (selectedId.equals(ridesModel.getValueAt(row, 0))) {
                    ridesTable.setRowSelectionInterval(row, row);
                }
            }
        }

        rickshawsModel.setRowCount(0);
        for (Rickshaw r : rideService.getRickshaws()) {
            rickshawsModel.addRow(new Object[]{r.getId(), r.getDriverName(),
                    routeService.getLocationName(r.getLocationId()), r.isAvailable() ? "AVAILABLE" : "BUSY"});
        }
        rickshawCountLabel.setText(rideService.countAvailableRickshaws() + " of "
                + rideService.getRickshaws().size() + " rickshaws available");

        queueModel.clear();
        int position = 1;
        for (RideRequest r : rideService.getPendingInOrder()) {
            queueModel.addElement(position + ". #" + r.getId() + " " + r.getPassengerName() + " ("
                    + r.getPassengerType() + ") at " + routeService.getLocationName(r.getPickupId()));
            position++;
        }
        if (queueModel.isEmpty()) {
            queueModel.addElement("(no pending requests)");
        }
        policyLabel.setText("Active: " + rideService.getQueuePolicy().getDescription());
    }
}
