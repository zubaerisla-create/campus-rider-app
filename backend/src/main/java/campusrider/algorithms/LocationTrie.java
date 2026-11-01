package campusrider.algorithms;

import campusrider.model.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trie (prefix tree) for location search and autocomplete.
 *
 * Every key (a full name, each word of the name, and each alias) is stored
 * lower-case, one character per level. A search walks down the characters of
 * the prefix, then collects every location stored below that node.
 *
 * Insert: O(L) per key.  Search: O(P + N) where P = prefix length and
 * N = number of nodes below the prefix (plus O(M log M) to sort M matches).
 *
 * Used by: GET /api/locations/search, which powers the pickup and destination
 * autocomplete in the passenger booking panel and the driver dashboard.
 * Why a Trie: every keystroke is a prefix query; a Trie answers it by walking
 * P characters instead of scanning every location name and alias.
 */
public class LocationTrie {

    private static class TrieNode {
        final Map<Character, TrieNode> children = new TreeMap<>();
        final List<Location> locationsEndingHere = new ArrayList<>();
    }

    private final TrieNode root = new TrieNode();

    /** Indexes a location by its full name, each word of its name, and its aliases. */
    public void addLocation(Location location) {
        insert(location.getName(), location);
        for (String word : location.getName().split("[\\s()\\-]+")) {
            insert(word, location);
        }
        for (String alias : location.getAliases()) {
            insert(alias, location);
        }
    }

    public void insert(String key, Location location) {
        String word = normalize(key);
        if (word.isEmpty()) {
            return;
        }
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        // The same key may be inserted twice (for example "bottola" as name and alias).
        if (!node.locationsEndingHere.contains(location)) {
            node.locationsEndingHere.add(location);
        }
    }

    /** Returns matching locations sorted by name, each one only once. Empty prefix returns nothing. */
    public List<Location> search(String prefix) {
        String word = normalize(prefix);
        if (word.isEmpty()) {
            return new ArrayList<>();
        }
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.get(c);
            if (node == null) {
                return new ArrayList<>(); // no key starts with this prefix
            }
        }

        // A map keyed by id removes duplicates (one location can have many keys).
        Map<String, Location> found = new LinkedHashMap<>();
        collect(node, found);
        // Custom merge sort (O(N log N)) orders the suggestions alphabetically.
        return MergeSort.sort(new ArrayList<>(found.values()), Comparator.comparing(Location::getName));
    }

    /** Depth-first walk that gathers every location in this subtree. */
    private void collect(TrieNode node, Map<String, Location> found) {
        for (Location location : node.locationsEndingHere) {
            found.put(location.getId(), location);
        }
        for (TrieNode child : node.children.values()) {
            collect(child, found);
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
