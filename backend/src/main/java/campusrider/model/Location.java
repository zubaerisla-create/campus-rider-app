package campusrider.model;

import java.util.List;

/**
 * A place on campus. In the graph, each Location is one vertex.
 *
 * x and y are normalised map coordinates (0.0 - 1.0) used by the web map to
 * draw the vertex. They have no effect on routing: distances come only from
 * the weighted road edges.
 */
public class Location {
    private final String id;
    private final String name;
    private final String category;
    private final boolean rickshawStand;
    private final List<String> aliases;
    private final double x;
    private final double y;

    public Location(String id, String name, String category, boolean rickshawStand, List<String> aliases) {
        this(id, name, category, rickshawStand, aliases, Double.NaN, Double.NaN);
    }

    public Location(String id, String name, String category, boolean rickshawStand, List<String> aliases,
                    double x, double y) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.rickshawStand = rickshawStand;
        this.aliases = List.copyOf(aliases);
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public boolean isRickshawStand() {
        return rickshawStand;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean hasCoordinates() {
        return !Double.isNaN(x) && !Double.isNaN(y);
    }

    public Location withCoordinates(double newX, double newY) {
        return new Location(id, name, category, rickshawStand, aliases, newX, newY);
    }

    @Override
    public String toString() {
        return name;
    }
}
