package lab01.slangdict._24127430.ui.dialogs;

import static lab01.slangdict._24127430.testutil.SwingHtml.cellRendersHtml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.ConflictResolver.Operation;
import lab01.slangdict._24127430.ui.dialogs.EntryEditorDialog.Draft;

/**
 * Phần nội dung của dialog Thêm/Sửa và dialog Trùng tên, kiểm tra mà không mở cửa sổ (chỉ tạo JPanel trên EDT).
 */
class EntryDialogsTest {

    @AfterEach
    void restoreLocale() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    // ===================== EntryEditorDialog.Form =====================

    private static EntryEditorDialog.Form form(boolean editing, Draft initial) throws Exception {
        return get(() -> new EntryEditorDialog.Form(editing, initial, name -> name.equalsIgnoreCase("lol") ? 2 : 0));
    }

    @Test
    void addFormStartsWithOneEmptyMeaningRow() throws Exception {
        EntryEditorDialog.Form form = form(false, new Draft("", List.of()));
        assertEquals(List.of(""), get(form::meaningRows));
    }

    @Test
    void validInputIsReturnedTrimmed() throws Exception {
        EntryEditorDialog.Form form = form(false, new Draft("  yeet ", List.of("To throw", " ", "Excitement")));

        Optional<Draft> draft = get(form::submit);

        assertEquals("yeet", draft.orElseThrow().slang());
        // dòng trống và trùng để service lọc; ở đây chỉ cần dữ liệu đi qua nguyên vẹn
        assertEquals(List.of("To throw", " ", "Excitement"), draft.get().meanings());
        assertEquals(" ", get(form::errorText));
    }

    @Test
    void invalidInputShowsTheProblemInsteadOfReturning() throws Exception {
        EntryEditorDialog.Form noName = form(false, new Draft(" ", List.of("x")));
        assertTrue(get(noName::submit).isEmpty());
        assertEquals(Messages.get("editor.problem.name_empty"), get(noName::errorText));

        EntryEditorDialog.Form noMeaning = form(false, new Draft("ok", List.of(" ")));
        assertTrue(get(noMeaning::submit).isEmpty());
        assertEquals(Messages.get("editor.problem.no_meaning"), get(noMeaning::errorText));

        EntryEditorDialog.Form pipe = form(false, new Draft("ok", List.of("a | b")));
        assertTrue(get(pipe::submit).isEmpty());
        assertEquals(Messages.get("editor.problem.meaning_invalid"), get(pipe::errorText));
        assertEquals(0, get(() -> pipe.table().getEditingRow())); // con trỏ được đưa tới đúng dòng sai
    }

    @Test
    void duplicateWarningFollowsTheTypedName() throws Exception {
        EntryEditorDialog.Form form = form(false, new Draft("", List.of()));
        assertEquals(" ", get(form::duplicateText));

        onEdt(() -> form.nameField().setText("LOL"));
        assertTrue(get(form::duplicateText).contains(Messages.format("editor.duplicate", 2)));

        onEdt(() -> form.nameField().setText("lolz"));
        assertEquals(" ", get(form::duplicateText));
    }

    @Test
    void editingWithoutRenamingDoesNotWarn() throws Exception {
        EntryEditorDialog.Form form = form(true, new Draft("lol", List.of("Laughing out loud")));
        assertEquals(" ", get(form::duplicateText)); // tên của chính nó
        onEdt(() -> form.nameField().setText("LOL"));
        assertEquals(" ", get(form::duplicateText)); // chỉ đổi hoa/thường
    }

    @Test
    void meaningsCanBeAddedRemovedAndReordered() throws Exception {
        EntryEditorDialog.Form form = form(true, new Draft("x", List.of("one", "two", "three")));

        onEdt(() -> {
            form.table().setRowSelectionInterval(2, 2);
            form.move(-1);
        });
        assertEquals(List.of("one", "three", "two"), get(form::meaningRows));
        assertEquals(1, get(() -> form.table().getSelectedRow())); // dòng vừa đưa lên vẫn được chọn

        onEdt(() -> form.move(-1));
        onEdt(() -> form.move(-1)); // đã ở đầu → không làm gì
        assertEquals(List.of("three", "one", "two"), get(form::meaningRows));

        onEdt(() -> {
            form.table().setRowSelectionInterval(0, 1);
            form.removeSelected();
        });
        assertEquals(List.of("two"), get(form::meaningRows));

        onEdt(() -> {
            form.addMeaning(); // thêm dòng trống và mở ô sửa ở dòng đó
            ((javax.swing.JTextField) form.table().getEditorComponent()).setText("four"); // như người dùng gõ
        });
        assertEquals(1, get(() -> form.table().getEditingRow()));
        // Bấm Lưu khi ô còn đang sửa dở: chữ đang gõ vẫn được tính
        assertEquals(List.of("two", "four"), get(form::submit).orElseThrow().meanings());
        assertEquals(List.of("two", "four"), get(form::meaningRows));
    }

    @Test
    void rowNumbersAreRecomputed() throws Exception {
        EntryEditorDialog.Form form = form(true, new Draft("x", List.of("a", "b", "c")));
        onEdt(() -> {
            form.table().setRowSelectionInterval(0, 0);
            form.removeSelected();
        });
        assertEquals(1, get(() -> form.table().getValueAt(0, 0)));
        assertEquals(2, get(() -> form.table().getValueAt(1, 0)));
    }

    // ===================== ConflictDialog.Form =====================

    private static final SlangEntry GG_1 = new SlangEntry(2, "gg", List.of("Good game"));
    private static final SlangEntry GG_2 = new SlangEntry(3, "GG", List.of("Gotta go"));

    @Test
    void conflictFormReturnsTheChosenOptionAndTarget() throws Exception {
        List<Decision> chosen = new ArrayList<>();
        Conflict conflict = new Conflict(Operation.ADD, "Gg", List.of("Great game"), List.of(GG_1, GG_2), null);
        ConflictDialog.Form form = get(() -> new ConflictDialog.Form(conflict, chosen::add));

        assertTrue(get(() -> form.targets().isEnabled())); // nhiều mục → được chọn
        assertEquals(GG_1, get(form::selectedTarget));     // mặc định mục đầu

        onEdt(() -> {
            form.targets().setSelectedIndex(1);
            form.optionButton(ConflictResolution.NEW_ENTRY).doClick();
        });

        assertEquals(List.of(new Decision(ConflictResolution.NEW_ENTRY, GG_2)), chosen);
    }

    @Test
    void conflictFormWithOneEntryJustShowsIt() throws Exception {
        List<Decision> chosen = new ArrayList<>();
        Conflict conflict = new Conflict(Operation.EDIT, "gg", List.of("x"), List.of(GG_1),
                new SlangEntry(9, "old", List.of("x")));
        ConflictDialog.Form form = get(() -> new ConflictDialog.Form(conflict, chosen::add));

        assertFalse(get(() -> form.targets().isEnabled()));
        // Chữ mô tả theo thao tác: sửa thì "Gộp nghĩa", không phải "Thêm nghĩa"
        String text = get(() -> form.optionButton(ConflictResolution.ADD_MEANING).getText());
        assertTrue(text.contains(Messages.get("conflict.edit.add_meaning.title")), text);

        onEdt(() -> form.optionButton(ConflictResolution.OVERWRITE).doClick());
        assertEquals(List.of(new Decision(ConflictResolution.OVERWRITE, GG_1)), chosen);
    }

    @Test
    void htmlSpecialCharactersAreShownLiterally() {
        assertEquals("&lt;3 &amp; &gt;_&lt;", ConflictDialog.escape("<3 & >_<"));
    }

    @Test
    void meaningsStartingWithAnHtmlTagAreShownAsPlainText() throws Exception {
        String meaning = "<html><font size=7>big";
        EntryEditorDialog.Form form = form(true, new Draft("x", List.of(meaning)));
        assertEquals(List.of(meaning), get(form::meaningRows));
        assertFalse(get(() -> cellRendersHtml(form.table(), 0, 1)));
    }

    // ===================== Tiện ích =====================

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
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
