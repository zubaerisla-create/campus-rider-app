package campusrider.controller;

import campusrider.config.AppProperties;
import campusrider.dto.CampusMapDto;
import campusrider.dto.LocationDto;
import campusrider.dto.RoadDto;
import campusrider.dto.RouteDto;
import campusrider.model.Location;
import campusrider.model.Road;
import campusrider.service.RideService;
import campusrider.service.RouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Public campus data: locations, Trie search, the road graph, and Dijkstra routes. */
@RestController
public class LocationController {
    private final RouteService routes;
    private final RideService rides;
    private final AppProperties properties;

    public LocationController(RouteService routes, RideService rides, AppProperties properties) {
        this.routes = routes;
        this.rides = rides;
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "locations", routes.getGraph().getLocationCount(),
                "roads", routes.getGraph().getRoadCount());
    }

    @GetMapping("/api/locations")
    public List<LocationDto> locations() {
        List<LocationDto> list = new ArrayList<>();
        for (Location l : routes.getLocationsSorted()) {
            list.add(LocationDto.of(l, routes.isRoutable(l.getId())));
        }
        return list;
    }

    /** Trie prefix search for pickup / destination autocomplete. */
    @GetMapping("/api/locations/search")
    public List<LocationDto> search(@RequestParam(name = "q", defaultValue = "") String query,
                                    @RequestParam(name = "limit", defaultValue = "8") int limit) {
        String q = query.length() > 60 ? query.substring(0, 60) : query;
        List<LocationDto> list = new ArrayList<>();
        for (Location l : routes.searchLocations(q)) {
            if (list.size() >= Math.max(1, Math.min(limit, 25))) {
                break;
            }
            list.add(LocationDto.of(l, routes.isRoutable(l.getId())));
        }
        return list;
    }

    /** The campus graph (vertices + weighted edges) for drawing the map. */
    @GetMapping("/api/map")
    public CampusMapDto map() {
        List<RoadDto> roads = new ArrayList<>();
        for (Road r : routes.getRoads()) {
            roads.add(new RoadDto(r.getFromId(), r.getToId(), r.getDistanceMeters(), r.getName()));
        }
        return new CampusMapDto(locations(), roads, properties.maxDetourMeters());
    }

    /** Dijkstra shortest path between two locations, with distance, time and fare estimate. */
    @GetMapping("/api/routes/shortest-path")
    public RouteDto shortestPath(@RequestParam String from, @RequestParam String to) {
        return rides.estimate(from, to);
    }
}
