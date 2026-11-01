package ui;

import dsa.CampusGraph;
import dsa.Edge;
import model.Location;
import model.RideRequest;
import model.Rickshaw;
import service.RideService;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom 2D Campus Map Canvas for Jahangirnagar University (JU).
 * Renders all graph vertices (locations), weighted edges (roads),
 * highlighted Dijkstra shortest routes, and live moving rickshaws with seat badges.
 */
public class CampusMapCanvas extends JPanel {
    private final RideService rideService;

    // Normalized (0.0 to 1.0) 2D coordinates for the 25 JU campus locations
    private final Map<String, double[]> nodeCoords = new HashMap<>();

    private List<String> highlightedRoute = List.of();
    private List<String> approachRoute = List.of();
    private String highlightedRickshawId = null;
    private String pickupLocationId = null;
    private String destinationLocationId = null;

    public CampusMapCanvas(RideService rideService) {
        this.rideService = rideService;
        setPreferredSize(new Dimension(620, 480));
        setBackground(new Color(245, 249, 245));
        initCoordinates();
    }

    private void initCoordinates() {
        // North / North-West / North-East
        nodeCoords.put("bishmile_gate", new double[]{0.50, 0.06});
        nodeCoords.put("old_arts_building", new double[]{0.32, 0.15});
        nodeCoords.put("jahanara_imam_hall", new double[]{0.74, 0.16});
        nodeCoords.put("pritilata_hall", new double[]{0.76, 0.27});
        nodeCoords.put("bottola", new double[]{0.18, 0.25});
        nodeCoords.put("rafiq_jabbar_hall", new double[]{0.12, 0.38});
        nodeCoords.put("fazilatunnesa_hall", new double[]{0.30, 0.30});
        nodeCoords.put("al_beruni_hall", new double[]{0.38, 0.24});

        // Central-North (Halls & Junctions)
        nodeCoords.put("bangabandhu_hall", new double[]{0.25, 0.42});
        nodeCoords.put("salam_barkat_hall", new double[]{0.44, 0.34});
        nodeCoords.put("tarasankar_mor", new double[]{0.36, 0.38});
        nodeCoords.put("chourangi_mor", new double[]{0.54, 0.43});

        // Central Academic & Landmarks
        nodeCoords.put("new_arts_building", new double[]{0.48, 0.54});
        nodeCoords.put("central_library", new double[]{0.62, 0.54});
        nodeCoords.put("cse_building", new double[]{0.82, 0.56});
        nodeCoords.put("shaheed_minar", new double[]{0.50, 0.65});
        nodeCoords.put("gymnasium_field", new double[]{0.72, 0.68});

        // South-Central & West
        nodeCoords.put("social_science_building", new double[]{0.26, 0.66});
        nodeCoords.put("medical_centre", new double[]{0.14, 0.64});
        nodeCoords.put("central_mosque", new double[]{0.12, 0.53});
        nodeCoords.put("central_cafeteria", new double[]{0.36, 0.74});
        nodeCoords.put("zahir_raihan_auditorium", new double[]{0.54, 0.76});

        // South / Gates
        nodeCoords.put("dairy_gate", new double[]{0.38, 0.88});
        nodeCoords.put("prantik_gate", new double[]{0.72, 0.90});
        nodeCoords.put("mmh_hall", new double[]{0.86, 0.84});

        // Fallback coordinates for any custom test locations
        List<Location> all = rideService.getRouteService().getLocationsSorted();
        int idx = 0;
        for (Location loc : all) {
            if (!nodeCoords.containsKey(loc.getId())) {
                double angle = 2 * Math.PI * idx / Math.max(1, all.size());
                nodeCoords.put(loc.getId(), new double[]{
                        0.5 + 0.38 * Math.cos(angle),
                        0.5 + 0.38 * Math.sin(angle)
                });
                idx++;
            }
        }
    }

    public void setHighlightedRoute(List<String> routePath, String pickupId, String destinationId) {
        this.highlightedRoute = routePath == null ? List.of() : List.copyOf(routePath);
        this.pickupLocationId = pickupId;
        this.destinationLocationId = destinationId;
        repaint();
    }

    public void setApproachRoute(List<String> approachPath) {
        this.approachRoute = approachPath == null ? List.of() : List.copyOf(approachPath);
        repaint();
    }

    public void setHighlightedRickshawId(String rickshawId) {
        this.highlightedRickshawId = rickshawId;
        repaint();
    }

    private Point toPixel(String locationId, int w, int h) {
        double[] norm = nodeCoords.getOrDefault(locationId, new double[]{0.5, 0.5});
        int padX = 48;
        int padY = 38;
        int usableW = Math.max(100, w - 2 * padX);
        int usableH = Math.max(100, h - 2 * padY - 26);
        int x = padX + (int) Math.round(norm[0] * usableW);
        int y = padY + (int) Math.round(norm[1] * usableH);
        return new Point(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        drawBackgroundGrid(g2, w, h);
        drawRoads(g2, w, h);
        drawRoutePath(g2, w, h, approachRoute, new Color(230, 126, 34, 210), true);
        drawRoutePath(g2, w, h, highlightedRoute, new Color(22, 160, 133, 230), false);
        drawLocations(g2, w, h);
        drawRickshaws(g2, w, h);
        drawLegend(g2, w, h);

        g2.dispose();
    }

    private void drawBackgroundGrid(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(228, 238, 228));
        g2.setStroke(new BasicStroke(1f));
        for (int x = 40; x < w; x += 60) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 40; y < h; y += 60) {
            g2.drawLine(0, y, w, y);
        }
        // Title watermark
        g2.setColor(new Color(90, 125, 95, 180));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g2.drawString("JU Campus Live Map (Graph + Dijkstra + Live Rickshaw GPS)", 12, 18);
    }

    private void drawRoads(Graphics2D g2, int w, int h) {
        CampusGraph graph = rideService.getRouteService().getGraph();
        Set<String> drawn = new HashSet<>();
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Location loc : graph.getAllLocations()) {
            String u = loc.getId();
            Point p1 = toPixel(u, w, h);
            for (Edge edge : graph.getNeighbors(u)) {
                String v = edge.getToId();
                String key = u.compareTo(v) < 0 ? u + "--" + v : v + "--" + u;
                if (drawn.add(key)) {
                    Point p2 = toPixel(v, w, h);
                    g2.setColor(new Color(185, 198, 190));
                    g2.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
                }
            }
        }
    }

    private void drawRoutePath(Graphics2D g2, int w, int h, List<String> path, Color color, boolean dashed) {
        if (path == null || path.size() < 2) {
            return;
        }
        if (dashed) {
            g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{8f, 6f}, 0f));
        } else {
            g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        g2.setColor(color);
        for (int i = 0; i < path.size() - 1; i++) {
            Point p1 = toPixel(path.get(i), w, h);
            Point p2 = toPixel(path.get(i + 1), w, h);
            g2.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
        }
    }

    private void drawLocations(Graphics2D g2, int w, int h) {
        CampusGraph graph = rideService.getRouteService().getGraph();
        Set<String> onRoute = new HashSet<>(highlightedRoute);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        FontMetrics fm = g2.getFontMetrics();

        for (Location loc : graph.getAllLocations()) {
            String id = loc.getId();
            Point p = toPixel(id, w, h);
            boolean isPickup = id.equals(pickupLocationId);
            boolean isDest = id.equals(destinationLocationId);
            boolean inPath = onRoute.contains(id);

            int r = (isPickup || isDest) ? 8 : (inPath ? 6 : 5);
            Color fill;
            if (isPickup) {
                fill = new Color(41, 128, 185); // Blue pickup
            } else if (isDest) {
                fill = new Color(192, 57, 43);  // Red destination
            } else if (inPath) {
                fill = new Color(22, 160, 133); // Teal path node
            } else if (loc.isRickshawStand()) {
                fill = new Color(52, 73, 94);
            } else {
                fill = new Color(127, 140, 141);
            }

            if (isPickup || isDest) {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 65));
                g2.fill(new Ellipse2D.Double(p.x - 13, p.y - 13, 26, 26));
            }

            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(p.x - r - 1, p.y - r - 1, 2 * r + 2, 2 * r + 2));
            g2.setColor(fill);
            g2.fill(new Ellipse2D.Double(p.x - r, p.y - r, 2 * r, 2 * r));

            String shortLabel = shortName(loc.getName());
            int tw = fm.stringWidth(shortLabel);
            g2.setColor(new Color(255, 255, 255, 210));
            g2.fillRoundRect(p.x - tw / 2 - 2, p.y + r + 1, tw + 4, 12, 4, 4);
            g2.setColor((isPickup || isDest || inPath) ? new Color(20, 30, 40) : new Color(75, 85, 95));
            g2.drawString(shortLabel, p.x - tw / 2, p.y + r + 10);
        }
    }

    private void drawRickshaws(Graphics2D g2, int w, int h) {
        List<Rickshaw> rickshaws = rideService.getRickshaws();
        // Track slight offset when multiple rickshaws stand at the same node
        Map<String, Integer> nodeCount = new HashMap<>();

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();

        for (Rickshaw r : rickshaws) {
            double rx;
            double ry;
            if (r.isMoving()) {
                Point pFrom = toPixel(r.getMovingFromId(), w, h);
                Point pTo = toPixel(r.getMovingToId(), w, h);
                double t = r.getSegmentProgress();
                rx = pFrom.x + (pTo.x - pFrom.x) * t;
                ry = pFrom.y + (pTo.y - pFrom.y) * t;
            } else {
                Point p = toPixel(r.getLocationId(), w, h);
                int idx = nodeCount.merge(r.getLocationId(), 1, Integer::sum) - 1;
                rx = p.x + (idx * 18) - 8;
                ry = p.y - 16 - (idx * 14);
            }

            boolean isSelected = r.getId().equals(highlightedRickshawId);
            Color badgeColor;
            if (r.getOccupiedSeats() == 0) {
                badgeColor = new Color(39, 174, 96); // Free (3/3 seats free)
            } else if (r.getAvailableSeats() > 0) {
                badgeColor = new Color(230, 126, 34); // Shared ride (1 or 2 seats free)
            } else {
                badgeColor = new Color(192, 57, 43); // Full (0 seats free)
            }

            String code = r.getId().replace("JU-RICK-", "R");
            String tag = code + " [" + r.getAvailableSeats() + "/" + r.getMaxSeats() + " free]";
            int bw = fm.stringWidth(tag) + 10;
            int bh = 18;
            double bx = rx - bw / 2.0;
            double by = ry - bh / 2.0;

            if (isSelected) {
                g2.setColor(new Color(241, 196, 15, 180));
                g2.fill(new RoundRectangle2D.Double(bx - 3, by - 3, bw + 6, bh + 6, 10, 10));
            }

            g2.setColor(badgeColor);
            g2.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 8, 8));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 8, 8));
            g2.drawString(tag, (int) Math.round(bx + 5), (int) Math.round(by + 13));
        }
    }

    private void drawLegend(Graphics2D g2, int w, int h) {
        int y = h - 10;
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        int x = 12;

        x = drawLegendDot(g2, x, y, new Color(39, 174, 96), "Rickshaw (3/3 Free)");
        x = drawLegendDot(g2, x, y, new Color(230, 126, 34), "Shared (1-2 Free)");
        x = drawLegendDot(g2, x, y, new Color(192, 57, 43), "Full (0 Free)");
        x = drawLegendDot(g2, x, y, new Color(41, 128, 185), "Pickup");
        drawLegendDot(g2, x, y, new Color(22, 160, 133), "Dijkstra Shortest Route");
    }

    private int drawLegendDot(Graphics2D g2, int x, int y, Color c, String text) {
        g2.setColor(c);
        g2.fillOval(x, y - 9, 10, 10);
        g2.setColor(new Color(50, 60, 70));
        g2.drawString(text, x + 14, y);
        return x + 18 + g2.getFontMetrics().stringWidth(text) + 12;
    }

    private static String shortName(String name) {
        int paren = name.indexOf('(');
        String clean = paren > 0 ? name.substring(0, paren).trim() : name;
        if (clean.length() > 18) {
            return clean.substring(0, 16) + "..";
        }
        return clean;
    }
}
