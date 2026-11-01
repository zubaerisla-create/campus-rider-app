package campusrider.algorithms;

import java.util.Comparator;
import java.util.List;

/**
 * Sorts available rickshaw options with the custom {@link MergeSort} (O(N log N)).
 *
 * Criteria (compatible rickshaws always come before incompatible ones):
 *   BEST_COMPATIBILITY - lowest greedy score first ({@link GreedyRickshawMatcher})
 *   NEAREST_PICKUP     - shortest Dijkstra distance to the pickup first
 *   MOST_SEATS         - most free seats first
 * Ties fall back to pickup distance, then rickshaw id, so the order is deterministic.
 *
 * Used by: GET /api/rides/available (the ride options list the passenger sees)
 * and RideMatchingService (ranking candidates before the greedy pick).
 */
public final class RickshawSorter {

    public enum SortCriterion {
        BEST_COMPATIBILITY,
        NEAREST_PICKUP,
        MOST_SEATS;

        public static SortCriterion fromParam(String value) {
            if (value == null || value.isBlank()) {
                return BEST_COMPATIBILITY;
            }
            switch (value.trim().toLowerCase()) {
                case "nearest":
                case "nearest_pickup":
                    return NEAREST_PICKUP;
                case "seats":
                case "most_seats":
                    return MOST_SEATS;
                default:
                    return BEST_COMPATIBILITY;
            }
        }
    }

    private RickshawSorter() {
    }

    public static List<RouteCompatibility.CompatibilityResult> mergeSort(
            List<RouteCompatibility.CompatibilityResult> items, SortCriterion criterion, int seatsNeeded) {
        return MergeSort.sort(items, comparatorFor(criterion, seatsNeeded));
    }

    static Comparator<RouteCompatibility.CompatibilityResult> comparatorFor(SortCriterion criterion,
                                                                           int seatsNeeded) {
        SortCriterion crit = criterion == null ? SortCriterion.BEST_COMPATIBILITY : criterion;
        return (a, b) -> {
            if (a.isCompatible() != b.isCompatible()) {
                return a.isCompatible() ? -1 : 1;
            }
            int primary;
            switch (crit) {
                case NEAREST_PICKUP:
                    primary = Double.compare(a.getDistanceToPickupMeters(), b.getDistanceToPickupMeters());
                    break;
                case MOST_SEATS:
                    primary = Integer.compare(b.getRickshaw().getAvailableSeats(), a.getRickshaw().getAvailableSeats());
                    break;
                case BEST_COMPATIBILITY:
                default:
                    primary = Double.compare(GreedyRickshawMatcher.score(a, seatsNeeded),
                            GreedyRickshawMatcher.score(b, seatsNeeded));
                    break;
            }
            if (primary == 0) {
                primary = Double.compare(a.getDistanceToPickupMeters(), b.getDistanceToPickupMeters());
            }
            return primary != 0 ? primary : a.getRickshaw().getId().compareTo(b.getRickshaw().getId());
        };
    }
}
