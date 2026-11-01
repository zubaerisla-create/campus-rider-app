package campusrider.algorithms;

import campusrider.model.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationTrieTest {

    private LocationTrie buildTrie() {
        LocationTrie trie = new LocationTrie();
        trie.addLocation(TestGraphs.location("dairy_gate", "Dairy Gate", "dairy", "main gate"));
        trie.addLocation(TestGraphs.location("prantik_gate", "Prantik Gate", "prantik"));
        trie.addLocation(TestGraphs.location("bottola", "Bottola", "bottola", "bot tola"));
        trie.addLocation(TestGraphs.location("cse", "CSE Department (Science Complex)", "cse"));
        return trie;
    }

    private static List<String> ids(List<Location> locations) {
        return locations.stream().map(Location::getId).collect(Collectors.toList());
    }

    @Test
    void prefixSearchIsCaseInsensitive() {
        LocationTrie trie = buildTrie();
        assertEquals(List.of("dairy_gate"), ids(trie.search("DAI")));
        assertEquals(List.of("dairy_gate"), ids(trie.search("dai")));
        assertEquals(List.of("dairy_gate"), ids(trie.search("  Dairy G ")));
    }

    @Test
    void matchesAnyWordOfTheNameAndAliases() {
        LocationTrie trie = buildTrie();
        assertEquals(List.of("dairy_gate", "prantik_gate"), ids(trie.search("gate")));
        assertEquals(List.of("cse"), ids(trie.search("science")));
        assertEquals(List.of("dairy_gate"), ids(trie.search("main")));
    }

    @Test
    void duplicateKeysReturnTheLocationOnce() {
        LocationTrie trie = buildTrie();
        // "bottola" is both the name and an alias, "bot" is also an alias prefix.
        assertEquals(List.of("bottola"), ids(trie.search("bot")));
        trie.addLocation(TestGraphs.location("bottola", "Bottola", "bottola"));
        assertEquals(1, trie.search("bottola").size());
    }

    @Test
    void emptyOrUnknownInputReturnsNothing() {
        LocationTrie trie = buildTrie();
        assertTrue(trie.search("").isEmpty());
        assertTrue(trie.search("   ").isEmpty());
        assertTrue(trie.search(null).isEmpty());
        assertTrue(trie.search("xyz").isEmpty());
    }
}
