package campusrider.matching;

import campusrider.algorithms.GreedyRickshawMatcher;
import campusrider.algorithms.RickshawSorter;
import campusrider.algorithms.RouteCompatibility;
import campusrider.config.AppProperties;
import campusrider.model.RideRequest;
import campusrider.model.Rickshaw;
import campusrider.service.RouteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Automatic rickshaw matching for a ride request:
 *
 *   1. Take every online rickshaw (the caller passes the current list).
 *   2. Use each rickshaw's current location (the vertex it is at or driving to).
 *   3. Compute the Dijkstra pickup distance,
 *   4. check free seats (minus seats already promised to other outstanding offers),
 *   5. evaluate route compatibility with its current passengers, and
 *   6. compute the additional detour             - all inside {@link RouteCompatibility}.
 *   7. Rank the candidates with the custom merge sort ({@link RickshawSorter}).
 *   8. Pick the best with the {@link GreedyRickshawMatcher} score; the request is sent to that driver.
 *
 * On rejection RideService adds the driver to the request's rejectedBy set and calls
 * findBest again, so the next most suitable rickshaw is chosen automatically.
 */
@Service
public class RideMatchingService {

    private final RouteService routes;
    private final AppProperties properties;

    public RideMatchingService(RouteService routes, AppProperties properties) {
        this.routes = routes;
        this.properties = properties;
    }

    /** Evaluates and sorts every online rickshaw for a trip (the passenger's "available rickshaws" list). */
    public List<RouteCompatibility.CompatibilityResult> rankRickshaws(
            String pickupId, String destinationId, int seats, List<Rickshaw> rickshaws,
            Map<String, List<RideRequest>> activeByRickshaw, Map<String, Integer> reservedSeats,
            RickshawSorter.SortCriterion criterion) {
        List<RouteCompatibility.CompatibilityResult> results = new ArrayList<>();
        for (Rickshaw r : rickshaws) {
            if (!r.isOnline()) {
                continue;
            }
            results.add(evaluate(r, activeByRickshaw.getOrDefault(r.getId(), List.of()),
                    pickupId, destinationId, seats, freeSeats(r, reservedSeats)));
        }
        return RickshawSorter.mergeSort(results, criterion, seats);
    }

    /**
     * The best rickshaw for a pending request, or null if no suitable rickshaw remains.
     * Rickshaws whose driver already rejected this request are skipped. A rickshaw the
     * passenger picked from the list is tried first, as long as it is still compatible.
     */
    public RouteCompatibility.CompatibilityResult findBest(RideRequest ride, List<Rickshaw> rickshaws,
                                                           Map<String, List<RideRequest>> activeByRickshaw,
                                                           Map<String, Integer> reservedSeats) {
        List<RouteCompatibility.CompatibilityResult> candidates = new ArrayList<>();
        for (Rickshaw r : rickshaws) {
            if (!r.isOnline() || ride.isRejectedBy(r.getId())) {
                continue;
            }
            RouteCompatibility.CompatibilityResult eval = evaluate(r,
                    activeByRickshaw.getOrDefault(r.getId(), List.of()), ride.getPickupId(),
                    ride.getDestinationId(), ride.getSeatsRequested(), freeSeats(r, reservedSeats));
            if (!eval.isCompatible()) {
                continue;
            }
            if (r.getId().equals(ride.getPreferredRickshawId())) {
                return eval; // the passenger's own choice wins while it is still suitable
            }
            candidates.add(eval);
        }
        List<RouteCompatibility.CompatibilityResult> ranked = RickshawSorter.mergeSort(candidates,
                RickshawSorter.SortCriterion.BEST_COMPATIBILITY, ride.getSeatsRequested());
        return GreedyRickshawMatcher.selectBest(ranked, ride.getSeatsRequested());
    }

    /** Route compatibility of one rickshaw for one trip, with the configured limits. */
    public RouteCompatibility.CompatibilityResult evaluate(Rickshaw rickshaw, List<RideRequest> activeRides,
                                                           String pickupId, String destinationId,
                                                           int seats, int seatsFree) {
        return RouteCompatibility.evaluate(routes.getGraph(), rickshaw, activeRides, pickupId, destinationId,
                seats, seatsFree, properties.maxDetourMeters(), properties.maxPickupMeters());
    }

    private static int freeSeats(Rickshaw r, Map<String, Integer> reservedSeats) {
        int reserved = reservedSeats == null ? 0 : reservedSeats.getOrDefault(r.getId(), 0);
        return Math.max(0, r.getAvailableSeats() - reserved);
    }
}
