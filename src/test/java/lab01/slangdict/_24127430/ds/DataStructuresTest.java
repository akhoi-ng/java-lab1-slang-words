package lab01.slangdict._24127430.ds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DataStructuresTest {

    private static final Levenshtein LEV = Levenshtein.INSTANCE;

    private static List<String> randomWords(int count, long seed) {
        Random random = new Random(seed);
        Set<String> words = new HashSet<>();
        while (words.size() < count) {
            int len = 1 + random.nextInt(7);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append((char) ('a' + random.nextInt(5))); // bảng chữ nhỏ để có nhiều từ gần nhau
            }
            words.add(sb.toString());
        }
        return new ArrayList<>(words);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    // ---------------- Levenshtein ----------------

    @Test
    void levenshteinKnownValues() {
        assertEquals(0, LEV.distance("lol", "lol"));
        assertEquals(1, LEV.distance("lol", "lool"));
        assertEquals(1, LEV.distance("lol", "lo"));
        assertEquals(1, LEV.distance("lol", "lul"));
        assertEquals(2, LEV.distance("love", "lvoe"));
        assertEquals(3, LEV.distance("kitten", "sitting"));
        assertEquals(3, LEV.distance("", "abc"));
        assertEquals(LEV.distance("flaw", "lawn"), LEV.distance("lawn", "flaw"));
    }

    @Test
    void levenshteinStaysSingletonAfterSerialization() throws Exception {
        assertSame(Levenshtein.INSTANCE, roundTrip(Levenshtein.INSTANCE));
    }

    // ---------------- InvertedIndex ----------------

    @Test
    void invertedIndexAddRemoveAndRange() {
        InvertedIndex<String, Integer> index = new InvertedIndex<>();
        assertTrue(index.add("love", 1));
        assertTrue(index.add("love", 2));
        assertFalse(index.add("love", 2));
        index.add("lovely", 3);
        index.add("glove", 4);

        assertEquals(Set.of(1, 2), index.get("love"));
        assertEquals(Set.of(), index.get("missing"));
        assertEquals(List.of("love", "lovely"), new ArrayList<>(index.keysInRange("lov", "lov￿")));

        assertTrue(index.remove("lovely", 3));
        assertFalse(index.containsKey("lovely"));
        assertFalse(index.remove("lovely", 3));
        assertEquals(2, index.keyCount());
    }

    // ---------------- Trie ----------------

    @Test
    void trieSupportsMultipleValuesPerKey() {
        Trie<Integer> trie = new Trie<>();
        trie.put("lol", 1);
        trie.put("lol", 2);
        trie.put("lmao", 3);
        trie.put("l", 4);

        assertEquals(Set.of(1, 2), trie.get("lol"));
        assertEquals(Set.of(), trie.get("lo"));
        assertEquals(List.of(4, 3, 1, 2), trie.valuesWithPrefix("l", 10)); // l < lmao < lol
        assertEquals(List.of(4, 3), trie.valuesWithPrefix("l", 2));
        assertEquals(List.of(), trie.valuesWithPrefix("x", 10));
        assertEquals(3, trie.keyCount());

        assertTrue(trie.remove("lol", 1));
        assertEquals(Set.of(2), trie.get("lol"));
        assertTrue(trie.remove("lol", 2));
        assertFalse(trie.remove("lol", 2));
        assertEquals(Set.of(), trie.get("lol"));
        assertEquals(2, trie.keyCount());
        assertEquals(List.of(4, 3), trie.valuesWithPrefix("l", 10));
    }

    @Test
    void triePrefixMatchesBruteForce() throws Exception {
        List<String> words = randomWords(2000, 42);
        Trie<String> trie = new Trie<>();
        words.forEach(w -> trie.put(w, w));
        Trie<String> restored = roundTrip(trie);

        for (String prefix : List.of("", "a", "ab", "cde", "eeee", "zz")) {
            List<String> expected = new TreeSet<>(words).stream()
                    .filter(w -> w.startsWith(prefix)).collect(Collectors.toList());
            assertEquals(expected, trie.valuesWithPrefix(prefix, Integer.MAX_VALUE), "prefix=" + prefix);
            assertEquals(expected, restored.valuesWithPrefix(prefix, Integer.MAX_VALUE), "restored prefix=" + prefix);
        }
    }

    @Test
    void trieWithVeryLongKeyRoundTripsWithoutDeepRecursion() throws Exception {
        // Serialize mặc định đệ quy theo độ dài key: 50.000 ký tự làm tràn stack mặc định khi đọc lại.
        String longKey = "x".repeat(50_000);
        Trie<Integer> trie = new Trie<>();
        trie.put(longKey, 1);
        trie.put("lol", 3);
        trie.put("lol", 2); // thứ tự value trong cùng key phải được giữ
        trie.put("l", 4);

        Trie<Integer> restored = roundTrip(trie);
        assertEquals(Set.of(1), restored.get(longKey));
        assertEquals(List.of(4, 3, 2), restored.valuesWithPrefix("l", 10));
        assertEquals(List.of(4, 3, 2, 1), restored.valuesWithPrefix("", 10));
        assertEquals(3, restored.keyCount());
        assertTrue(restored.remove("lol", 3));
        assertEquals(List.of(4, 2), restored.valuesWithPrefix("l", 10));

        assertTrue(restored.remove(longKey, 1));
        assertFalse(restored.remove(longKey, 1));
        assertEquals(List.of(), restored.valuesWithPrefix("x", 10));
        assertEquals(2, restored.keyCount());
    }

    @Test
    void emptyTrieRoundTrips() throws Exception {
        Trie<Integer> restored = roundTrip(new Trie<Integer>());
        assertEquals(0, restored.keyCount());
        assertEquals(List.of(), restored.valuesWithPrefix("", 10));
        restored.put("a", 1);
        assertEquals(Set.of(1), restored.get("a"));
    }

    // ---------------- BKTree ----------------

    @Test
    void bkTreeSearchMatchesBruteForce() throws Exception {
        List<String> words = randomWords(3000, 7);
        BKTree<String> tree = new BKTree<>(LEV);
        words.forEach(tree::add);
        assertEquals(words.size(), tree.size());
        BKTree<String> restored = roundTrip(tree);

        for (String query : List.of("abc", "eeeee", "a", "dcbadcb", "zzz")) {
            for (int k = 0; k <= 2; k++) {
                final int max = k;
                Set<String> expected = words.stream()
                        .filter(w -> LEV.distance(query, w) <= max).collect(Collectors.toSet());
                Set<String> actual = tree.search(query, k).stream()
                        .map(BKTree.Match::item).collect(Collectors.toSet());
                assertEquals(expected, actual, "query=" + query + " k=" + k);
                assertEquals(expected.size(), restored.search(query, k).size());
            }
        }
    }

    @Test
    void bkTreeResultsAreSortedAndSoftDeleteWorks() {
        BKTree<String> tree = new BKTree<>(LEV);
        for (String w : List.of("lol", "lool", "lul", "lmao", "rofl")) {
            tree.add(w);
        }
        assertFalse(tree.add("lol"));

        List<BKTree.Match<String>> matches = tree.search("lol", 1);
        assertEquals("lol", matches.get(0).item());
        assertEquals(0, matches.get(0).distance());
        assertEquals(Set.of("lol", "lool", "lul"),
                matches.stream().map(BKTree.Match::item).collect(Collectors.toSet()));

        assertTrue(tree.remove("lol"));
        assertFalse(tree.remove("lol"));
        assertFalse(tree.contains("lol"));
        assertEquals(4, tree.size());
        assertEquals(Set.of("lool", "lul"),
                tree.search("lol", 1).stream().map(BKTree.Match::item).collect(Collectors.toSet()));

        assertTrue(tree.add("lol")); // thêm lại -> bỏ đánh dấu xoá
        assertTrue(tree.contains("lol"));
        assertEquals(5, tree.size());
    }
}
