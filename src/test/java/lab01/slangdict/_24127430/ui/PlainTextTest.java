package lab01.slangdict._24127430.ui;

import static lab01.slangdict._24127430.testutil.SwingHtml.rendersHtml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class PlainTextTest {

    private static final String HTML_LIKE = "<html><font color=red size=7>BIG</font>";

    @Test
    void swingRendersHtmlLikeTextAsHtmlByDefault() throws Exception {
        // Lý do có PlainText: không làm gì thì Swing vẽ tên slang này thành chữ đỏ cỡ 7
        assertTrue(get(() -> rendersHtml(new JLabel(HTML_LIKE))));
        assertTrue(get(() -> rendersHtml(new JButton(HTML_LIKE))));
    }

    @Test
    void labelsAndButtonsShowTheTextAsIs() throws Exception {
        assertFalse(get(() -> {
            JLabel label = new JLabel();
            assertSame(label, PlainText.of(label));
            label.setText(HTML_LIKE);
            assertEquals(HTML_LIKE, label.getText());
            return rendersHtml(label);
        }));
        assertFalse(get(() -> {
            JButton button = PlainText.of(new JButton());
            button.setText(HTML_LIKE);
            return rendersHtml(button);
        }));
    }

    @Test
    void htmlStaysOffAfterAThemeChange() throws Exception {
        assertFalse(get(() -> {
            JLabel label = PlainText.of(new JLabel());
            label.setText(HTML_LIKE);
            label.updateUI(); // đổi sáng/tối gọi updateUI cho mọi component
            return rendersHtml(label);
        }));
    }

    @Test
    void cellRendererShowsTheTextAsIs() throws Exception {
        assertFalse(get(() -> rendersHtml(PlainText.cellRenderer()
                .getTableCellRendererComponent(new JTable(), HTML_LIKE, false, false, 0, 0))));
    }

    @Test
    void tooltipEscapesOnlyHtmlLikeText() {
        assertEquals("<html>&lt;html&gt;&lt;b&gt;x &amp; y&lt;/b&gt;</html>", PlainText.tooltip("<html><b>x & y</b>"));
        assertEquals("<html>&lt;HTML&gt;x</html>", PlainText.tooltip("<HTML>x"));
        assertEquals("Laughing out loud", PlainText.tooltip("Laughing out loud"));
        assertEquals("<3 & :)", PlainText.tooltip("<3 & :)")); // không bắt đầu bằng <html> thì Swing vốn hiện đúng
        assertEquals(null, PlainText.tooltip(null));
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
