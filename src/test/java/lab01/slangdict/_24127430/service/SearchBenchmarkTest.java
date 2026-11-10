package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.tools.BigDataGenerator;

/**
 * Yêu cầu của đề: với 100.000 slang, mỗi chức năng tìm kiếm phải chạy dưới 1 giây.
 * Đo <b>lần gọi đầu tiên</b> của mỗi query (chưa warm-up JIT) để ra con số sát với lần bấm đầu tiên trên UI.
 * Bảng thời gian được in ra console khi chạy test.
 */
class SearchBenchmarkTest {

    private static final int SIZE = 100_000;
    private static final long LIMIT_MS = 1000;
    /** Số kết quả UI sẽ hiển thị; vẫn phải chấm điểm + sắp xếp toàn bộ kết quả khớp. */
    private static final int UI_LIMIT = 1000;

    private static SlangDictionary big;
    private static long buildMillis;

    @BeforeAll
    static void buildBigDictionary() throws Exception {
        long start = System.nanoTime();
        big = SlangDictionary.build(BigDataGenerator.generate(
                BigDataGenerator.loadBundledOriginal(), SIZE, BigDataGenerator.DEFAULT_SEED));
        buildMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Dọn rác còn lại từ lúc build (và từ các test chạy trước) để lần đo đầu tiên không dính một lần
        // GC dồn ứ. Đã gặp: chạy cả bộ test, query đầu tiên đo ra ~960 ms dù chạy riêng chỉ ~2 ms.
        // Trên app thật cũng vậy: người dùng chỉ bấm tìm sau khi load xong vài giây.
        System.gc();
    }

    private record Case(SearchTarget target, SearchMode mode, String query) {
    }

    @Test
    void everySearchOn100kIsUnderOneSecond() {
        assertEquals(SIZE, big.size());
        SearchService search = new SearchService(() -> big);

        // Mỗi tổ hợp target × mode, gồm cả query "nặng" nhất (khớp rất nhiều entry: "a", "e")
        List<Case> cases = List.of(
                new Case(SearchTarget.SLANG, SearchMode.EXACT, "lol"),
                new Case(SearchTarget.SLANG, SearchMode.PREFIX, "lo"),
                new Case(SearchTarget.SLANG, SearchMode.PREFIX, "a"),
                new Case(SearchTarget.SLANG, SearchMode.SUBSTRING, "o"),
                new Case(SearchTarget.SLANG, SearchMode.FUZZY, "lool"),
                new Case(SearchTarget.SLANG, SearchMode.FUZZY, "lmaoo"),
                new Case(SearchTarget.DEFINITION, SearchMode.EXACT, "laugh out loud"),
                new Case(SearchTarget.DEFINITION, SearchMode.EXACT, "the"),
                new Case(SearchTarget.DEFINITION, SearchMode.PREFIX, "lau"),
                new Case(SearchTarget.DEFINITION, SearchMode.PREFIX, "a"),
                new Case(SearchTarget.DEFINITION, SearchMode.SUBSTRING, "aug"),
                new Case(SearchTarget.DEFINITION, SearchMode.SUBSTRING, "e"),
                new Case(SearchTarget.DEFINITION, SearchMode.FUZZY, "lauhg"),
                new Case(SearchTarget.DEFINITION, SearchMode.FUZZY, "laugh out lound"));

        List<String> failures = new ArrayList<>();
        System.out.printf("%n[Benchmark] %,d slang, build index %,d ms%n", big.size(), buildMillis);
        System.out.printf("  %-10s %-9s %-18s %9s %10s%n", "target", "mode", "query", "matches", "ms");
        for (Case c : cases) {
            SearchOutcome outcome = search.search(c.target(), c.mode(), c.query(), UI_LIMIT);
            System.out.printf("  %-10s %-9s %-18s %,9d %10.2f%n",
                    c.target(), c.mode(), "\"" + c.query() + "\"", outcome.totalMatches(), outcome.elapsedMillis());
            assertFalse(outcome.isEmpty(), "benchmark query should find something: " + c);
            if (outcome.elapsedMillis() >= LIMIT_MS) {
                failures.add(c + " took " + outcome.elapsedMillis() + " ms");
            }
        }
        assertTrue(failures.isEmpty(), "Searches over " + LIMIT_MS + " ms: " + failures);
    }
}
