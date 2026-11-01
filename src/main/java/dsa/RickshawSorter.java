package dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom Merge Sort implementation O(N log N) for sorting available Rickshaw
 * candidates by Distance to Pickup, Route Compatibility Score, or Available Seats.
 */
public class RickshawSorter {

    public enum SortCriterion {
        BEST_COMPATIBILITY("Best Route Compatibility (Greedy)"),
        NEAREST_PICKUP("Nearest to Pickup (Dijkstra)"),
        MOST_SEATS("Most Available Seats");

        private final String label;

        SortCriterion(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private RickshawSorter() {
    }

    /**
     * Sorts a list of CompatibilityResult items using an explicit top-down
     * Merge Sort algorithm in O(N log N) time.
     */
    public static List<RouteCompatibility.CompatibilityResult> mergeSort(
            List<RouteCompatibility.CompatibilityResult> items,
            SortCriterion criterion) {
        if (items == null || items.size() <= 1) {
            return items == null ? List.of() : new ArrayList<>(items);
        }
        Comparator<RouteCompatibility.CompatibilityResult> cmp = comparatorFor(criterion);
        List<RouteCompatibility.CompatibilityResult> copy = new ArrayList<>(items);
        mergeSortRecursive(copy, 0, copy.size() - 1, cmp);
        return copy;
    }

    private static void mergeSortRecursive(List<RouteCompatibility.CompatibilityResult> list,
                                           int left,
                                           int right,
                                           Comparator<RouteCompatibility.CompatibilityResult> cmp) {
        if (left >= right) {
            return;
        }
        int mid = left + (right - left) / 2;
        mergeSortRecursive(list, left, mid, cmp);
        mergeSortRecursive(list, mid + 1, right, cmp);
        merge(list, left, mid, right, cmp);
    }

    private static void merge(List<RouteCompatibility.CompatibilityResult> list,
                              int left,
                              int mid,
                              int right,
                              Comparator<RouteCompatibility.CompatibilityResult> cmp) {
        List<RouteCompatibility.CompatibilityResult> temp = new ArrayList<>(right - left + 1);
        int i = left;
        int j = mid + 1;

        while (i <= mid && j <= right) {
            if (cmp.compare(list.get(i), list.get(j)) <= 0) {
                temp.add(list.get(i++));
            } else {
                temp.add(list.get(j++));
            }
        }
        while (i <= mid) {
            temp.add(list.get(i++));
        }
        while (j <= right) {
            temp.add(list.get(j++));
        }
        for (int k = 0; k < temp.size(); k++) {
            list.set(left + k, temp.get(k));
        }
    }

    private static Comparator<RouteCompatibility.CompatibilityResult> comparatorFor(SortCriterion criterion) {
        SortCriterion crit = criterion == null ? SortCriterion.BEST_COMPATIBILITY : criterion;
        return (a, b) -> {
            // Always place compatible rickshaws before incompatible ones
            if (a.isCompatible() != b.isCompatible()) {
                return a.isCompatible() ? -1 : 1;
            }
            int primary = 0;
            switch (crit) {
                case NEAREST_PICKUP:
                    primary = Double.compare(a.getDistanceToPickupMeters(), b.getDistanceToPickupMeters());
                    if (primary == 0) {
                        primary = Double.compare(a.getDetourMeters(), b.getDetourMeters());
                    }
                    break;
                case MOST_SEATS:
                    primary = Integer.compare(
                            b.getRickshaw().getAvailableSeats(),
                            a.getRickshaw().getAvailableSeats());
                    if (primary == 0) {
                        primary = Double.compare(a.getDistanceToPickupMeters(), b.getDistanceToPickupMeters());
                    }
                    break;
                case BEST_COMPATIBILITY:
                default:
                    primary = Double.compare(a.getGreedyScore(), b.getGreedyScore());
                    if (primary == 0) {
                        primary = Double.compare(a.getDistanceToPickupMeters(), b.getDistanceToPickupMeters());
                    }
                    break;
            }
            if (primary != 0) {
                return primary;
            }
            return a.getRickshaw().getId().compareTo(b.getRickshaw().getId());
        };
    }
}
