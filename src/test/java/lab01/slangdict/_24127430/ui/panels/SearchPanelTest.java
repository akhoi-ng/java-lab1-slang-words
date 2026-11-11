package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.ui.Feature;

/**
 * Kiểm tra màn hình Search mà không mở cửa sổ nào: tạo panel trên EDT, tìm ngay trên luồng gọi
 * (executor {@code Runnable::run}) rồi chờ EDT xử lý xong phần hiển thị kết quả.
 */
class SearchPanelTest {

    private SearchPanel panel;
    private final List<Feature> navigated = new ArrayList<>();
    private final List<HistoryItem> history = new ArrayList<>();

    @BeforeEach
    void createPanel() throws Exception {
        SlangDictionary dictionary = SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "lol", List.of("Lots of love")),
                new SlangEntry(2, "LOLZ", List.of("Plural of lol, laugh")),
                new SlangEntry(3, "BRB", List.of("Be right back")),
                new SlangEntry(4, "&&", List.of("And & and"))));
        onEdt(() -> {
            panel = new SearchPanel(new SearchService(() -> dictionary), navigated::add, Runnable::run);
            panel.setSearchListener(history::add);
        });
    }

    @AfterEach
    void restoreLocale() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    @Test
    void enterSearchesShowsBestMatchAndRecordsHistory() throws Exception {
        typeAndPressEnter("lol"); // mặc định: SLANG + PREFIX

        assertEquals(List.of("LOL", "lol", "LOLZ"), slangColumn());
        assertEquals(0, panel.table().getSelectedRow()); // kết quả tốt nhất được chọn sẵn
        assertTrue(panel.detailText().startsWith("1. Laughing out loud"));
        assertTrue(panel.statusText().startsWith("3 "));
        assertEquals(1, history.size());
        HistoryItem item = history.get(0);
        assertEquals(SearchTarget.SLANG, item.target());
        assertEquals(SearchMode.PREFIX, item.mode());
        assertEquals("lol", item.query());
    }

    @Test
    void searchAsYouTypeIsRecordedOnceTheUserStopsTyping() throws Exception {
        type("l");
        type("lo");
        assertTrue(history.isEmpty(), "chữ gõ dở không vào lịch sử");
        type("lol");
        assertEquals(List.of("LOL", "lol", "LOLZ"), slangColumn());
        assertTrue(history.isEmpty());

        onEdt(panel::fireHistoryTimer); // người dùng ngừng gõ HISTORY_DELAY_MS

        assertEquals(1, history.size());
        HistoryItem item = history.get(0);
        assertEquals(SearchTarget.SLANG, item.target());
        assertEquals(SearchMode.PREFIX, item.mode());
        assertEquals("lol", item.query());
        onEdt(panel::fireHistoryTimer);
        assertEquals(1, history.size(), "mỗi kết quả chỉ ghi một lần");
    }

    @Test
    void typingAgainBeforeTheDelayCancelsTheRecord() throws Exception {
        type("lo");
        assertTrue(panel.historyPending());
        onEdt(() -> panel.queryField().setText("lol")); // gõ tiếp trước khi hết 1,5 s
        assertFalse(panel.historyPending());

        onEdt(() -> panel.queryField().setText(""));
        assertFalse(panel.historyPending(), "xóa ô tìm thì không ghi gì");
        assertTrue(history.isEmpty());
    }

    @Test
    void movingIntoTheResultsRecordsRightAway() throws Exception {
        type("lol");
        onEdt(() -> { // như bấm chuột vào bảng / nhấn ↓ (test không mở cửa sổ nên không có focus thật)
            FocusEvent gained = new FocusEvent(panel.table(), FocusEvent.FOCUS_GAINED);
            for (FocusListener listener : panel.table().getFocusListeners()) {
                listener.focusGained(gained);
            }
        });
        assertEquals(List.of("lol"), history.stream().map(HistoryItem::query).collect(Collectors.toList()));
        assertFalse(panel.historyPending());
    }

    @Test
    void enterAfterTypingRecordsOnlyOnce() throws Exception {
        type("lol");
        onEdt(() -> panel.queryField().postActionEvent());
        flushEdt();
        onEdt(panel::fireHistoryTimer);
        assertEquals(1, history.size());
    }

    @Test
    void searchesThatOnlyRefreshDataAreNotRecorded() throws Exception {
        typeAndPressEnter("lol");
        onEdt(panel::refresh); // sau khi thêm/sửa/xóa
        flushEdt();
        onEdt(panel::fireHistoryTimer);
        assertEquals(1, history.size());
    }

    @Test
    void clearingTheQueryClearsTheResults() throws Exception {
        typeAndPressEnter("lol");
        onEdt(() -> panel.queryField().setText(""));
        flushEdt();
        assertEquals(0, panel.table().getRowCount());
        assertEquals(Messages.get("search.status.idle"), panel.statusText());
    }

    @Test
    void openingViaF2SwitchesToDefinitionWithoutNavigatingAgain() throws Exception {
        onEdt(() -> panel.onShown(Feature.SEARCH_DEFINITION));
        assertEquals(SearchTarget.DEFINITION, panel.target());
        assertEquals(SearchMode.SUBSTRING, panel.modeCombo().getSelectedItem()); // definition mặc định "Chứa"
        assertTrue(navigated.isEmpty());

        typeAndPressEnter("laugh");
        // "laugh" ra cả "Laughing out loud"; LOLZ có nguyên từ "laugh" nên đứng đầu
        assertEquals(List.of("LOLZ", "LOL"), slangColumn());
    }

    @Test
    void definitionKeywordMadeOfSymbolsFindsAndHighlightsTheRawText() throws Exception {
        onEdt(() -> panel.onShown(Feature.SEARCH_DEFINITION));
        typeAndPressEnter("&");
        assertEquals(List.of("&&"), slangColumn());
        assertEquals(List.of("8-9"), ranges("Rock n' & roll", SearchMode.EXACT, " & "));
        assertEquals(List.of("0-2", "3-5"), ranges(":) :)", SearchMode.FUZZY, ":)"));
    }

    @Test
    void clickingTheDefinitionTabTellsMainFrameSoTheSidebarFollows() throws Exception {
        onEdt(() -> panel.definitionTab().doClick());
        assertEquals(List.of(Feature.SEARCH_DEFINITION), navigated);
        assertEquals(SearchTarget.DEFINITION, panel.target());
    }

    @Test
    void eachTargetRemembersItsOwnMode() throws Exception {
        onEdt(() -> panel.modeCombo().setSelectedItem(SearchMode.FUZZY)); // slang → FUZZY
        onEdt(() -> panel.onShown(Feature.SEARCH_DEFINITION));
        assertEquals(SearchMode.SUBSTRING, panel.modeCombo().getSelectedItem());
        onEdt(() -> panel.onShown(Feature.SEARCH_SLANG));
        assertEquals(SearchMode.FUZZY, panel.modeCombo().getSelectedItem());
    }

    @Test
    void exactWithoutResultsSuggestsAndTheSuggestionSearches() throws Exception {
        onEdt(() -> panel.modeCombo().setSelectedItem(SearchMode.EXACT));
        typeAndPressEnter("lool");
        assertEquals(0, panel.table().getRowCount());
        assertEquals(List.of("LOL"), panel.shownSuggestions());

        onEdt(() -> panel.clickSuggestion(0));
        flushEdt();
        assertEquals("LOL", panel.queryField().getText());
        assertEquals(List.of("LOL", "lol"), slangColumn());
        assertTrue(panel.shownSuggestions().isEmpty());
        assertEquals(List.of("lool", "LOL"), history.stream().map(HistoryItem::query).collect(Collectors.toList()));
    }

    @Test
    void searchFromHistoryNavigatesAndRunsTheRequest() throws Exception {
        onEdt(() -> panel.search(SearchTarget.DEFINITION, SearchMode.PREFIX, "lov"));
        flushEdt();
        assertEquals(List.of(Feature.SEARCH_DEFINITION), navigated);
        assertEquals(SearchMode.PREFIX, panel.modeCombo().getSelectedItem());
        assertEquals(List.of("lol"), slangColumn()); // "Lots of love"
        assertEquals(1, history.size());
    }

    @Test
    void switchingLanguageRewritesTheShownStatus() throws Exception {
        onEdt(() -> Messages.setLocale(Messages.VIETNAMESE));
        typeAndPressEnter("lol");
        assertTrue(panel.statusText().contains("kết quả"), panel.statusText());
        onEdt(() -> Messages.setLocale(Messages.ENGLISH));
        assertTrue(panel.statusText().startsWith("3 results"), panel.statusText());
        assertEquals("Meanings", panel.table().getColumnModel().getColumn(1).getHeaderValue());
    }

    @Test
    void matchRangesFollowTheSearchMode() {
        String text = "The other theme";
        assertEquals(List.of("0-3"), ranges(text, SearchMode.EXACT, "the"));
        assertEquals(List.of("0-3", "10-13"), ranges(text, SearchMode.PREFIX, "THE"));
        assertEquals(List.of("0-3", "5-8", "10-13"), ranges(text, SearchMode.SUBSTRING, "the"));
        assertEquals(List.of(), ranges(text, SearchMode.FUZZY, "the"));
        assertEquals(List.of(), ranges(text, SearchMode.EXACT, "?!"));
        // nhiều từ khóa: tô từng từ; ký tự đặc biệt của regex không gây lỗi
        assertEquals(List.of("0-8", "13-17"), ranges("Laughing out loud", SearchMode.EXACT, "loud laughing"));
        assertFalse(ranges("a+b", SearchMode.SUBSTRING, "a+b").isEmpty());
    }

    // ---------------- tiện ích ----------------

    private void typeAndPressEnter(String query) throws Exception {
        onEdt(() -> {
            panel.queryField().setText(query);
            panel.queryField().postActionEvent(); // giống nhấn Enter
        });
        flushEdt();
    }

    /** Gõ vào ô tìm (mode mặc định SLANG + PREFIX tìm ngay khi gõ), không chờ debounce. */
    private void type(String query) throws Exception {
        onEdt(() -> {
            panel.queryField().setText(query);
            panel.fireLiveTimer();
        });
        flushEdt();
    }

    private List<String> slangColumn() throws Exception {
        List<String> names = new ArrayList<>();
        onEdt(() -> {
            for (int row = 0; row < panel.table().getRowCount(); row++) {
                names.add((String) panel.table().getValueAt(row, 0));
            }
        });
        return names;
    }

    private static List<String> ranges(String text, SearchMode mode, String query) {
        return SearchPanel.matchRanges(text, mode, query).stream()
                .map(range -> range[0] + "-" + range[1]).collect(Collectors.toList());
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(action);
    }

    /** Kết quả tìm được đưa lên bảng bằng invokeLater → chờ EDT chạy hết các việc đang xếp hàng. */
    private static void flushEdt() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
