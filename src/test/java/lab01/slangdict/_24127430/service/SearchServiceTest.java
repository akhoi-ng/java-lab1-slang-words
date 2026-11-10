package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.ds.Levenshtein;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchResult;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;

class SearchServiceTest {

    private static final SearchTarget SLANG = SearchTarget.SLANG;
    private static final SearchTarget DEFINITION = SearchTarget.DEFINITION;
    private static final int ALL = SearchService.UNLIMITED;

    /**
     * Từ điển nhỏ, id = vị trí trong danh sách:
     * 0 LOL, 1 lol (trùng tên), 2 LOLZ, 3 ROFL, 4 lmao, 5 BRB, 6 xlolx, 7 ":|", 8 LOUDOUT
     */
    private static SlangDictionary small() {
        List<SlangEntry> entries = List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "lol", List.of("Lots of love")),
                new SlangEntry(2, "LOLZ", List.of("Plural of lol, laugh")),
                new SlangEntry(3, "ROFL", List.of("Rolling on the floor laughing")),
                new SlangEntry(4, "lmao", List.of("Laughing my ass off")),
                new SlangEntry(5, "BRB", List.of("Be right back")),
                new SlangEntry(6, "xlolx", List.of("Kiss, lol")),
                new SlangEntry(7, ":|", List.of("Bored face")),
                new SlangEntry(8, "LOUDOUT", List.of("Loud, out of here")));
        return SlangDictionary.build(entries);
    }

    private static SearchService service(SlangDictionary dictionary) {
        return new SearchService(() -> dictionary);
    }

    private static List<Integer> ids(SearchOutcome outcome) {
        return outcome.results().stream().map(r -> r.entry().getId()).collect(Collectors.toList());
    }

    private static Set<Integer> idSet(SearchOutcome outcome) {
        return new TreeSet<>(ids(outcome));
    }

    private static List<Integer> scores(SearchOutcome outcome) {
        return outcome.results().stream().map(SearchResult::score).collect(Collectors.toList());
    }

    // ---------------- chung ----------------

    @Test
    void blankOrNullQueryGivesNothing() {
        SearchService search = service(small());
        for (SearchTarget target : SearchTarget.values()) {
            for (SearchMode mode : SearchMode.values()) {
                for (String query : new String[] {null, "", "   "}) {
                    SearchOutcome outcome = search.search(target, mode, query, ALL);
                    assertTrue(outcome.isEmpty(), target + " " + mode + " '" + query + "'");
                    assertEquals(0, outcome.totalMatches());
                }
            }
        }
        // definition chỉ gồm dấu câu: không có từ nào để tìm
        assertTrue(search.search(DEFINITION, SearchMode.EXACT, "!!! ...", ALL).isEmpty());
    }

    @Test
    void limitCutsResultsButTotalCountsEveryMatch() {
        SearchService search = service(small());
        // PREFIX "l": LOL, lol, LOLZ, lmao, LOUDOUT
        SearchOutcome all = search.search(SLANG, SearchMode.PREFIX, "l", ALL);
        assertEquals(5, all.totalMatches());
        SearchOutcome two = search.search(SLANG, SearchMode.PREFIX, "l", 2);
        assertEquals(2, two.results().size());
        assertEquals(5, two.totalMatches());
        assertTrue(two.isTruncated());
        assertEquals(ids(all).subList(0, 2), ids(two), "the cut keeps the best-ranked results");

        assertThrows(IllegalArgumentException.class, () -> search.search(SLANG, SearchMode.EXACT, "lol", 0));
        assertTrue(all.elapsedNanos() > 0);
    }

    @Test
    void resultsAreSortedByScoreThenName() {
        SearchService search = service(small());
        for (SearchTarget target : SearchTarget.values()) {
            for (SearchMode mode : SearchMode.values()) {
                List<SearchResult> results = search.search(target, mode, "lol", ALL).results();
                List<SearchResult> sorted = new ArrayList<>(results);
                sorted.sort(null);
                assertEquals(sorted, results, target + " " + mode);
            }
        }
    }

    @Test
    void serviceAlwaysUsesTheCurrentDictionary() {
        AtomicReference<SlangDictionary> current = new AtomicReference<>(small());
        SearchService search = new SearchService(current::get);
        assertEquals(2, search.search(SLANG, SearchMode.EXACT, "lol", ALL).totalMatches());

        // giống Reset: thay bằng một object từ điển khác
        current.set(SlangDictionary.build(List.of(new SlangEntry(0, "lol", List.of("Only one")))));
        assertEquals(1, search.search(SLANG, SearchMode.EXACT, "lol", ALL).totalMatches());
    }

    @Test
    void elapsedTimeDoesNotCountWaitingForTheSaveLock() throws Exception {
        SlangDictionary dictionary = small();
        SearchService search = service(dictionary);
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        Thread saver = new Thread(() -> {
            synchronized (dictionary) { // giống luồng ghi .dat đang serialize
                locked.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        saver.start();
        locked.await();

        long start = System.nanoTime();
        SearchOutcome outcome = search.search(SLANG, SearchMode.EXACT, "lol", ALL);
        long wall = System.nanoTime() - start;
        saver.join();

        assertEquals(2, outcome.totalMatches());
        assertTrue(wall >= 400_000_000L, "phải chờ khóa thật: " + wall);
        assertTrue(outcome.elapsedNanos() < 200_000_000L, "chỉ tính thời gian tìm: " + outcome.elapsedNanos());
    }

    // ---------------- F1: theo tên slang ----------------

    @Test
    void slangExactIgnoresCaseAndReturnsDuplicates() {
        SearchService search = service(small());
        assertEquals(Set.of(0, 1), idSet(search.search(SLANG, SearchMode.EXACT, "  LoL ", ALL)));
        assertEquals(Set.of(7), idSet(search.search(SLANG, SearchMode.EXACT, ":|", ALL)));
        assertTrue(search.search(SLANG, SearchMode.EXACT, "lo", ALL).isEmpty());
    }

    @Test
    void slangPrefixPutsExactNameFirst() {
        SearchOutcome outcome = service(small()).search(SLANG, SearchMode.PREFIX, "lol", ALL);
        assertEquals(List.of(0, 1, 2), ids(outcome)); // LOL, lol (trùng đúng) rồi LOLZ
        assertEquals(List.of(100, 100, 50), scores(outcome));
    }

    @Test
    void slangSubstringRanksExactThenPrefixThenInside() {
        SearchService search = service(small());
        SearchOutcome outcome = search.search(SLANG, SearchMode.SUBSTRING, "lol", ALL);
        assertEquals(List.of(0, 1, 2, 6), ids(outcome)); // LOL, lol, LOLZ, xlolx
        assertEquals(List.of(100, 100, 50, 20), scores(outcome));
        // ký hiệu đặc biệt vẫn tìm được
        assertEquals(Set.of(7), idSet(search.search(SLANG, SearchMode.SUBSTRING, "|", ALL)));
    }

    @Test
    void slangFuzzyFindsTypos() {
        SearchService search = service(small());
        // "lul" (3 ký tự → sai tối đa 1): lol cách 1; LOLZ cách 2 nên không lấy
        SearchOutcome outcome = search.search(SLANG, SearchMode.FUZZY, "lul", ALL);
        assertEquals(Set.of(0, 1), idSet(outcome));
        assertEquals(List.of(90, 90), scores(outcome));
        // "rolf" (4 ký tự): ROFL cách 2 (đảo chỗ = 2 phép thay) → không lấy; "rofll" (5 ký tự → sai 2) thì lấy
        assertTrue(search.search(SLANG, SearchMode.FUZZY, "rolf", ALL).isEmpty());
        assertEquals(Set.of(3), idSet(search.search(SLANG, SearchMode.FUZZY, "rofll", ALL)));
        // khớp đúng (LOLZ, 100) xếp trên khớp gần đúng (LOL, lol: 90)
        SearchOutcome lolz = search.search(SLANG, SearchMode.FUZZY, "lolz", ALL);
        assertEquals(List.of(2, 0, 1), ids(lolz));
        assertEquals(List.of(100, 90, 90), scores(lolz));
    }

    // ---------------- F2: theo definition ----------------

    @Test
    void definitionExactMatchesWholeWordsOnly() {
        SearchService search = service(small());
        assertEquals(Set.of(0, 3, 4), idSet(search.search(DEFINITION, SearchMode.EXACT, "LAUGHING", ALL)));
        assertEquals(Set.of(2), idSet(search.search(DEFINITION, SearchMode.EXACT, "laugh", ALL)));
        assertTrue(search.search(DEFINITION, SearchMode.EXACT, "laug", ALL).isEmpty());
    }

    @Test
    void definitionMultiWordIsAndAndPhraseRanksFirst() {
        SearchService search = service(small());
        // AND: phải có cả hai từ
        assertEquals(Set.of(0), idSet(search.search(DEFINITION, SearchMode.EXACT, "laughing loud", ALL)));
        // "out loud": LOL có nguyên cụm → +100; LOUDOUT có cả hai từ nhưng không liền nhau
        SearchOutcome outcome = search.search(DEFINITION, SearchMode.EXACT, "Out, LOUD!", ALL);
        assertEquals(List.of(0, 8), ids(outcome));
        assertEquals(List.of(120, 20), scores(outcome));
        // một từ không khớp gì → cả query không có kết quả
        assertTrue(search.search(DEFINITION, SearchMode.EXACT, "laughing zzz", ALL).isEmpty());
        // từ lặp lại chỉ tính một lần
        SearchOutcome once = search.search(DEFINITION, SearchMode.EXACT, "love", ALL);
        SearchOutcome twice = search.search(DEFINITION, SearchMode.EXACT, "love love", ALL);
        assertEquals(List.of(1), ids(twice));
        assertEquals(scores(once), scores(twice));
    }

    @Test
    void definitionPrefixAndSubstringPreferWholeWords() {
        SearchService search = service(small());
        SearchOutcome prefix = search.search(DEFINITION, SearchMode.PREFIX, "laugh", ALL);
        assertEquals(Set.of(0, 2, 3, 4), idSet(prefix));
        assertEquals(2, ids(prefix).get(0)); // "laugh" trùng đúng (10) trên "laughing" (5)

        assertEquals(Set.of(0, 2, 3, 4), idSet(search.search(DEFINITION, SearchMode.SUBSTRING, "augh", ALL)));
        assertEquals(Set.of(0, 3, 4), idSet(search.search(DEFINITION, SearchMode.PREFIX, "laughi", ALL)));
        assertTrue(search.search(DEFINITION, SearchMode.PREFIX, "augh", ALL).isEmpty());
    }

    @Test
    void definitionFuzzyToleratesTyposButNotForTinyWords() {
        SearchService search = service(small());
        // "laughng" (7 ký tự → sai 2): laughing cách 1, laugh cách 2
        SearchOutcome outcome = search.search(DEFINITION, SearchMode.FUZZY, "laughng", ALL);
        assertEquals(Set.of(0, 2, 3, 4), idSet(outcome));
        assertEquals(2, ids(outcome).get(3)); // laugh (điểm 4) xếp cuối, sau các laughing (điểm 7)
        // từ ≤ 2 ký tự phải đúng hẳn: "of" chỉ khớp "of", không khớp "on"/"off"
        assertEquals(Set.of(1, 2, 8), idSet(search.search(DEFINITION, SearchMode.FUZZY, "of", ALL)));
    }

    // ---------------- gợi ý "Có phải bạn muốn tìm" ----------------

    @Test
    void slangSuggestionsKeepOriginalSpellingAndSkipTheQueryItself() {
        SearchService search = service(small());
        // "lool" (≤ 4 ký tự → sai 1): chỉ "lol" đủ gần; hai entry "LOL"/"lol" → lấy cách viết của id nhỏ nhất
        assertEquals(List.of("LOL"), search.suggestions(SLANG, "lool", 5));
        // query đã có trong từ điển thì không gợi ý lại chính nó
        assertEquals(List.of("LOLZ"), search.suggestions(SLANG, " LOL ", 5));
        assertEquals(List.of(), search.suggestions(SLANG, "qwertyuiop", 5));
        assertEquals(List.of(), search.suggestions(SLANG, "   ", 5));
    }

    @Test
    void slangSuggestionsAreSortedByDistanceAndLimited() {
        SlangDictionary dictionary = SlangDictionary.build(List.of(
                new SlangEntry(0, "abcxyz", List.of("x")),   // cách "abcdez" 2
                new SlangEntry(1, "abcdey", List.of("y")),   // cách 1
                new SlangEntry(2, "abcdex", List.of("z")))); // cách 1
        SearchService search = service(dictionary);
        // khoảng cách nhỏ trước, cùng khoảng cách thì theo thứ tự chữ cái
        assertEquals(List.of("abcdex", "abcdey", "abcxyz"), search.suggestions(SLANG, "abcdez", 5));
        assertEquals(List.of("abcdex"), search.suggestions(SLANG, "abcdez", 1));
    }

    @Test
    void definitionSuggestionFixesOnlyUnknownWords() {
        SearchService search = service(small());
        assertEquals(List.of("laughing out loud"), search.suggestions(DEFINITION, "Laughing out LOUND!", 5));
        // mọi từ đều có trong từ vựng → không có gì để sửa
        assertEquals(List.of(), search.suggestions(DEFINITION, "laughing back", 5));
        // có từ không sửa được → không gợi ý
        assertEquals(List.of(), search.suggestions(DEFINITION, "laughing zzzzzzzz", 5));
        assertEquals(List.of(), search.suggestions(DEFINITION, "?!", 5));
        // câu gợi ý phải thật sự ra kết quả
        assertFalse(search.search(DEFINITION, SearchMode.EXACT, "laughing out loud", ALL).isEmpty());
    }

    // ---------------- đối chiếu với cách duyệt tuần tự trên file gốc ----------------

    private static SlangDictionary original;

    @BeforeAll
    static void loadOriginal() throws Exception {
        try (InputStream in = SearchServiceTest.class.getResourceAsStream("/data/slang.txt")) {
            original = SlangDictionary.build(SlangFileParser.parse(in));
        }
    }

    /** Tập id mà index trả về phải giống hệt tập id tìm được bằng cách duyệt từng entry (O(n)). */
    @Test
    void indexedSearchMatchesBruteForceOnOriginalData() {
        SearchService search = service(original);
        Random random = new Random(7);
        List<SlangEntry> entries = new ArrayList<>(original.all());
        entries.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

        for (int round = 0; round < 60; round++) {
            SlangEntry sample = entries.get(random.nextInt(entries.size()));

            String name = Tokenizer.normalizeName(sample.getSlang());
            String namePart = name.substring(0, 1 + random.nextInt(name.length()));
            checkSlang(search, entries, SearchMode.EXACT, name, String::equals);
            checkSlang(search, entries, SearchMode.PREFIX, namePart, String::startsWith);
            checkSlang(search, entries, SearchMode.SUBSTRING, namePart, String::contains);
            checkSlang(search, entries, SearchMode.FUZZY, mutate(name, random),
                    (candidate, q) -> Levenshtein.INSTANCE.distance(candidate, q) <= SearchService.nameFuzzyDistance(q));

            List<String> words = Tokenizer.tokens(sample.getMeanings().get(0));
            if (words.isEmpty()) {
                continue;
            }
            String word = words.get(random.nextInt(words.size()));
            String two = words.size() >= 2 ? words.get(0) + " " + words.get(1) : word;
            String wordPart = word.substring(0, 1 + random.nextInt(word.length()));
            checkDefinition(search, entries, SearchMode.EXACT, two, String::equals);
            checkDefinition(search, entries, SearchMode.PREFIX, wordPart, String::startsWith);
            checkDefinition(search, entries, SearchMode.SUBSTRING, wordPart, String::contains);
            checkDefinition(search, entries, SearchMode.FUZZY, two, (w, t) ->
                    Levenshtein.INSTANCE.distance(w, t) <= SearchService.wordFuzzyDistance(t));
        }
    }

    /** Đổi ngẫu nhiên một ký tự để tạo lỗi gõ. */
    private static String mutate(String text, Random random) {
        char[] chars = text.toCharArray();
        chars[random.nextInt(chars.length)] = (char) ('a' + random.nextInt(26));
        return new String(chars);
    }

    private static void checkSlang(SearchService search, List<SlangEntry> entries, SearchMode mode, String query,
            BiPredicate<String, String> nameMatches) {
        String q = Tokenizer.normalizeName(query);
        Set<Integer> expected = new HashSet<>();
        for (SlangEntry entry : entries) {
            if (nameMatches.test(Tokenizer.normalizeName(entry.getSlang()), q)) {
                expected.add(entry.getId());
            }
        }
        assertEquals(expected, new HashSet<>(ids(search.search(SLANG, mode, query, ALL))),
                "SLANG " + mode + " '" + query + "'");
    }

    /** Duyệt tuần tự: entry khớp khi MỌI từ của query khớp ít nhất một từ trong definition. */
    private static void checkDefinition(SearchService search, List<SlangEntry> entries, SearchMode mode, String query,
            BiPredicate<String, String> wordMatches) {
        Set<String> queryWords = new HashSet<>(Tokenizer.tokens(query));
        Set<Integer> expected = new HashSet<>();
        for (SlangEntry entry : entries) {
            Set<String> words = new HashSet<>();
            entry.getMeanings().forEach(m -> words.addAll(Tokenizer.tokens(m)));
            boolean all = queryWords.stream().allMatch(t -> words.stream().anyMatch(w -> wordMatches.test(w, t)));
            if (all) {
                expected.add(entry.getId());
            }
        }
        SearchOutcome outcome = search.search(DEFINITION, mode, query, ALL);
        assertEquals(expected, new HashSet<>(ids(outcome)), "DEFINITION " + mode + " '" + query + "'");
        assertEquals(expected.size(), outcome.totalMatches());
        assertFalse(outcome.isTruncated());
    }
}
