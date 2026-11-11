package lab01.slangdict._24127430.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.ConflictResolver.Operation;
import lab01.slangdict._24127430.ui.PlainText;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Hỏi người dùng khi trùng tên slang (yêu cầu của F4: "confirm có overwrite hay duplicate ra 1 slang word mới").
 * <pre>
 * ┌ Slang đã tồn tại ──────────────────────────────────────────────┐
 * │ ⚠ Slang "gg" đã có trong từ điển.                               │
 * │ Nghĩa bạn vừa nhập: Great game                                  │
 * │ Có 2 mục cùng tên, chọn mục bị ảnh hưởng:                        │
 * │ ┌──────────────────────────────────────────────────────────────┐ │
 * │ │ gg   Good game                                                │ │
 * │ │ GG   Gotta go                                                 │ │
 * │ └──────────────────────────────────────────────────────────────┘ │
 * │ Bạn muốn làm gì?                                                 │
 * │ [ Ghi đè        Thay toàn bộ nghĩa của mục đã có bằng nghĩa mới ] │
 * │ [ Thêm nghĩa    Giữ nghĩa cũ và thêm các nghĩa mới vào mục đã có ] │
 * │ [ Tạo bản mới   Giữ nguyên mục cũ, thêm một slang riêng cùng tên ] │
 * │                                                  [Quay lại sửa] │
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 * Mỗi lựa chọn là một nút lớn có mô tả, bấm là xong (không phải chọn rồi bấm OK). Chữ mô tả khác nhau giữa
 * thêm và đổi tên, vì cùng một lựa chọn có tác dụng khác nhau (xem {@code DictionaryService}).
 */
public final class ConflictDialog {

    private ConflictDialog() {
    }

    /**
     * Mở dialog (modal).
     *
     * @return lựa chọn của người dùng; rỗng nếu bấm "Quay lại sửa", Esc hoặc đóng cửa sổ
     */
    public static Optional<Decision> show(Component parent, Conflict conflict) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Messages.get("conflict.title"), Dialog.ModalityType.APPLICATION_MODAL);
        Decision[] result = {null};
        Form form = new Form(conflict, decision -> {
            result[0] = decision;
            dialog.dispose();
        });

        JButton back = new JButton(Messages.get("conflict.back"));
        back.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(back);
        south.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 560), dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        // Không để focus sẵn ở "Ghi đè": lỡ nhấn Enter là mất dữ liệu. Nhiều mục → chọn mục trước; một mục → nút quay lại
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                (form.hasChoiceOfTarget() ? form.targets() : back).requestFocusInWindow();
            }
        });
        dialog.setVisible(true); // modal
        return Optional.ofNullable(result[0]);
    }

    /** Nội dung dialog, tách riêng để test được mà không mở cửa sổ. */
    static final class Form extends JPanel {

        /** Số mục tối đa hiện trong danh sách trước khi phải cuộn. */
        private static final int VISIBLE_ROWS = 5;
        /** Bề rộng phần chữ (px), giữ dialog không bị kéo quá rộng bởi nghĩa dài. */
        private static final int TEXT_WIDTH = 500;

        private final Conflict conflict;
        private final Consumer<Decision> onChoose;
        private final JList<SlangEntry> targets;
        private final Map<ConflictResolution, JButton> optionButtons = new EnumMap<>(ConflictResolution.class);

        Form(Conflict conflict, Consumer<Decision> onChoose) {
            this.conflict = Objects.requireNonNull(conflict, "conflict");
            this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            Font font = UIManager.getFont("Label.font");
            Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);

            String headerKey = conflict.operation() == Operation.ADD ? "conflict.header.add" : "conflict.header.edit";
            JLabel header = new JLabel("⚠ " + Messages.format(headerKey, conflict.slang()));
            header.setForeground(EntryEditorDialog.Form.warningColor());
            if (font != null) {
                header.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 3f));
            }
            addRow(header, 8);

            // body có width cố định để nghĩa dài tự xuống dòng thay vì kéo dialog rộng ra
            JLabel yours = new JLabel("<html><body style='width:" + TEXT_WIDTH + "px'><b>"
                    + escape(Messages.get("conflict.yourMeanings")) + "</b> "
                    + escape(String.join(" | ", conflict.meanings())) + "</body></html>");
            addRow(yours, 10);

            List<SlangEntry> existing = conflict.existing();
            boolean many = existing.size() > 1;
            JLabel existingLabel = new JLabel(many ? Messages.format("conflict.existing.many", existing.size())
                    : Messages.get("conflict.existing.one"));
            existingLabel.setFont(font == null ? null : font.deriveFont(Font.BOLD));
            addRow(existingLabel, 4);

            targets = new JList<>(existing.toArray(new SlangEntry[0]));
            targets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            targets.setSelectedIndex(0);
            targets.setVisibleRowCount(Math.min(existing.size(), VISIBLE_ROWS));
            targets.setFixedCellWidth(TEXT_WIDTH); // nghĩa quá dài bị cắt, rê chuột để xem đủ (tooltip)
            targets.setEnabled(many); // một mục thì không có gì để chọn, chỉ để xem
            targets.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean selected, boolean focused) {
                    super.getListCellRendererComponent(list, value, index, selected && many, focused && many);
                    SlangEntry entry = (SlangEntry) value;
                    setText(html("<b>" + escape(entry.getSlang()) + "</b>&nbsp;&nbsp;"
                            + escape(entry.getMeaningText())));
                    setToolTipText(PlainText.tooltip(entry.getMeaningText()));
                    setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    if (!many) {
                        setForeground(ThemeManager.color("Label.foreground", Color.BLACK));
                    }
                    return this;
                }
            });
            JScrollPane scroll = new JScrollPane(targets);
            addRow(scroll, 12);

            JLabel question = new JLabel(Messages.get("conflict.question"));
            question.setFont(font == null ? null : font.deriveFont(Font.BOLD));
            addRow(question, 6);

            JPanel options = new JPanel(new GridLayout(0, 1, 0, 6));
            String prefix = "conflict." + conflict.operation().name().toLowerCase(Locale.ROOT) + ".";
            for (ConflictResolution resolution : ConflictResolution.values()) {
                String key = prefix + resolution.name().toLowerCase(Locale.ROOT);
                JButton button = new JButton(html("<b>" + escape(Messages.get(key + ".title")) + "</b><br>"
                        + "<span style='color:" + hex(muted) + "'>" + escape(Messages.get(key + ".desc"))
                        + "</span>"));
                button.setHorizontalAlignment(SwingConstants.LEADING);
                button.setMargin(new Insets(6, 10, 6, 10));
                button.addActionListener(e -> choose(resolution));
                optionButtons.put(resolution, button);
                options.add(button);
            }
            addRow(options, 0);
        }

        private void addRow(JComponent component, int gapBelow) {
            component.setAlignmentX(LEFT_ALIGNMENT);
            add(component);
            if (gapBelow > 0) {
                add(javax.swing.Box.createVerticalStrut(gapBelow));
            }
        }

        /** Như khi người dùng bấm nút lựa chọn. */
        void choose(ConflictResolution resolution) {
            onChoose.accept(new Decision(resolution, selectedTarget()));
        }

        SlangEntry selectedTarget() {
            SlangEntry selected = targets.getSelectedValue();
            return selected != null ? selected : conflict.existing().get(0);
        }

        /** Có nhiều mục cùng tên để chọn mục bị ảnh hưởng. */
        boolean hasChoiceOfTarget() {
            return conflict.existing().size() > 1;
        }

        // ----- cho test -----

        JList<SlangEntry> targets() {
            return targets;
        }

        JButton optionButton(ConflictResolution resolution) {
            return optionButtons.get(resolution);
        }

        private static String html(String body) {
            return "<html>" + body + "</html>";
        }

        private static String hex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }

    /** Thoát ký tự HTML: tên slang như "<3" hay "&" phải hiện đúng nguyên văn. */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
