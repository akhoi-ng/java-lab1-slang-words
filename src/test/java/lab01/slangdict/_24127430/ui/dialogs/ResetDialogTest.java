package lab01.slangdict._24127430.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.i18n.Messages;

/** Nội dung dialog Reset, không mở cửa sổ. */
class ResetDialogTest {

    @BeforeEach
    void vietnamese() {
        Messages.setLocale(Messages.VIETNAMESE);
    }

    @AfterEach
    void restore() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    private static ResetDialog.Form form(int size, int history, String source) throws Exception {
        List<ResetDialog.Form> box = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> box.add(new ResetDialog.Form(size, history, source)));
        return box.get(0);
    }

    @Test
    void clearHistoryIsOffByDefault() throws Exception {
        ResetDialog.Form form = form(7640, 12, "jar:/data/slang.txt");
        assertTrue(form.clearHistoryBox().isEnabled());
        assertFalse(form.clearHistory());
        assertTrue(form.clearHistoryBox().getText().contains("12"));

        form.clearHistoryBox().setSelected(true);
        assertTrue(form.clearHistory());
    }

    @Test
    void emptyHistoryDisablesTheCheckbox() throws Exception {
        ResetDialog.Form form = form(7640, 0, "jar:/data/slang.txt");
        assertFalse(form.clearHistoryBox().isEnabled());
        assertFalse(form.clearHistory());
    }

    @Test
    void showsTheCurrentSizeAndWhereTheOriginalComesFrom() throws Exception {
        ResetDialog.Form bundled = form(7640, 0, "jar:/data/slang.txt");
        assertTrue(bundled.detailsText().contains("7.640"), bundled.detailsText());
        assertTrue(bundled.sourceText().contains(Messages.get("reset.source.bundled")));

        ResetDialog.Form external = form(10, 0, "D:\\app\\data\\slang<1>.txt");
        assertTrue(external.sourceText().contains("D:\\app\\data\\slang&lt;1&gt;.txt"), external.sourceText());
    }
}
