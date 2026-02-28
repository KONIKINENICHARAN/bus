import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class BusBookingApp extends JFrame {

    // ───────── colours & fonts ─────────
    private static final Color PRIMARY      = new Color(33, 150, 243);
    private static final Color PRIMARY_DARK = new Color(21, 101, 192);
    private static final Color ACCENT       = new Color(255, 152, 0);
    private static final Color BG           = new Color(245, 245, 245);
    private static final Color CARD_BG      = Color.WHITE;
    private static final Color SEAT_FREE    = new Color(76, 175, 80);
    private static final Color SEAT_BOOKED  = new Color(244, 67, 54);
    private static final Color SEAT_SELECTED= new Color(255, 193, 7);
    private static final Color TEXT_DARK    = new Color(33, 33, 33);
    private static final Color TEXT_LIGHT   = Color.WHITE;
    private static final Font  TITLE_FONT  = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font  LABEL_FONT  = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font  BTN_FONT    = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font  SEAT_FONT   = new Font("Segoe UI", Font.BOLD, 12);

    // ───────── state ─────────
    private CardLayout cardLayout;
    private JPanel     mainPanel;

    // Route-selection panel
    private JComboBox<String> fromCombo, toCombo;

    // Bus-listing panel
    private JPanel busListPanel;

    // Seat-layout panel
    private int selectedScheduleId = -1;
    private int selectedBusId      = -1;
    private int selectedSeatNo     = -1;
    private JPanel seatGrid;
    private JLabel seatInfoLabel;

    // ─────────────────────────────────────────────────────────────
    public BusBookingApp() {
        super("🚌  Bus Ticket Booking System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 680);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        cardLayout = new CardLayout();
        mainPanel  = new JPanel(cardLayout);
        mainPanel.setBackground(BG);

        mainPanel.add(buildRoutePanel(),   "ROUTE");
        mainPanel.add(buildBusListPanel(), "BUSES");
        mainPanel.add(buildSeatPanel(),    "SEATS");

        add(mainPanel);
        cardLayout.show(mainPanel, "ROUTE");
        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  PANEL 1 – Route Selection
    // ══════════════════════════════════════════════════════════════
    private JPanel buildRoutePanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BG);

        JPanel card = createCard(460, 380);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // title
        JLabel title = centeredLabel("Select Your Route", TITLE_FONT, PRIMARY_DARK);
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 25, 0));
        card.add(title);

        // from
        card.add(fieldLabel("From"));
        fromCombo = styledCombo();
        card.add(wrapCenter(fromCombo, 320, 38));
        card.add(Box.createVerticalStrut(18));

        // to
        card.add(fieldLabel("To"));
        toCombo = styledCombo();
        card.add(wrapCenter(toCombo, 320, 38));
        card.add(Box.createVerticalStrut(30));

        // search button
        JButton search = styledButton("Search Buses", PRIMARY, TEXT_LIGHT);
        search.addActionListener(e -> onSearchBuses());
        card.add(wrapCenter(search, 220, 42));

        card.add(Box.createVerticalStrut(15));
        populateRouteDropdowns();

        outer.add(card);
        return outer;
    }

    private void populateRouteDropdowns() {
        try (Connection c = DBConnection.getConnection();
             Statement  s = c.createStatement()) {
            Set<String> froms = new LinkedHashSet<>(), tos = new LinkedHashSet<>();
            ResultSet r = s.executeQuery(
                    "SELECT DISTINCT FROM_ADDRESS, TO_ADDRESS FROM BUS_SCHEDULE ORDER BY FROM_ADDRESS");
            while (r.next()) {
                froms.add(r.getString("FROM_ADDRESS"));
                tos.add(r.getString("TO_ADDRESS"));
            }
            froms.forEach(fromCombo::addItem);
            tos.forEach(toCombo::addItem);
        } catch (Exception ex) { showError("Failed to load routes: " + ex.getMessage()); }
    }

    private void onSearchBuses() {
        String from = (String) fromCombo.getSelectedItem();
        String to   = (String) toCombo.getSelectedItem();
        if (from == null || to == null) { showError("Select both addresses"); return; }

        busListPanel.removeAll();
        busListPanel.setLayout(new BoxLayout(busListPanel, BoxLayout.Y_AXIS));

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT SCHEDULE_ID, BUS_ID, FROM_ADDRESS, TO_ADDRESS, JOURNEY_DATE " +
                     "FROM BUS_SCHEDULE WHERE UPPER(FROM_ADDRESS)=UPPER(?) AND UPPER(TO_ADDRESS)=UPPER(?)")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ResultSet r = ps.executeQuery();
            boolean found = false;
            while (r.next()) {
                found = true;
                int schedId  = r.getInt("SCHEDULE_ID");
                int busId    = r.getInt("BUS_ID");
                String route = r.getString("FROM_ADDRESS") + "  →  " + r.getString("TO_ADDRESS");
                String date  = r.getString("JOURNEY_DATE").substring(0, 10);

                // count booked seats
                int booked = countBookedSeats(schedId);

                JPanel busCard = createCard(650, 90);
                busCard.setLayout(new BorderLayout(15, 0));
                busCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(6, 0, 6, 0),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                                BorderFactory.createEmptyBorder(12, 18, 12, 18))));
                busCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                // Bus icon on the left
                JLabel busIcon = new JLabel(new BusIcon(56, 40));
                busIcon.setPreferredSize(new Dimension(64, 48));
                JPanel iconWrap = new JPanel(new GridBagLayout());
                iconWrap.setOpaque(false);
                iconWrap.add(busIcon);
                busCard.add(iconWrap, BorderLayout.WEST);

                JPanel infoPanel = new JPanel(new GridLayout(2, 1));
                infoPanel.setOpaque(false);
                infoPanel.add(boldLabel("Bus #" + busId + "   |   " + route, 15, TEXT_DARK));
                infoPanel.add(boldLabel("Date: " + date + "   |   Seats Available: " + (30 - booked) + "/30",
                        12, new Color(100, 100, 100)));
                busCard.add(infoPanel, BorderLayout.CENTER);

                JButton selectBtn = styledButton("Select", PRIMARY, TEXT_LIGHT);
                selectBtn.setPreferredSize(new Dimension(100, 36));
                selectBtn.addActionListener(ev -> onBusSelected(schedId, busId));
                JPanel btnWrap = new JPanel(new GridBagLayout());
                btnWrap.setOpaque(false);
                btnWrap.add(selectBtn);
                busCard.add(btnWrap, BorderLayout.EAST);

                busListPanel.add(Box.createVerticalStrut(6));
                busListPanel.add(busCard);
            }
            if (!found) {
                busListPanel.add(centeredLabel("No buses found for this route.", LABEL_FONT, SEAT_BOOKED));
            }
        } catch (Exception ex) { showError("Search failed: " + ex.getMessage()); ex.printStackTrace(); }

        busListPanel.revalidate();
        busListPanel.repaint();
        cardLayout.show(mainPanel, "BUSES");
    }

    // ══════════════════════════════════════════════════════════════
    //  PANEL 2 – Bus Listing
    // ══════════════════════════════════════════════════════════════
    private JPanel buildBusListPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);

        // header
        JPanel header = headerBar("Available Buses", () -> cardLayout.show(mainPanel, "ROUTE"));
        outer.add(header, BorderLayout.NORTH);

        busListPanel = new JPanel();
        busListPanel.setBackground(BG);
        JScrollPane scroll = new JScrollPane(busListPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private void onBusSelected(int schedId, int busId) {
        selectedScheduleId = schedId;
        selectedBusId      = busId;
        selectedSeatNo     = -1;
        refreshSeatLayout();
        cardLayout.show(mainPanel, "SEATS");
    }

    // ══════════════════════════════════════════════════════════════
    //  PANEL 3 – Seat Layout
    // ══════════════════════════════════════════════════════════════
    private JPanel buildSeatPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG);

        JPanel header = headerBar("Select Your Seat", () -> cardLayout.show(mainPanel, "BUSES"));
        outer.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(BG);

        JPanel busBody = new JPanel();
        busBody.setLayout(new BoxLayout(busBody, BoxLayout.Y_AXIS));
        busBody.setBackground(CARD_BG);
        busBody.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(16, new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(15, 20, 15, 20)));

        // driver area
        JPanel driverRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        driverRow.setOpaque(false);
        JLabel driverIcon = new JLabel("  🚗  Driver");
        driverIcon.setFont(new Font("Segoe UI", Font.BOLD, 13));
        driverIcon.setForeground(new Color(120, 120, 120));
        driverRow.add(driverIcon);
        busBody.add(driverRow);
        busBody.add(Box.createVerticalStrut(8));

        // separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        busBody.add(sep);
        busBody.add(Box.createVerticalStrut(12));

        // seat grid: 8 rows x 4 cols with aisle gap
        seatGrid = new JPanel(new GridLayout(8, 5, 6, 6)); // 5 cols: 2 seats, aisle, 2 seats
        seatGrid.setOpaque(false);
        busBody.add(seatGrid);

        busBody.add(Box.createVerticalStrut(15));

        // legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        legend.setOpaque(false);
        legend.add(legendItem(SEAT_FREE,    "Available"));
        legend.add(legendItem(SEAT_BOOKED,  "Booked"));
        legend.add(legendItem(SEAT_SELECTED,"Selected"));
        busBody.add(legend);

        busBody.add(Box.createVerticalStrut(12));

        // info + book button
        seatInfoLabel = new JLabel("Click a green seat to select it");
        seatInfoLabel.setFont(LABEL_FONT);
        seatInfoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        busBody.add(seatInfoLabel);
        busBody.add(Box.createVerticalStrut(10));

        JButton bookBtn = styledButton("Book Selected Seat", ACCENT, TEXT_DARK);
        bookBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        bookBtn.addActionListener(e -> onBookSeat());
        busBody.add(wrapCenter(bookBtn, 220, 42));

        center.add(busBody);
        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    private void refreshSeatLayout() {
        seatGrid.removeAll();
        Set<Integer> booked = getBookedSeats(selectedScheduleId);
        selectedSeatNo = -1;
        seatInfoLabel.setText("Click a green seat to select it");

        int seatNum = 1;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 5; col++) {
                if (col == 2) {
                    // aisle
                    JLabel aisle = new JLabel("");
                    aisle.setPreferredSize(new Dimension(20, 40));
                    seatGrid.add(aisle);
                    continue;
                }
                final int seat = seatNum;
                JButton btn = new JButton(String.valueOf(seat));
                btn.setFont(SEAT_FONT);
                btn.setPreferredSize(new Dimension(55, 40));
                btn.setFocusPainted(false);
                btn.setBorderPainted(false);
                btn.setOpaque(true);

                if (booked.contains(seat)) {
                    btn.setBackground(SEAT_BOOKED);
                    btn.setForeground(TEXT_LIGHT);
                    btn.setToolTipText("Seat " + seat + " – Booked");
                    btn.setEnabled(false);
                } else {
                    btn.setBackground(SEAT_FREE);
                    btn.setForeground(TEXT_LIGHT);
                    btn.setToolTipText("Seat " + seat + " – Available");
                    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    btn.addActionListener(ev -> {
                        selectedSeatNo = seat;
                        seatInfoLabel.setText("Selected Seat: " + seat);
                        highlightSelection();
                    });
                }
                seatGrid.add(btn);
                seatNum++;
            }
        }
        // last row: 2 extra seats at back (seats 33, 34 — optional; total = 32 seats)
        seatGrid.revalidate();
        seatGrid.repaint();
    }

    private void highlightSelection() {
        Set<Integer> booked = getBookedSeats(selectedScheduleId);
        int seatNum = 1;
        for (Component comp : seatGrid.getComponents()) {
            if (comp instanceof JButton) {
                JButton btn = (JButton) comp;
                int s = seatNum;
                seatNum++;
                if (booked.contains(s)) continue;
                if (s == selectedSeatNo) {
                    btn.setBackground(SEAT_SELECTED);
                    btn.setForeground(TEXT_DARK);
                } else {
                    btn.setBackground(SEAT_FREE);
                    btn.setForeground(TEXT_LIGHT);
                }
            }
        }
    }

    private void onBookSeat() {
        if (selectedSeatNo == -1) { showError("Please select a seat first."); return; }

        // double-booking check
        if (getBookedSeats(selectedScheduleId).contains(selectedSeatNo)) {
            showError("Seat " + selectedSeatNo + " is already booked! Please choose another.");
            refreshSeatLayout();
            return;
        }
        showPassengerForm();
    }

    // ══════════════════════════════════════════════════════════════
    //  Passenger Form Dialog
    // ══════════════════════════════════════════════════════════════
    private void showPassengerForm() {
        JDialog dlg = new JDialog(this, "Passenger Details", true);
        dlg.setSize(420, 380);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.getContentPane().setBackground(CARD_BG);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CARD_BG);
        form.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill   = GridBagConstraints.HORIZONTAL;

        // Title
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        JLabel ttl = new JLabel("Enter Passenger Details");
        ttl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        ttl.setForeground(PRIMARY_DARK);
        form.add(ttl, g);

        g.gridwidth = 1;

        // Seat info
        g.gridy = 1; g.gridx = 0; g.gridwidth = 2;
        JLabel seatLbl = new JLabel("Bus #" + selectedBusId + "  |  Seat: " + selectedSeatNo);
        seatLbl.setFont(LABEL_FONT);
        seatLbl.setForeground(new Color(100, 100, 100));
        form.add(seatLbl, g);

        g.gridwidth = 1;

        // Passenger ID (auto-generated)
        int nextPassengerId = getNextPassengerId();
        g.gridy = 2; g.gridx = 0;
        form.add(formLabel("Passenger ID:"), g);
        g.gridx = 1;
        JTextField pidField = new JTextField(String.valueOf(nextPassengerId), 15);
        pidField.setFont(LABEL_FONT);
        pidField.setEditable(false);
        pidField.setBackground(new Color(230, 230, 230));
        form.add(pidField, g);

        // Name
        g.gridy = 3; g.gridx = 0;
        form.add(formLabel("Name:"), g);
        g.gridx = 1;
        JTextField nameField = new JTextField(15);
        nameField.setFont(LABEL_FONT);
        form.add(nameField, g);

        // Age
        g.gridy = 4; g.gridx = 0;
        form.add(formLabel("Age:"), g);
        g.gridx = 1;
        JTextField ageField = new JTextField(15);
        ageField.setFont(LABEL_FONT);
        form.add(ageField, g);

        // Gender
        g.gridy = 5; g.gridx = 0;
        form.add(formLabel("Gender:"), g);
        g.gridx = 1;
        JComboBox<String> genderCombo = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        genderCombo.setFont(LABEL_FONT);
        form.add(genderCombo, g);

        // Buttons
        g.gridy = 6; g.gridx = 0; g.gridwidth = 2;
        g.anchor = GridBagConstraints.CENTER;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);

        JButton confirmBtn = styledButton("Confirm Booking", PRIMARY, TEXT_LIGHT);
        JButton cancelBtn  = styledButton("Cancel", SEAT_BOOKED, TEXT_LIGHT);

        cancelBtn.addActionListener(e -> dlg.dispose());
        confirmBtn.addActionListener(e -> {
            String pidStr = pidField.getText().trim();
            String name   = nameField.getText().trim();
            String ageStr = ageField.getText().trim();
            String gender = (String) genderCombo.getSelectedItem();

            if (pidStr.isEmpty() || name.isEmpty() || ageStr.isEmpty()) {
                showError("All fields are required.");
                return;
            }

            int passengerId, age;
            try {
                passengerId = Integer.parseInt(pidStr);
                age = Integer.parseInt(ageStr);
            } catch (NumberFormatException ex) {
                showError("Passenger ID and Age must be numbers.");
                return;
            }

            // Attempt booking with double-booking guard
            if (bookSeat(passengerId, name, age, gender)) {
                dlg.dispose();
                JOptionPane.showMessageDialog(this,
                        " Booking Confirmed!\n\n" +
                        "Bus #" + selectedBusId + "\n" +
                        "Seat: " + selectedSeatNo + "\n" +
                        "Passenger: " + name + "\n" +
                        "Passenger ID: " + passengerId,
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshSeatLayout();
            }
        });

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);
        form.add(btnPanel, g);

        dlg.add(form);
        dlg.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  Database Operations
    // ══════════════════════════════════════════════════════════════

    /** Book a seat – inserts into PASSENGERS and SEAT_BOOKINGS with double-booking prevention */
    private boolean bookSeat(int passengerId, String name, int age, String gender) {
        Connection c = null;
        try {
            c = DBConnection.getConnection();
            c.setAutoCommit(false);

            // ── DOUBLE-BOOKING CHECK (with row-level lock) ──
            PreparedStatement checkPs = c.prepareStatement(
                    "SELECT COUNT(*) FROM SEAT_BOOKINGS WHERE SCHEDULE_ID=? AND SEAT_NO=?");
            checkPs.setInt(1, selectedScheduleId);
            checkPs.setInt(2, selectedSeatNo);
            ResultSet checkRs = checkPs.executeQuery();
            checkRs.next();
            if (checkRs.getInt(1) > 0) {
                c.rollback();
                showError("Seat " + selectedSeatNo + " was just booked by someone else!\nPlease choose another seat.");
                refreshSeatLayout();
                return false;
            }

            // ── INSERT PASSENGER ──
            PreparedStatement pasPs = c.prepareStatement(
                    "INSERT INTO PASSENGERS (PASSENGER_ID, NAME, AGE, GENDER) VALUES (?, ?, ?, ?)");
            pasPs.setInt(1, passengerId);
            pasPs.setString(2, name);
            pasPs.setInt(3, age);
            pasPs.setString(4, gender);
            pasPs.executeUpdate();

            // ── INSERT SEAT BOOKING ──
            PreparedStatement bookPs = c.prepareStatement(
                    "INSERT INTO SEAT_BOOKINGS (SCHEDULE_ID, SEAT_NO, PASSENGER_ID) VALUES (?, ?, ?)");
            bookPs.setInt(1, selectedScheduleId);
            bookPs.setInt(2, selectedSeatNo);
            bookPs.setInt(3, passengerId);
            bookPs.executeUpdate();

            c.commit();
            return true;

        } catch (SQLException ex) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            if (ex.getErrorCode() == 1) { // ORA-00001 unique constraint
                showError("Duplicate entry! Passenger ID or seat already exists.");
            } else {
                showError("Booking failed: " + ex.getMessage());
            }
            ex.printStackTrace();
            return false;
        } finally {
            try { if (c != null) { c.setAutoCommit(true); c.close(); } } catch (Exception ignored) {}
        }
    }

    /** Get set of booked seat numbers for a schedule */
    private Set<Integer> getBookedSeats(int scheduleId) {
        Set<Integer> seats = new HashSet<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT SEAT_NO FROM SEAT_BOOKINGS WHERE SCHEDULE_ID=?")) {
            ps.setInt(1, scheduleId);
            ResultSet r = ps.executeQuery();
            while (r.next()) seats.add(r.getInt("SEAT_NO"));
        } catch (Exception ex) { ex.printStackTrace(); }
        return seats;
    }

    /** Get next auto-incremented passenger ID */
    private int getNextPassengerId() {
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT NVL(MAX(PASSENGER_ID),0)+1 FROM PASSENGERS")) {
            r.next();
            return r.getInt(1);
        } catch (Exception ex) { ex.printStackTrace(); return 1; }
    }

    /** Count booked seats */
    private int countBookedSeats(int scheduleId) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM SEAT_BOOKINGS WHERE SCHEDULE_ID=?")) {
            ps.setInt(1, scheduleId);
            ResultSet r = ps.executeQuery();
            r.next();
            return r.getInt(1);
        } catch (Exception ex) { return 0; }
    }

    // ══════════════════════════════════════════════════════════════
    //  UI Helpers
    // ══════════════════════════════════════════════════════════════

    private JPanel headerBar(String title, Runnable onBack) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(PRIMARY_DARK);
        bar.setPreferredSize(new Dimension(0, 52));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        JButton back = new JButton("← Back");
        back.setFont(BTN_FONT);
        back.setForeground(TEXT_LIGHT);
        back.setBackground(PRIMARY_DARK);
        back.setBorderPainted(false);
        back.setFocusPainted(false);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> onBack.run());
        bar.add(back, BorderLayout.WEST);

        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lbl.setForeground(TEXT_LIGHT);
        bar.add(lbl, BorderLayout.CENTER);

        return bar;
    }

    private JPanel createCard(int w, int h) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setPreferredSize(new Dimension(w, h));
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(12, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        return card;
    }

    private JLabel centeredLabel(String text, Font font, Color fg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(font);
        l.setForeground(fg);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(LABEL_FONT);
        l.setForeground(TEXT_DARK);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 70, 4, 0));
        return l;
    }

    private JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(LABEL_FONT);
        l.setForeground(TEXT_DARK);
        return l;
    }

    private JLabel boldLabel(String text, int size, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, size));
        l.setForeground(fg);
        return l;
    }

    private JComboBox<String> styledCombo() {
        JComboBox<String> cb = new JComboBox<>();
        cb.setFont(LABEL_FONT);
        cb.setBackground(Color.WHITE);
        cb.setMaximumSize(new Dimension(320, 38));
        return cb;
    }

    private JButton styledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(BTN_FONT);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // hover effect
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bg.darker()); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    private JPanel wrapCenter(JComponent comp, int w, int h) {
        comp.setPreferredSize(new Dimension(w, h));
        comp.setMaximumSize(new Dimension(w, h));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrap.setOpaque(false);
        wrap.add(comp);
        return wrap;
    }

    private JPanel legendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);
        JPanel swatch = new JPanel();
        swatch.setBackground(color);
        swatch.setPreferredSize(new Dimension(18, 18));
        item.add(swatch);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        item.add(lbl);
        return item;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ────── Bus icon (drawn with Java2D) ──────
    static class BusIcon implements Icon {
        private final int w, h;
        BusIcon(int w, int h) { this.w = w; this.h = h; }
        public int getIconWidth()  { return w; }
        public int getIconHeight() { return h; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bx = x + 2, by = y + 4, bw = w - 4, bh = h - 14;
            // body
            g2.setColor(new Color(33, 150, 243));
            g2.fillRoundRect(bx, by, bw, bh, 8, 8);
            // roof stripe
            g2.setColor(new Color(21, 101, 192));
            g2.fillRoundRect(bx, by, bw, 8, 8, 8);
            // windows
            g2.setColor(new Color(187, 222, 251));
            int winY = by + 10, winH = 8, gap = 3;
            int winW = (bw - 10 - gap * 3) / 4;
            for (int i = 0; i < 4; i++) {
                g2.fillRoundRect(bx + 4 + i * (winW + gap), winY, winW, winH, 3, 3);
            }
            // windshield (front)
            g2.setColor(new Color(144, 202, 249));
            g2.fillRoundRect(bx + bw - 14, by + 3, 12, bh - 6, 4, 4);
            // wheels
            g2.setColor(new Color(55, 55, 55));
            int wheelY = by + bh - 3, wheelR = 7;
            g2.fillOval(bx + 8, wheelY, wheelR, wheelR);
            g2.fillOval(bx + bw - 16, wheelY, wheelR, wheelR);
            // wheel hub
            g2.setColor(new Color(200, 200, 200));
            g2.fillOval(bx + 10, wheelY + 2, 3, 3);
            g2.fillOval(bx + bw - 14, wheelY + 2, 3, 3);
            // headlight
            g2.setColor(new Color(255, 235, 59));
            g2.fillOval(bx + bw - 5, by + bh - 8, 5, 5);
            g2.dispose();
        }
    }

    // ────── Rounded border ──────
    static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        RoundedBorder(int r, Color c) { radius = r; color = c; }
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }
        public Insets getBorderInsets(Component c) { return new Insets(4, 4, 4, 4); }
    }

    // ══════════════════════════════════════════════════════════════
    //  Main
    // ══════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(BusBookingApp::new);
    }
}
