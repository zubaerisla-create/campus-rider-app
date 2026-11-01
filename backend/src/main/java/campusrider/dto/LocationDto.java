package campusrider.dto;

import campusrider.model.Location;

import java.util.List;

public record LocationDto(String id, String name, String category, boolean rickshawStand,
                          List<String> aliases, double x, double y, boolean reachable) {
    public static LocationDto of(Location l, boolean reachable) {
        return new LocationDto(l.getId(), l.getName(), l.getCategory(), l.isRickshawStand(), l.getAliases(),
                l.hasCoordinates() ? l.getX() : 0.5, l.hasCoordinates() ? l.getY() : 0.5, reachable);
    }
}
