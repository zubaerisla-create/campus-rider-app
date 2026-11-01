package campusrider.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hand-written top-down Merge Sort.
 *
 * Idea: split the list into two halves, sort each half recursively, then merge
 * the two sorted halves by repeatedly taking the smaller front element.
 *
 * Time:  O(N log N) in every case (log N levels, O(N) merge work per level).
 * Space: O(N) for the temporary merge buffer.
 * Stable: equal elements keep their original order (the merge takes from the
 * left half on ties), so sorting by a secondary key first is preserved.
 *
 * Used for: ranking available rickshaws ({@link RickshawSorter}), Trie search
 * results, the location list, and ride history. No call to Collections.sort,
 * List.sort or Arrays.sort is used anywhere in the backend.
 */
public final class MergeSort {

    private MergeSort() {
    }

    /** Returns a new sorted list; the input list is not modified. */
    public static <T> List<T> sort(List<T> items, Comparator<? super T> comparator) {
        List<T> copy = items == null ? new ArrayList<>() : new ArrayList<>(items);
        if (copy.size() > 1) {
            List<T> buffer = new ArrayList<>(copy);
            sortRange(copy, buffer, 0, copy.size() - 1, comparator);
        }
        return copy;
    }

    private static <T> void sortRange(List<T> list, List<T> buffer, int left, int right,
                                      Comparator<? super T> comparator) {
        if (left >= right) {
            return;
        }
        int mid = left + (right - left) / 2;
        sortRange(list, buffer, left, mid, comparator);
        sortRange(list, buffer, mid + 1, right, comparator);
        merge(list, buffer, left, mid, right, comparator);
    }

    private static <T> void merge(List<T> list, List<T> buffer, int left, int mid, int right,
                                  Comparator<? super T> comparator) {
        int i = left;
        int j = mid + 1;
        int k = left;
        while (i <= mid && j <= right) {
            // "<= 0" takes the left element on ties, which keeps the sort stable.
            if (comparator.compare(list.get(i), list.get(j)) <= 0) {
                buffer.set(k++, list.get(i++));
            } else {
                buffer.set(k++, list.get(j++));
            }
        }
        while (i <= mid) {
            buffer.set(k++, list.get(i++));
        }
        while (j <= right) {
            buffer.set(k++, list.get(j++));
        }
        for (int p = left; p <= right; p++) {
            list.set(p, buffer.get(p));
        }
    }
}
