package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.io.HistoryStore;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.testutil.ScratchDir;

class HistoryServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 11, 9, 0);

    /** Không dùng @TempDir: xem {@link ScratchDir}. */
    private Path dir;
    private Path file;
    private HistoryStore store;
    private HistoryService history;
    private final AtomicInteger changes = new AtomicInteger();

    @BeforeEach
    void createService() throws IOException {
        dir = ScratchDir.create("slangdict-history-service");
        file = dir.resolve("history.dat");
        store = new HistoryStore(file);
        history = new HistoryService(store);
        history.addChangeListener(changes::incrementAndGet);
    }

    @AfterEach
    void closeStore() {
        store.close();
        ScratchDir.deleteQuietly(dir);
    }

    private static HistoryItem slang(String query, int minute) {
        return new HistoryItem(SearchTarget.SLANG, SearchMode.EXACT, query, T0.plusMinutes(minute));
    }

    private List<String> queries() {
        return history.items().stream().map(HistoryItem::query).toList();
    }

    /** Đọc lại từ đĩa bằng một service mới, giống lần mở app sau. */
    private HistoryService reopen() {
        store.flush();
        return new HistoryService(new HistoryStore(file));
    }

    @Test
    void newestSearchComesFirstAndSurvivesARestart() {
        history.add(slang("lol", 0));
        history.add(new HistoryItem(SearchTarget.DEFINITION, SearchMode.PREFIX, "laugh", T0.plusMinutes(1)));
        history.add(slang("brb", 2));

        assertEquals(List.of("brb", "laugh", "lol"), queries());
        assertEquals(3, changes.get(), "one notification per change");
        assertEquals(history.items(), reopen().items());
    }

    @Test
    void repeatingTheLatestSearchOnlyUpdatesItsTime() {
        history.add(slang("lol", 0));
        history.add(slang("LOL", 5)); // cùng lần tìm, khác hoa thường
        assertEquals(1, history.size());
        assertEquals(T0.plusMinutes(5), history.get(0).time());

        history.add(new HistoryItem(SearchTarget.SLANG, SearchMode.FUZZY, "lol", T0)); // khác mode → dòng mới
        history.add(slang("lol", 6)); // trùng một dòng cũ nhưng không phải dòng mới nhất → vẫn thêm
        assertEquals(3, history.size());
    }

    @Test
    void keepsAtMostMaxItemsDroppingTheOldest() {
        int total = HistoryService.MAX_ITEMS + 5;
        for (int i = 0; i < total; i++) {
            history.add(slang("q" + i, i));
        }
        assertEquals(HistoryService.MAX_ITEMS, history.size());
        assertEquals("q" + (total - 1), history.get(0).query());
        assertEquals("q5", history.get(HistoryService.MAX_ITEMS - 1).query()); // q0..q4 bị bỏ
    }

    @Test
    void removeDeletesTheGivenRowsAndIgnoresInvalidOnes() {
        for (String q : List.of("a", "b", "c", "d", "e")) {
            history.add(slang(q, 0));
        }
        changes.set(0);
        // danh sách hiện tại: e d c b a
        history.remove(3, 0, 3, 99, -1); // trùng, ngoài phạm vi → bỏ qua
        assertEquals(List.of("d", "c", "a"), queries());
        assertEquals(1, changes.get());

        history.remove(42);
        history.remove();
        assertEquals(1, changes.get(), "nothing removed → no notification, no save");
        assertEquals(List.of("d", "c", "a"), reopen().items().stream().map(HistoryItem::query).toList());
    }

    @Test
    void clearEmptiesTheHistoryOnDiskToo() {
        history.add(slang("lol", 0));
        history.clear();
        assertEquals(0, history.size());
        assertEquals(0, reopen().size());

        changes.set(0);
        history.clear();
        assertEquals(0, changes.get(), "clearing an empty history does nothing");
    }

    @Test
    void itemsViewIsReadOnly() {
        history.add(slang("lol", 0));
        assertThrows(UnsupportedOperationException.class, () -> history.items().clear());
    }

    @Test
    void damagedFileStartsEmptyAndIsReplacedOnTheNextChange() throws IOException {
        Files.write(file, new byte[] {1, 2, 3, 4, 5});
        HistoryService damaged = new HistoryService(store);
        assertEquals(0, damaged.size());

        damaged.add(slang("lol", 0));
        assertEquals(List.of("lol"), reopen().items().stream().map(HistoryItem::query).toList());
    }

    @Test
    void anOversizedFileIsTrimmedWhenLoaded() throws IOException {
        List<HistoryItem> many = new ArrayList<>();
        for (int i = 0; i < HistoryService.MAX_ITEMS + 50; i++) {
            many.add(slang("q" + i, i));
        }
        store.save(many);
        HistoryService loaded = new HistoryService(store);
        assertEquals(HistoryService.MAX_ITEMS, loaded.size());
        assertTrue(loaded.items().equals(many.subList(0, HistoryService.MAX_ITEMS)), "keeps the newest (first) rows");
    }
}
