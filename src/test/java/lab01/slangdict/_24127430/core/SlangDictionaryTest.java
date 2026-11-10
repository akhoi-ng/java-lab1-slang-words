package lab01.slangdict._24127430.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.ds.BKTree;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.model.SlangEntry;

class SlangDictionaryTest {

    private static SlangDictionary original() throws Exception {
        try (InputStream in = SlangDictionaryTest.class.getResourceAsStream("/data/slang.txt")) {
            return SlangDictionary.build(SlangFileParser.parse(in));
        }
    }

    private static SlangDictionary roundTrip(SlangDictionary dictionary) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(dictionary);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (SlangDictionary) in.readObject();
        }
    }

    private static Set<String> slangsOf(SlangDictionary d, Iterable<Integer> ids) {
        Set<String> names = new TreeSet<>();
        ids.forEach(id -> names.add(d.get(id).getSlang()));
        return names;
    }

    private static Set<String> items(List<BKTree.Match<String>> matches) {
        return matches.stream().map(BKTree.Match::item).collect(Collectors.toSet());
    }

    // ---------------- build từ file gốc ----------------

    @Test
    void buildsOriginalFile() throws Exception {
        SlangDictionary d = original();
        assertEquals(7638, d.size());
        assertEquals(7638, d.all().size());
        assertEquals(7638, d.names().size(), "file gốc không có tên trùng");
        assertEquals(7638, d.nextId());
        assertEquals("#1", d.get(0).getSlang());
        assertNull(d.get(7638));

        assertTrue(slangsOf(d, d.idsWithWord("money")).contains("$"));
        assertTrue(slangsOf(d, d.idsWithWord("LAUGHING")).containsAll(Set.of("LOL", "LMAO")));
        assertTrue(d.vocabulary().contains("laughing"));
    }

    @Test
    void nameLookupIgnoresCase() throws Exception {
        SlangDictionary d = original();
        for (String query : List.of("LOL", "lol", "LoL", "  lol ")) {
            assertEquals(Set.of("LOL"), slangsOf(d, d.idsByName(query)), query);
        }
        assertEquals(Set.of(), d.idsByName("definitely-not-a-slang"));

        Set<String> lmPrefix = slangsOf(d, d.idsWithNamePrefix("Lm", Integer.MAX_VALUE));
        assertTrue(lmPrefix.contains("LMAO"));
        assertTrue(lmPrefix.stream().allMatch(s -> s.toLowerCase().startsWith("lm")));

        assertTrue(items(d.fuzzyNames("LOOL", 1)).contains("lol"));
    }

    @Test
    void wordPrefixAndFuzzyUseVocabulary() {
        SlangDictionary d = SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "ILY", List.of("I love you", "lovely")),
                new SlangEntry(2, "GLHF", List.of("Good luck, have fun"))));

        assertEquals(List.of("laughing", "loud", "love", "lovely", "luck"), new ArrayList<>(d.wordsWithPrefix("L")));
        assertEquals(List.of("love", "lovely"), new ArrayList<>(d.wordsWithPrefix("lov")));
        assertEquals(List.of(), new ArrayList<>(d.wordsWithPrefix("xyz")));
        assertEquals(Set.of(1), d.idsWithWord("Love"));
        assertEquals(Set.of("lovely"), items(d.fuzzyWords("lovly", 1)));         // chèn 'e'
        assertEquals(Set.of("lovely", "love"), items(d.fuzzyWords("lovly", 2))); // love: thay l→e, xoá y
        assertEquals("lovely", d.fuzzyWords("lovly", 2).get(0).item(), "khoảng cách nhỏ nhất đứng đầu");
    }

    // ---------------- tên trùng ----------------

    @Test
    void duplicateNamesAreSeparateEntries() {
        SlangDictionary d = new SlangDictionary();
        SlangEntry a = d.add("LOL", List.of("Laugh out loud"));
        SlangEntry b = d.add("lol", List.of("Lots of love"));
        assertEquals(0, a.getId());
        assertEquals(1, b.getId());
        assertEquals(2, d.size());
        assertEquals(Set.of(0, 1), d.idsByName("Lol"));
        assertEquals(Set.of("lol"), d.names());
        assertEquals(Set.of(0, 1), new HashSet<>(d.idsWithNamePrefix("l", 10)));

        d.remove(0);
        assertEquals(Set.of(1), d.idsByName("LOL"));
        assertEquals(Set.of("lol"), items(d.fuzzyNames("lol", 0)), "vẫn còn một entry tên lol");
        assertEquals(Set.of(), d.idsWithWord("laugh"));
        assertEquals(Set.of(), items(d.fuzzyWords("laugh", 0)), "từ không còn entry nào thì biến khỏi BK-tree");

        d.remove(1);
        assertEquals(Set.of(), d.names());
        assertEquals(Set.of(), items(d.fuzzyNames("lol", 0)));
        assertNull(d.randomEntry(new Random(1)));
    }

    @Test
    void replaceRenamesAndRejectsUnknownId() {
        SlangDictionary d = new SlangDictionary();
        SlangEntry e = d.add("brb", List.of("Be right back"));
        d.replace(e.withSlang("BRB2").withMeanings(List.of("Be right back soon")));

        assertEquals(Set.of(), d.idsByName("brb"));
        assertEquals(Set.of(e.getId()), d.idsByName("brb2"));
        assertEquals(Set.of(e.getId()), d.idsWithWord("soon"));
        assertEquals("BRB2", d.get(e.getId()).getSlang());

        assertThrows(NoSuchElementException.class, () -> d.replace(new SlangEntry(99, "x", List.of("y"))));
        assertNull(d.remove(99));
        assertThrows(IllegalArgumentException.class, () -> d.add(" ", List.of("x")));
        assertEquals(e.getId() + 1, d.nextId(), "add lỗi không được làm tốn id");
        assertThrows(IllegalArgumentException.class, () -> SlangDictionary.build(List.of(
                new SlangEntry(1, "a", List.of("x")), new SlangEntry(1, "b", List.of("y")))));
    }

    // ---------------- thay đổi ngẫu nhiên vẫn giữ index đồng bộ ----------------

    private static final String[] NAME_POOL = {"lol", "LOL", "lool", "lul", "brb", "brB", "afk", "gg", "ggwp",
            "omg", "omfg", "idk", "idc", "ily", "smh", "tbh", "ngl", "irl", "fyi", "asap", "$", ":|", "<3"};
    private static final String[] WORD_POOL = {"laugh", "laughing", "love", "lovely", "loud", "out", "good",
            "game", "well", "played", "be", "right", "back", "away", "from", "keyboard", "know", "care", "money",
            "honest", "real", "life", "shake", "head", "café", "24", "7"};

    private static List<String> randomMeanings(Random random) {
        List<String> meanings = new ArrayList<>();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            StringBuilder meaning = new StringBuilder();
            int words = 1 + random.nextInt(4);
            for (int w = 0; w < words; w++) {
                if (w > 0) {
                    meaning.append(random.nextBoolean() ? " " : ", ");
                }
                String word = WORD_POOL[random.nextInt(WORD_POOL.length)];
                meaning.append(random.nextInt(4) == 0 ? word.toUpperCase() : word);
            }
            meanings.add(meaning.toString());
        }
        return meanings;
    }

    private static String randomName(Random random) {
        return NAME_POOL[random.nextInt(NAME_POOL.length)];
    }

    private static int randomExistingId(SlangDictionary d, Random random) {
        return d.randomEntry(random).getId();
    }

    /**
     * So sánh theo nội dung với một từ điển build lại từ đầu bằng chính các entry hiện có.
     * Không so cấu trúc cây: BK-tree có xoá mềm nên hình dạng khác nhưng kết quả tìm phải như nhau.
     */
    private static void assertSameIndexes(SlangDictionary expected, SlangDictionary actual, Set<String> probeNames,
                                          Set<String> probeWords) {
        assertEquals(expected.size(), actual.size());
        for (SlangEntry e : expected.all()) {
            SlangEntry other = actual.get(e.getId());
            assertNotNull(other, "thiếu id " + e.getId());
            assertEquals(e.getSlang(), other.getSlang());
            assertEquals(e.getMeanings(), other.getMeanings());
        }

        assertEquals(expected.names(), actual.names());
        for (String name : probeNames) {
            assertEquals(expected.idsByName(name), actual.idsByName(name), "idsByName " + name);
            for (int len = 0; len <= name.length(); len++) {
                String prefix = name.substring(0, len);
                List<Integer> got = actual.idsWithNamePrefix(prefix, Integer.MAX_VALUE);
                assertEquals(got.size(), new HashSet<>(got).size(), "prefix không được trả id trùng");
                assertEquals(new HashSet<>(expected.idsWithNamePrefix(prefix, Integer.MAX_VALUE)),
                        new HashSet<>(got), "prefix " + prefix);
            }
            for (int k = 0; k <= 2; k++) {
                assertEquals(items(expected.fuzzyNames(name, k)), items(actual.fuzzyNames(name, k)),
                        "fuzzy name " + name + " k=" + k);
            }
        }

        assertEquals(expected.vocabulary(), actual.vocabulary());
        for (String word : probeWords) {
            assertEquals(expected.idsWithWord(word), actual.idsWithWord(word), "word " + word);
            assertEquals(expected.wordsWithPrefix(word.substring(0, 1)), actual.wordsWithPrefix(word.substring(0, 1)));
            for (int k = 0; k <= 2; k++) {
                assertEquals(items(expected.fuzzyWords(word, k)), items(actual.fuzzyWords(word, k)),
                        "fuzzy word " + word + " k=" + k);
            }
        }

        // idList/positionOf: random phải ra đúng các entry đang có, và với đủ lượt thì gặp hết
        Set<Integer> seen = new HashSet<>();
        Random random = new Random(3);
        for (int i = 0; i < actual.size() * 30; i++) {
            SlangEntry e = actual.randomEntry(random);
            assertNotNull(actual.get(e.getId()));
            seen.add(e.getId());
        }
        assertEquals(expected.all().stream().map(SlangEntry::getId).collect(Collectors.toSet()), seen);
    }

    @Test
    void randomMutationsKeepEveryIndexInSync() throws Exception {
        Random random = new Random(2024);
        List<SlangEntry> initial = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            initial.add(new SlangEntry(i, randomName(random), randomMeanings(random)));
        }
        SlangDictionary d = SlangDictionary.build(initial);

        Set<String> probeNames = new HashSet<>();
        for (String name : NAME_POOL) {
            probeNames.add(Tokenizer.normalizeName(name));
        }
        probeNames.add("zzz");
        Set<String> probeWords = new HashSet<>();
        for (String word : WORD_POOL) {
            probeWords.add(word.toLowerCase());
        }
        probeWords.add("zzz");

        for (int step = 0; step < 3000; step++) {
            int op = random.nextInt(10);
            if (d.size() < 20 || op < 4) {
                d.add(randomName(random), randomMeanings(random));
            } else if (op < 7) {
                SlangEntry old = d.get(randomExistingId(d, random));
                SlangEntry updated = switch (random.nextInt(3)) {
                    case 0 -> old.withSlang(randomName(random));
                    case 1 -> old.withMeanings(randomMeanings(random));
                    default -> old.withSlang(randomName(random)).withMeanings(randomMeanings(random));
                };
                d.replace(updated);
            } else {
                assertNotNull(d.remove(randomExistingId(d, random)));
            }
        }

        // Pool nhỏ nên sau vòng lặp tên/từ nào cũng còn. Xoá sạch vài tên và một từ để kiểm tra
        // nhánh "không còn entry nào" (xoá mềm khỏi BK-tree, bỏ key khỏi index).
        List<SlangEntry> toRemove = d.all().stream()
                .filter(e -> e.getSlang().toLowerCase().startsWith("l")
                        || Tokenizer.tokens(e.getMeaningText()).contains("keyboard"))
                .collect(Collectors.toList());
        assertTrue(toRemove.size() > 0);
        toRemove.forEach(e -> d.remove(e.getId()));
        assertEquals(Set.of(), d.idsByName("lol"));
        assertEquals(Set.of(), d.idsWithWord("keyboard"));

        SlangDictionary rebuilt = SlangDictionary.build(new ArrayList<>(d.all()));
        assertSameIndexes(rebuilt, d, probeNames, probeWords);

        // serialize rồi đọc lại: dữ liệu, index và bộ đếm id đều giữ nguyên
        SlangDictionary restored = roundTrip(d);
        assertSameIndexes(rebuilt, restored, probeNames, probeWords);
        assertEquals(d.nextId(), restored.nextId());
        SlangEntry added = restored.add("new one", List.of("brand new"));
        assertEquals(d.nextId(), added.getId());
        assertEquals(Set.of(added.getId()), restored.idsWithWord("brand"));
    }

    @Test
    void originalSurvivesSerialization() throws Exception {
        SlangDictionary d = original();
        SlangDictionary restored = roundTrip(d);
        assertEquals(d.size(), restored.size());
        assertEquals(d.names(), restored.names());
        assertEquals(d.vocabulary(), restored.vocabulary());
        assertEquals(d.idsByName("LOL"), restored.idsByName("lol"));
        assertEquals(d.idsWithWord("money"), restored.idsWithWord("money"));
        assertEquals(items(d.fuzzyNames("lmoa", 2)), items(restored.fuzzyNames("lmoa", 2)));
    }
}
