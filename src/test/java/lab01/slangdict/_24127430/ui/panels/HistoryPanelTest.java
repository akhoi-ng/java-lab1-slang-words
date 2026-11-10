package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.io.HistoryStore;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.HistoryService;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.testutil.ScratchDir;
import lab01.slangdict._24127430.ui.Feature;

/** Màn hình Lịch sử, không mở cửa sổ: mọi thao tác chạy trên EDT bằng invokeAndWait. */
class HistoryPanelTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 11, 14, 30, 5);

    /** Không dùng @TempDir: xem {@link ScratchDir}. */
    private Path dir;
    private HistoryStore store;
    private HistoryService history;
    private HistoryPanel panel;
    private final List<HistoryItem> replayed = new ArrayList<>();

    @BeforeEach
    void createPanel() throws Exception {
        Messages.setLocale(Messages.VIETNAMESE);
        dir = ScratchDir.create("slangdict-history-panel");
        store = new HistoryStore(dir.resolve("history.dat"));
        history = new HistoryService(store);
        onEdt(() -> panel = new HistoryPanel(history, replayed::add));
    }

    @AfterEach
    void cleanUp() {
        store.close();
        ScratchDir.deleteQuietly(dir);
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    private void addOnEdt(SearchTarget target, SearchMode mode, String query, int minute) throws Exception {
        onEdt(() -> history.add(new HistoryItem(target, mode, query, T0.plusMinutes(minute))));
    }

    private String cell(int row, int column) throws Exception {
        AtomicReference<String> value = new AtomicReference<>();
        onEdt(() -> value.set(String.valueOf(panel.table().getValueAt(row, column))));
        return value.get();
    }

    @Test
    void showsAnEmptyStateUntilSomethingIsSearched() throws Exception {
        assertTrue(panel.showingEmptyState());
        addOnEdt(SearchTarget.SLANG, SearchMode.PREFIX, "lol", 0);

        assertFalse(panel.showingEmptyState());
        assertEquals(1, panel.table().getRowCount());
        assertTrue(panel.countText().startsWith("1 "));
        assertEquals("11/11/2026  14:30:05", cell(0, 0));
        assertEquals("Slang", cell(0, 1));
        assertEquals("Tiền tố", cell(0, 2));
        assertEquals("lol", cell(0, 3));
    }

    @Test
    void cellsAndHeadersFollowTheLanguage() throws Exception {
        addOnEdt(SearchTarget.DEFINITION, SearchMode.EXACT, "laugh", 0);
        onEdt(() -> Messages.setLocale(Messages.ENGLISH));

        assertEquals("Nov 11, 2026  2:30:05 PM", cell(0, 0));
        assertEquals("Definition", cell(0, 1));
        assertEquals("Exact", cell(0, 2));
        assertEquals("Keyword", panel.table().getColumnModel().getColumn(3).getHeaderValue());
        assertTrue(panel.countText().startsWith("1 search ·"), panel.countText()); // số ít, không phải "1 searches"
        addOnEdt(SearchTarget.SLANG, SearchMode.EXACT, "lol", 1);
        assertTrue(panel.countText().startsWith("2 searches ·"), panel.countText());
    }

    @Test
    void replaySendsTheSelectedSearch() throws Exception {
        addOnEdt(SearchTarget.SLANG, SearchMode.EXACT, "old", 0);
        addOnEdt(SearchTarget.DEFINITION, SearchMode.FUZZY, "lauhg", 1);
        onEdt(() -> {
            panel.onShown(Feature.HISTORY); // chọn sẵn dòng mới nhất
            panel.replaySelected();
            panel.table().setRowSelectionInterval(1, 1);
            panel.replaySelected();
        });
        assertEquals(List.of("lauhg", "old"), replayed.stream().map(HistoryItem::query).toList());
        assertEquals(SearchMode.FUZZY, replayed.get(0).mode());
    }

    @Test
    void deleteRemovesSelectedRowsAndSelectsTheNextOne() throws Exception {
        for (String q : List.of("a", "b", "c", "d")) {
            addOnEdt(SearchTarget.SLANG, SearchMode.EXACT, q, 0);
        }
        // bảng: d c b a → chọn "c" và "b"
        onEdt(() -> {
            panel.table().setRowSelectionInterval(1, 2);
            panel.deleteSelected();
        });
        assertEquals(List.of("d", "a"), history.items().stream().map(HistoryItem::query).toList());
        assertEquals(1, panel.table().getSelectedRow(), "the row that moved into place is selected");

        onEdt(() -> {
            panel.deleteSelected(); // xóa "a" (dòng cuối) → "d" được chọn → xóa nốt
            panel.deleteSelected();
        });
        assertTrue(panel.showingEmptyState());
    }

    @Test
    void clearAllAsksFirst() throws Exception {
        addOnEdt(SearchTarget.SLANG, SearchMode.EXACT, "lol", 0);
        List<String> questions = new ArrayList<>();
        onEdt(() -> {
            panel.setConfirmer(message -> {
                questions.add(message);
                return false; // bấm "Không"
            });
            panel.clearAll();
        });
        assertEquals(1, history.size());
        assertTrue(questions.get(0).contains("1"), questions.get(0));

        onEdt(() -> {
            panel.setConfirmer(message -> true);
            panel.clearAll();
        });
        assertEquals(0, history.size());
        assertTrue(panel.showingEmptyState());
    }

    /** Nối dây giống App: tìm ở màn hình Search → có lịch sử; tìm lại từ lịch sử → Search tìm đúng như cũ. */
    @Test
    void endToEndWithTheSearchScreen() throws Exception {
        SlangDictionary dictionary = SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "BRB", List.of("Be right back"))));
        List<Feature> navigated = new ArrayList<>();
        AtomicReference<SearchPanel> search = new AtomicReference<>();
        AtomicReference<HistoryPanel> wired = new AtomicReference<>();
        onEdt(() -> {
            SearchPanel searchPanel = new SearchPanel(new SearchService(() -> dictionary), navigated::add, Runnable::run);
            searchPanel.setSearchListener(history::add);
            search.set(searchPanel);
            wired.set(new HistoryPanel(history,
                    item -> searchPanel.search(item.target(), item.mode(), item.query())));
        });

        onEdt(() -> {
            search.get().definitionTab().doClick();
            search.get().queryField().setText("back");
            search.get().queryField().postActionEvent(); // Enter
        });
        flushEdt();
        assertEquals(1, history.size());
        assertEquals(SearchTarget.DEFINITION, history.get(0).target());

        // Người dùng chuyển về tab Slang, gõ thứ khác... rồi tìm lại từ lịch sử
        onEdt(() -> {
            search.get().queryField().setText("zzz");
            wired.get().onShown(Feature.HISTORY);
            wired.get().replaySelected();
        });
        flushEdt();
        assertEquals(SearchTarget.DEFINITION, search.get().target());
        assertEquals("back", search.get().queryField().getText());
        assertEquals("BRB", search.get().table().getValueAt(0, 0));
        assertEquals(Feature.SEARCH_DEFINITION, navigated.get(navigated.size() - 1));
        assertEquals(1, history.size(), "searching the same thing again only refreshes the latest row");
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(action);
    }

    private static void flushEdt() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
