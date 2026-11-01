package campusrider.algorithms;

import java.util.List;

/**
 * Greedy rickshaw selection.
 *
 * Greedy choice: for each request, immediately pick the single candidate with
 * the lowest cost score and send the request to that driver. We never look
 * ahead at future requests; if the driver rejects, the next-lowest candidate
 * is chosen (see RideMatchingService), which is again a greedy step.
 *
 * Score (lower is better), in meters-equivalent:
 *
 *   score = pickupDistance                       how far the rickshaw must drive to the passenger
 *         + 1.5 * detour                         extra meters imposed on the existing route
 *         - 100 * [pickup lies on current route] reward rickshaws already passing by
 *         + 20 * (freeSeats - seatsNeeded)        best fit: prefer the rickshaw the request fills
 *                                                 most, keeping empty rickshaws for groups
 *
 * Incompatible candidates (no seats, too far, detour over the limit, unfair to
 * existing passengers) are never selected.
 *
 * Time: O(R) to pick the best of R candidates.
 * Why greedy: requests arrive one at a time and drivers answer in seconds, so a
 * fast, explainable locally-optimal choice beats an expensive global assignment.
 */
public final class GreedyRickshawMatcher {

    public static final double DETOUR_WEIGHT = 1.5;
    public static final double ON_ROUTE_BONUS = 100.0;
    public static final double SPARE_SEAT_PENALTY = 20.0;

    private GreedyRickshawMatcher() {
    }

    public static double score(RouteCompatibility.CompatibilityResult c, int seatsNeeded) {
        if (c == null || !c.isCompatible()) {
            return Double.POSITIVE_INFINITY;
        }
        int spareSeats = Math.max(0, c.getRickshaw().getAvailableSeats() - seatsNeeded);
        return c.getDistanceToPickupMeters()
                + DETOUR_WEIGHT * c.getDetourMeters()
                - (c.isPickupOnCurrentRoute() ? ON_ROUTE_BONUS : 0.0)
                + SPARE_SEAT_PENALTY * spareSeats;
    }

    /** Linear scan for the lowest score; ties go to the smaller rickshaw id. Null when none is compatible. */
    public static RouteCompatibility.CompatibilityResult selectBest(
            List<RouteCompatibility.CompatibilityResult> candidates, int seatsNeeded) {
        RouteCompatibility.CompatibilityResult best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (RouteCompatibility.CompatibilityResult c : candidates) {
            double s = score(c, seatsNeeded);
            if (Double.isInfinite(s)) {
                continue;
            }
            if (best == null || s < bestScore
                    || (s == bestScore && c.getRickshaw().getId().compareTo(best.getRickshaw().getId()) < 0)) {
                best = c;
                bestScore = s;
            }
        }
        return best;
    }
}
