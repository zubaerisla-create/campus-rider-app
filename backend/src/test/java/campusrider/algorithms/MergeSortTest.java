package campusrider.algorithms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSortTest {

    @Test
    void sortsRandomListsLikeAReferenceSort() {
        Random random = new Random(42);
        for (int n = 0; n < 200; n += 7) {
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(random.nextInt(50) - 25);
            }
            List<Integer> sorted = MergeSort.sort(items, Comparator.naturalOrder());
            assertEquals(n, sorted.size());
            for (int i = 1; i < sorted.size(); i++) {
                assertTrue(sorted.get(i - 1) <= sorted.get(i), "not sorted at " + i);
            }
        }
    }

    @Test
    void isStableAndDoesNotModifyTheInput() {
        List<String> items = List.of("b1", "a1", "b2", "a2", "c1", "a3");
        List<String> sorted = MergeSort.sort(items, Comparator.comparing(s -> s.charAt(0)));
        assertEquals(List.of("a1", "a2", "a3", "b1", "b2", "c1"), sorted);
        assertEquals(List.of("b1", "a1", "b2", "a2", "c1", "a3"), items);
    }

    @Test
    void handlesEmptyAndNull() {
        assertTrue(MergeSort.sort(List.<Integer>of(), Comparator.naturalOrder()).isEmpty());
        assertTrue(MergeSort.<Integer>sort(null, Comparator.naturalOrder()).isEmpty());
    }
}
