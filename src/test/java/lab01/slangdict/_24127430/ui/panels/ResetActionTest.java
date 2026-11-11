package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.HistoryStore;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.service.HistoryService;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.testutil.ScratchDir;

/**
 * F7 từ đầu tới cuối như trong app: xác nhận (giả) → build từ file gốc → thay từ điển → màn hình Search tìm lại,
 * lịch sử bị xóa nếu được chọn. "Luồng nền" và "EDT" đều chạy ngay trên luồng gọi.
 */
class ResetActionTest {

    private static final int ORIGINAL_SIZE = 7638;

    private Path dir;
    private DictionaryStore store;
    private HistoryStore historyStore;
    private DictionaryService service;
    private HistoryService history;
    private SearchPanel panel;
    private ResetAction action;
    private final FakeUi ui = new FakeUi();

    @BeforeEach
    void setUp() throws Exception {
        Messages.setLocale(Messages.VIETNAMESE);
        dir = ScratchDir.create("slangdict-reset");
        store = new DictionaryStore(dir.resolve("dictionary.dat"), dir.resolve("slang.txt"));
        historyStore = new HistoryStore(dir.resolve("history.dat"));
        history = new HistoryService(historyStore);
        service = new DictionaryService(SlangDictionary.build(List.of(
                new SlangEntry(0, "yeet", List.of("To throw")),
                new SlangEntry(1, "LOL", List.of("Lots of love")))), store);
        onEdt(() -> {
            panel = new SearchPanel(new SearchService(service::current), feature -> { }, Runnable::run);
            action = new ResetAction(service, history, panel, ui, Runnable::run, Runnable::run);
            history.add(new HistoryItem(SearchTarget.SLANG, SearchMode.PREFIX, "yeet", LocalDateTime.now()));
            history.add(new HistoryItem(SearchTarget.SLANG, SearchMode.EXACT, "lol", LocalDateTime.now()));
        });
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
        historyStore.close();
        store.close();
        ScratchDir.deleteQuietly(dir);
    }

    @Test
    void cancelChangesNothing() throws Exception {
        ui.answer = Optional.empty();
        SlangDictionary before = service.current();

        onEdt(action);

        assertSame(before, service.current());
        assertEquals(2, history.size());
        assertTrue(ui.busy.isEmpty(), "window was never locked");
        assertNull(ui.doneSize);
    }

    @Test
    void confirmDialogShowsCurrentSizes() throws Exception {
        ui.answer = Optional.empty();
        onEdt(action);
        assertEquals(List.of(2, 2), ui.asked);
        assertTrue(ui.askedSource.startsWith("jar:"));
    }

    @Test
    void resetKeepsHistoryUnlessAsked() throws Exception {
        ui.answer = Optional.of(false);
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "yeet"));
        assertEquals(List.of("yeet"), get(panel::slangColumn));
        int historyBefore = history.size();

        onEdt(action);

        assertEquals(ORIGINAL_SIZE, service.size());
        assertEquals(historyBefore, history.size());
        // Màn hình Search tìm lại trên dữ liệu gốc: "yeet" (do người dùng thêm) không còn
        assertTrue(get(panel::slangColumn).isEmpty());
        assertTrue(get(panel::noticeText).contains("7.638"), get(panel::noticeText));
        assertEquals(List.of(true, false), ui.busy, "locked while building, unlocked afterwards");
        assertEquals(ORIGINAL_SIZE, ui.doneSize);
        assertFalse(ui.doneHistoryCleared);
    }

    @Test
    void resetCanAlsoClearTheHistory() throws Exception {
        ui.answer = Optional.of(true);

        onEdt(action);

        assertEquals(ORIGINAL_SIZE, service.size());
        assertEquals(0, history.size());
        assertTrue(ui.doneHistoryCleared);
    }

    @Test
    void resetIsSavedToDisk() throws Exception {
        ui.answer = Optional.of(false);
        onEdt(action);
        store.flush();
        assertEquals(ORIGINAL_SIZE, DictionaryStore.load(store.dictionaryFile()).size());
    }

    @Test
    void brokenOriginalFileShowsAnErrorAndKeepsEverything() throws Exception {
        Files.writeString(dir.resolve("slang.txt"), "abc`First\n`no slang\n");
        ui.answer = Optional.of(true);
        SlangDictionary before = service.current();

        onEdt(action);

        assertSame(before, service.current());
        assertEquals(2, history.size(), "history is only cleared after a successful reset");
        assertEquals(List.of(true, false), ui.busy);
        assertTrue(ui.error.contains("2"), ui.error); // thông báo có số dòng lỗi
        assertNull(ui.doneSize);

        // Sửa file rồi thử lại: lần này được
        Files.writeString(dir.resolve("slang.txt"), "abc`First\n");
        onEdt(action);
        assertEquals(1, service.size());
    }

    // ===================== Hỗ trợ =====================

    /** Giao diện giả: trả lời soạn sẵn, ghi lại những gì được gọi. */
    private static final class FakeUi implements ResetAction.Ui {
        Optional<Boolean> answer = Optional.empty();
        final List<Integer> asked = new ArrayList<>();
        String askedSource;
        final List<Boolean> busy = new ArrayList<>();
        Integer doneSize;
        boolean doneHistoryCleared;
        String error;

        @Override
        public Optional<Boolean> confirm(int dictionarySize, int historySize, String source) {
            asked.add(dictionarySize);
            asked.add(historySize);
            askedSource = source;
            return answer;
        }

        @Override
        public void setBusy(boolean on) {
            busy.add(on);
        }

        @Override
        public void showDone(int size, long elapsedMillis, boolean historyCleared) {
            doneSize = size;
            doneHistoryCleared = historyCleared;
        }

        @Override
        public void showError(String message) {
            error = message;
        }
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static <T> T get(Callable<T> read) throws Exception {
        List<T> box = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                box.add(read.call());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return box.get(0);
    }
}
