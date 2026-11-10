package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ChangeResult;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.testutil.ScratchDir;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.dialogs.EntryEditorDialog.Draft;

/**
 * F4, F5, F6 từ đầu tới cuối như trong app: hành động → (dialog giả) → DictionaryService → màn hình Search hiện kết
 * quả. Dialog được thay bằng câu trả lời soạn sẵn ({@link ScriptedPrompts}), tìm kiếm chạy ngay trên luồng gọi.
 */
class EntryActionsTest {

    private Path dir;
    private DictionaryStore store;
    private DictionaryService service;
    private SearchPanel panel;
    private EntryActions actions;
    private final ScriptedPrompts prompts = new ScriptedPrompts();
    private final List<Feature> navigated = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        dir = ScratchDir.create("slangdict-entry-actions");
        store = new DictionaryStore(dir.resolve("dictionary.dat"), dir.resolve("slang.txt"));
        service = new DictionaryService(SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "LMAO", List.of("Laughing my ass off")),
                new SlangEntry(2, "BRB", List.of("Be right back")))), store);
        onEdt(() -> {
            panel = new SearchPanel(new SearchService(service::current), navigated::add, Runnable::run);
            actions = new EntryActions(service, panel, navigated::add, prompts);
        });
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
        store.close();
        ScratchDir.deleteQuietly(dir);
    }

    // ===================== F4 Thêm =====================

    @Test
    void addShowsTheNewEntrySelectedOnTheSearchScreen() throws Exception {
        prompts.drafts.add(Optional.of(new Draft("yeet", List.of("To throw"))));

        onEdt(actions::add);

        assertEquals(1, service.entriesNamed("yeet").size());
        assertTrue(navigated.contains(Feature.SEARCH_SLANG)); // panel chưa hiện → được đưa về màn hình Search
        assertEquals(List.of("yeet"), get(panel::slangColumn));
        assertEquals("yeet", get(() -> panel.selectedEntry().orElseThrow().getSlang()));
        assertEquals("✓ " + Messages.format("notice.added", "yeet"), get(panel::noticeText));
    }

    @Test
    void addSuggestsTheSlangQueryThatFoundNothing() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "yeet"));
        prompts.drafts.add(Optional.empty()); // hủy

        onEdt(actions::add);

        assertEquals("yeet", prompts.initials.get(0).slang());
        assertEquals(3, service.size());
    }

    @Test
    void backFromConflictReopensEditorWithWhatWasTyped() throws Exception {
        Draft typed = new Draft("lol", List.of("Lots of love"));
        prompts.drafts.add(Optional.of(typed));
        prompts.decisions.add(Optional.empty()); // "Quay lại sửa"
        prompts.drafts.add(Optional.empty());    // rồi hủy

        onEdt(actions::add);

        assertEquals(2, prompts.initials.size());
        assertEquals(typed, prompts.initials.get(1));
        assertEquals(List.of("Laughing out loud"), service.get(0).getMeanings());
        assertEquals(1, prompts.conflicts.get(0).existing().size());
    }

    @Test
    void addMeaningKeepsTheCurrentSearchAndSelectsTheEntry() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.PREFIX, "l"));
        prompts.drafts.add(Optional.of(new Draft("LOL", List.of("Lots of love"))));
        prompts.decide(ConflictResolution.ADD_MEANING);

        onEdt(actions::add);

        assertEquals(List.of("Laughing out loud", "Lots of love"), service.get(0).getMeanings());
        assertEquals(List.of("LMAO", "LOL"), get(panel::slangColumn)); // vẫn là kết quả của "l"
        SlangEntry selected = get(() -> panel.selectedEntry().orElseThrow());
        assertEquals(0, selected.getId());
        assertEquals(2, selected.getMeanings().size()); // bản mới, không phải bản cũ trước khi sửa
        assertTrue(get(panel::noticeText).contains(Messages.format("notice.meaningsAdded", "LOL")));
    }

    @Test
    void newSearchClearsTheNotice() throws Exception {
        prompts.drafts.add(Optional.of(new Draft("yeet", List.of("To throw"))));
        onEdt(actions::add);
        assertFalse(get(panel::noticeText).isEmpty());

        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.PREFIX, "b"));

        assertEquals("", get(panel::noticeText));
    }

    // ===================== F5 Sửa =====================

    @Test
    void editWithoutSelectionShowsHintInsteadOfDialog() throws Exception {
        onEdt(actions::edit);

        assertTrue(prompts.initials.isEmpty());
        assertTrue(get(panel::noticeText).contains(Messages.get("notice.selectToEdit")));
    }

    @Test
    void renameRevealsTheEntryUnderItsNewName() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.PREFIX, "brb"));
        prompts.drafts.add(Optional.of(new Draft("brb2", List.of("Be right back", "Bathroom break"))));

        onEdt(actions::edit);

        assertEquals(new Draft("BRB", List.of("Be right back")), prompts.initials.get(0)); // dữ liệu cũ điền sẵn
        assertEquals("brb2", service.get(2).getSlang());
        // "brb" (tiền tố) vẫn khớp "brb2" → giữ lần tìm, chọn đúng entry
        assertEquals(List.of("brb2"), get(panel::slangColumn));
        assertEquals(2, get(() -> panel.selectedEntry().orElseThrow().getId()));
    }

    @Test
    void renameOutOfTheCurrentResultsSearchesTheNewName() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "brb"));
        prompts.drafts.add(Optional.of(new Draft("afk", List.of("Away from keyboard"))));

        onEdt(actions::edit);

        assertEquals(List.of("afk"), get(panel::slangColumn));
        assertEquals("afk", get(() -> panel.queryField().getText()));
        assertEquals(2, get(() -> panel.selectedEntry().orElseThrow().getId()));
    }

    @Test
    void renameOntoAnotherNameAsksAndCanOverwrite() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "lmao"));
        prompts.drafts.add(Optional.of(new Draft("lol", List.of("Laughing my ass off"))));
        prompts.decide(ConflictResolution.OVERWRITE);

        onEdt(actions::edit);

        Conflict conflict = prompts.conflicts.get(0);
        assertEquals(1, conflict.editing().getId());
        assertNull(service.get(0));
        assertEquals("lol", service.get(1).getSlang());
        assertEquals(List.of("lol"), get(panel::slangColumn));
        assertTrue(get(panel::noticeText).contains(Messages.format("notice.replacedOther", "lol")));
    }

    @Test
    void editingUsesTheSameNameCountExcludingItself() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "lol"));
        prompts.drafts.add(Optional.empty());

        onEdt(actions::edit);

        ToIntFunction<String> count = prompts.sameNameCounters.get(0);
        assertEquals(0, count.applyAsInt("LOL")); // chính nó không tính
        assertEquals(1, count.applyAsInt("brb"));
    }

    // ===================== F6 Xóa =====================

    @Test
    void deleteAsksThenRemovesAndSelectsTheNextRow() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.PREFIX, "l"));
        onEdt(() -> panel.selectRow(0)); // LMAO
        prompts.confirmations.add(true);

        onEdt(actions::delete);

        assertEquals(1, prompts.deleteAsked.get(0).getId());
        assertNull(service.get(1));
        assertEquals(List.of("LOL"), get(panel::slangColumn));
        assertEquals(0, get(() -> panel.table().getSelectedRow()));
        assertTrue(get(panel::noticeText).contains(Messages.format("notice.deleted", "LMAO")));
        assertEquals(2, DictionaryStore.load(flushed()).size()); // đã lưu xuống .dat
    }

    @Test
    void deleteCancelledKeepsEverything() throws Exception {
        onEdt(() -> panel.search(SearchTarget.SLANG, SearchMode.EXACT, "brb"));
        prompts.confirmations.add(false);

        onEdt(actions::delete);

        assertEquals(3, service.size());
        assertEquals(List.of("BRB"), get(panel::slangColumn));
    }

    @Test
    void deleteWithoutSelectionShowsHint() throws Exception {
        onEdt(actions::delete);

        assertTrue(prompts.deleteAsked.isEmpty());
        assertTrue(get(panel::noticeText).contains(Messages.get("notice.selectToDelete")));
    }

    // ===================== Chữ thông báo =====================

    @Test
    void noticeKeysMatchWhatHappened() {
        SlangEntry e = new SlangEntry(0, "x", List.of("y"));
        assertEquals("notice.added", EntryActions.addNoticeKey(result(ChangeResult.Status.CHANGED, null, e)));
        assertEquals("notice.addedDuplicate", EntryActions.addNoticeKey(
                result(ChangeResult.Status.CHANGED, ConflictResolution.NEW_ENTRY, e)));
        assertEquals("notice.unchangedMeanings", EntryActions.addNoticeKey(
                result(ChangeResult.Status.UNCHANGED, ConflictResolution.ADD_MEANING, e)));
        assertEquals("notice.unchanged", EntryActions.editNoticeKey(result(ChangeResult.Status.UNCHANGED, null, e)));
        assertEquals("notice.merged", EntryActions.editNoticeKey(
                result(ChangeResult.Status.CHANGED, ConflictResolution.ADD_MEANING, e)));
        for (String key : List.of("notice.added", "notice.unchanged", "notice.merged")) {
            assertFalse(Messages.get(key).startsWith("!"), key);
        }
    }

    private static ChangeResult result(ChangeResult.Status status, ConflictResolution resolution, SlangEntry entry) {
        return new ChangeResult(status, resolution, entry, List.of());
    }

    // ===================== Tiện ích =====================

    private Path flushed() {
        store.flush();
        return store.dictionaryFile();
    }

    /** Chạy trên EDT rồi chờ các kết quả tìm kiếm (được đưa về EDT bằng invokeLater) hiện xong. */
    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
        for (int i = 0; i < 3; i++) { // reveal có thể tìm thêm một lần nữa
            SwingUtilities.invokeAndWait(() -> { });
        }
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

    /** Câu trả lời soạn sẵn cho từng dialog, đồng thời ghi lại dialog đã được mở với dữ liệu gì. */
    private static final class ScriptedPrompts implements EntryActions.Prompts {
        final Deque<Optional<Draft>> drafts = new ArrayDeque<>();
        final Deque<Optional<ConflictResolution>> decisions = new ArrayDeque<>();
        final Deque<Boolean> confirmations = new ArrayDeque<>();

        final List<Draft> initials = new ArrayList<>();
        final List<ToIntFunction<String>> sameNameCounters = new ArrayList<>();
        final List<Conflict> conflicts = new ArrayList<>();
        final List<SlangEntry> deleteAsked = new ArrayList<>();

        void decide(ConflictResolution resolution) {
            decisions.add(Optional.of(resolution));
        }

        @Override
        public Optional<Draft> editEntry(Component parent, boolean editing, Draft initial,
                                         ToIntFunction<String> sameName) {
            initials.add(initial);
            sameNameCounters.add(sameName);
            return drafts.isEmpty() ? Optional.empty() : drafts.poll();
        }

        @Override
        public Optional<Decision> resolveConflict(Component parent, Conflict conflict) {
            conflicts.add(conflict);
            Optional<ConflictResolution> choice = decisions.isEmpty() ? Optional.empty() : decisions.poll();
            return choice.map(resolution -> new Decision(resolution, conflict.existing().get(0)));
        }

        @Override
        public boolean confirmDelete(Component parent, SlangEntry entry) {
            deleteAsked.add(entry);
            return !confirmations.isEmpty() && confirmations.poll();
        }
    }
}
