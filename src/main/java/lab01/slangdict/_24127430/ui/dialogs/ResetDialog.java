package lab01.slangdict._24127430.ui.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Xác nhận Reset (F7): cảnh báo mất mọi thay đổi + checkbox "Xóa cả lịch sử tìm kiếm".
 * <pre>
 * ┌ Khôi phục dữ liệu gốc ─────────────────────────────────────────┐
 * │ ⚠  Đưa từ điển về danh sách slang gốc?                          │
 * │    Từ điển hiện có 7.640 slang. Mọi slang bạn đã thêm, sửa      │
 * │    hoặc xóa sẽ mất. Không thể hoàn tác.                         │
 * │    File gốc: slang.txt nhúng trong file jar                      │
 * │    [ ] Xóa cả lịch sử tìm kiếm (12 lượt)                         │
 * │                                         [Khôi phục]  [Hủy]      │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 * Nút mặc định (Enter) là <b>Hủy</b>: thao tác không hoàn tác được thì phải chủ động bấm "Khôi phục".
 * Checkbox mặc định không chọn: lịch sử chỉ bị xóa khi người dùng muốn.
 */
public final class ResetDialog {

    private ResetDialog() {
    }

    /**
     * Hiện dialog và chờ người dùng chọn.
     *
     * @param dictionarySize số slang hiện có
     * @param historySize    số dòng lịch sử hiện có (0 thì checkbox bị tắt)
     * @param source         file gốc sẽ đọc ({@code DictionaryService.originalSource()})
     * @return rỗng nếu hủy; ngược lại là có xóa cả lịch sử hay không
     */
    public static Optional<Boolean> show(Component parent, int dictionarySize, int historySize, String source) {
        Form form = new Form(dictionarySize, historySize, source);
        Object[] options = {Messages.get("reset.button"), Messages.get("common.cancel")};
        int choice = JOptionPane.showOptionDialog(parent, form, Messages.get("reset.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0 ? Optional.of(form.clearHistory()) : Optional.empty();
    }

    /** Nội dung dialog, tách khỏi JOptionPane để test được mà không mở cửa sổ. */
    static final class Form extends JPanel {

        private final JLabel question = new JLabel();
        private final JLabel details = new JLabel();
        private final JLabel sourceLabel = new JLabel();
        private final JCheckBox clearHistoryBox = new JCheckBox();

        Form(int dictionarySize, int historySize, String source) {
            super(new GridBagLayout());
            question.setText(Messages.get("reset.question"));
            details.setText(html(Messages.format("reset.details", dictionarySize)));
            sourceLabel.setText(html(sourceText(source)));
            if (historySize > 0) {
                clearHistoryBox.setText(Messages.format("reset.clearHistory", historySize));
            } else {
                clearHistoryBox.setText(Messages.get("reset.clearHistory.empty"));
                clearHistoryBox.setEnabled(false);
            }

            Font font = UIManager.getFont("Label.font");
            if (font != null) {
                question.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));
                sourceLabel.setFont(font.deriveFont(font.getSize2D() - 1f));
            }
            sourceLabel.setForeground(ThemeManager.color("Label.disabledForeground", Color.GRAY));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1;
            gbc.insets = new Insets(0, 0, 8, 0);
            add(question, gbc);
            add(details, gbc);
            gbc.insets = new Insets(0, 0, 12, 0);
            add(sourceLabel, gbc);
            gbc.insets = new Insets(0, 0, 4, 0);
            add(clearHistoryBox, gbc);
        }

        /** "slang.txt nhúng trong jar" thay cho đường dẫn kỹ thuật {@code jar:/data/slang.txt}. */
        static String sourceText(String source) {
            return source.startsWith("jar:") ? Messages.get("reset.source.bundled")
                    : Messages.format("reset.source.file", source);
        }

        /** Bọc HTML có chiều rộng cố định để JLabel tự xuống dòng. */
        private static String html(String text) {
            String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
            return "<html><body style='width:380px'>" + escaped + "</body></html>";
        }

        boolean clearHistory() {
            return clearHistoryBox.isSelected();
        }

        // ----- cho test -----

        JCheckBox clearHistoryBox() {
            return clearHistoryBox;
        }

        String detailsText() {
            return details.getText();
        }

        String sourceText() {
            return sourceLabel.getText();
        }
    }
}
