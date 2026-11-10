package lab01.slangdict._24127430.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.Theme;
import lab01.slangdict._24127430.service.DictionaryService;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Dialog dùng chung cho thêm (F4) và sửa (F5) một slang: tên + danh sách nghĩa.
 * <pre>
 * ┌ [4] Thêm slang mới ───────────────────────────────────────────┐
 * │ Slang  [ yeet                                               ] │
 * │        ⚠ Đã có 1 slang tên này. Khi lưu, bạn sẽ được chọn ...   │
 * │ Các nghĩa                  [Thêm nghĩa] [Xóa nghĩa] [Lên] [Xuống] │
 * │ ┌ # ┬ Nghĩa ───────────────────────────────────────────────┐ │
 * │ │ 1 │ To throw something with force                          │ │
 * │ │ 2 │ Expression of excitement                               │ │
 * │ └───┴────────────────────────────────────────────────────────┘ │
 * │ Mỗi dòng một nghĩa. Gõ hoặc bấm đúp vào một dòng để sửa ...     │
 * │ (lỗi nhập liệu màu đỏ)                              [Lưu] [Hủy] │
 * └───────────────────────────────────────────────────────────────┘
 * </pre>
 * Dialog chỉ thu thập và kiểm tra dữ liệu (dùng chung {@link DictionaryService#validate} với service), không sửa
 * từ điển. Việc hỏi trùng tên và lưu là của {@code EntryActions} + {@link DictionaryService}.
 * <p>
 * Phần nội dung là {@link Form} (một {@code JPanel}), tách khỏi {@code JDialog} để test được mà không cần mở cửa sổ.
 */
public final class EntryEditorDialog {

    /** Dữ liệu người dùng nhập (chưa chuẩn hóa: service sẽ bỏ khoảng trắng, dòng trống, nghĩa trùng). */
    public record Draft(String slang, List<String> meanings) {
        public Draft {
            Objects.requireNonNull(slang, "slang");
            meanings = List.copyOf(meanings);
        }
    }

    private EntryEditorDialog() {
    }

    /**
     * Mở dialog (modal) và chờ người dùng lưu hoặc hủy.
     *
     * @param parent       component bất kỳ trong cửa sổ cha
     * @param editing      true: sửa (F5), false: thêm (F4)
     * @param initial      giá trị ban đầu
     * @param sameNameCount số entry <b>khác</b> đang mang một tên, để báo trước "tên đã có" khi đang gõ
     * @return dữ liệu đã kiểm tra hợp lệ, hoặc rỗng nếu người dùng hủy
     */
    public static Optional<Draft> show(Component parent, boolean editing, Draft initial,
                                       ToIntFunction<String> sameNameCount) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, Messages.get(editing ? "editor.title.edit" : "editor.title.add"),
                Dialog.ModalityType.APPLICATION_MODAL);
        Form form = new Form(editing, initial, sameNameCount);
        Draft[] result = {null};

        JButton save = new JButton(Messages.get("editor.save"));
        JButton cancel = new JButton(Messages.get("common.cancel"));
        save.setToolTipText(Messages.get("editor.save") + " (Ctrl+Enter)");
        Runnable trySave = () -> form.submit().ifPresent(draft -> {
            result[0] = draft;
            dialog.dispose();
        });
        save.addActionListener(e -> trySave.run());
        cancel.addActionListener(e -> dialog.dispose());
        form.setSaveAction(trySave);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(save);
        buttons.add(cancel);
        JPanel south = new JPanel(new BorderLayout(12, 0));
        south.add(form.errorLabel(), BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        south.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        dialog.setContentPane(content);

        // Enter ở ô tên do Form tự xử lý; Ctrl+Enter lưu từ bất kỳ đâu; Esc hủy (khi không đang sửa dở một ô)
        dialog.getRootPane().registerKeyboardAction(e -> trySave.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.setMinimumSize(new Dimension(520, 400));
        dialog.setSize(600, 470);
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                form.focusFirstField();
            }
        });
        dialog.setVisible(true); // modal: chặn tới khi dialog đóng
        return Optional.ofNullable(result[0]);
    }

    // ===================== Nội dung dialog =====================

    /** Ô tên + bảng nghĩa + kiểm tra dữ liệu. Không phụ thuộc JDialog nên test được. */
    static final class Form extends JPanel {

        private final boolean editing;
        private final String originalName;
        private final ToIntFunction<String> sameNameCount;
        private Runnable saveAction = () -> { };

        private final JLabel titleLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JTextField nameField = new JTextField();
        private final JLabel duplicateLabel = new JLabel(" ");
        private final JLabel meaningsLabel = new JLabel();
        private final JLabel hintLabel = new JLabel();
        private final JLabel errorLabel = new JLabel(" ");
        private final MeaningTableModel model = new MeaningTableModel();
        private final JTable table = new JTable(model);
        private final JButton addButton = new JButton();
        private final JButton removeButton = new JButton();
        private final JButton upButton = new JButton();
        private final JButton downButton = new JButton();

        Form(boolean editing, Draft initial, ToIntFunction<String> sameNameCount) {
            super(new BorderLayout(0, 8));
            this.editing = editing;
            this.originalName = editing ? initial.slang().strip() : null;
            this.sameNameCount = Objects.requireNonNull(sameNameCount, "sameNameCount");

            add(buildTop(), BorderLayout.NORTH);
            add(buildMeanings(), BorderLayout.CENTER);

            nameField.setText(initial.slang());
            model.setRows(initial.meanings());
            if (model.getRowCount() == 0) {
                model.add(""); // thêm mới: sẵn một dòng trống để gõ nghĩa đầu tiên
            }
            applyTexts();
            applyStyles();
            updateDuplicateWarning();
            updateButtons();
        }

        private JComponent buildTop() {
            titleLabel.setIcon(new NumberBadgeIcon(editing ? 5 : 4));
            titleLabel.setIconTextGap(10);

            nameField.putClientProperty("JTextField.showClearButton", true);
            nameLabel.setLabelFor(nameField);
            // Enter ở ô tên: chưa có nghĩa nào thì chuyển xuống gõ nghĩa, có rồi thì lưu
            nameField.addActionListener(e -> {
                if (DictionaryService.cleanMeanings(currentMeanings()).isEmpty()) {
                    editRow(firstBlankRowOrNew());
                } else {
                    saveAction.run();
                }
            });
            nameField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateDuplicateWarning();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateDuplicateWarning();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateDuplicateWarning();
                }
            });

            JPanel grid = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 0, 12, 0);
            grid.add(titleLabel, gbc);

            gbc.gridy = 1;
            gbc.gridwidth = 1;
            gbc.insets = new Insets(0, 0, 0, 10);
            grid.add(nameLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 0, 0, 0);
            grid.add(nameField, gbc);

            gbc.gridy = 2;
            gbc.insets = new Insets(4, 0, 4, 0);
            grid.add(duplicateLabel, gbc);
            return grid;
        }

        private JComponent buildMeanings() {
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setSurrendersFocusOnKeystroke(true); // gõ phím ở một dòng là sửa luôn dòng đó
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE); // bấm Lưu khi đang gõ dở vẫn giữ chữ
            table.setFillsViewportHeight(true);
            table.getTableHeader().setReorderingAllowed(false);
            table.getColumnModel().getColumn(0).setMaxWidth(40);
            table.getColumnModel().getColumn(0).setPreferredWidth(32);
            table.getSelectionModel().addListSelectionListener(e -> updateButtons());
            model.addTableModelListener(e -> updateButtons());

            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeMeaning", this::removeSelected);
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), "addMeaning", this::addMeaning);
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "moveUp", () -> move(-1));
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "moveDown", () -> move(1));

            addButton.addActionListener(e -> addMeaning());
            removeButton.addActionListener(e -> removeSelected());
            upButton.addActionListener(e -> move(-1));
            downButton.addActionListener(e -> move(1));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            actions.add(addButton);
            actions.add(removeButton);
            actions.add(upButton);
            actions.add(downButton);

            JPanel header = new JPanel(new BorderLayout());
            header.add(meaningsLabel, BorderLayout.WEST);
            header.add(actions, BorderLayout.EAST);

            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.add(header, BorderLayout.NORTH);
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            panel.add(hintLabel, BorderLayout.SOUTH);
            return panel;
        }

        private void bindKey(KeyStroke key, String name, Runnable action) {
            table.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
            table.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    action.run();
                }
            });
        }

        // ----- thao tác trên danh sách nghĩa -----

        /** Thêm một dòng trống ở cuối và bắt đầu gõ vào đó. */
        void addMeaning() {
            stopEditing();
            editRow(model.add(""));
        }

        /** Xóa các dòng đang chọn, rồi chọn dòng kế tiếp. */
        void removeSelected() {
            stopEditing();
            int[] rows = table.getSelectedRows();
            if (rows.length == 0) {
                return;
            }
            for (int i = rows.length - 1; i >= 0; i--) {
                model.remove(rows[i]);
            }
            int next = Math.min(rows[0], model.getRowCount() - 1);
            if (next >= 0) {
                table.setRowSelectionInterval(next, next);
            }
        }

        /** Đưa dòng đang chọn lên ({@code -1}) hoặc xuống ({@code +1}) một vị trí. */
        void move(int delta) {
            stopEditing();
            int row = table.getSelectedRow();
            int target = row + delta;
            if (row < 0 || table.getSelectedRowCount() != 1 || target < 0 || target >= model.getRowCount()) {
                return;
            }
            model.swap(row, target);
            table.setRowSelectionInterval(target, target);
        }

        private int firstBlankRowOrNew() {
            for (int row = 0; row < model.getRowCount(); row++) {
                if (model.get(row).isBlank()) {
                    return row;
                }
            }
            return model.add("");
        }

        private void editRow(int row) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 1, true));
            if (table.editCellAt(row, 1)) {
                Component editor = table.getEditorComponent();
                if (editor != null) {
                    editor.requestFocusInWindow();
                }
            }
        }

        private void stopEditing() {
            TableCellEditor editor = table.getCellEditor();
            if (editor != null && !editor.stopCellEditing()) {
                editor.cancelCellEditing();
            }
        }

        // ----- kiểm tra và trả kết quả -----

        /**
         * Kiểm tra dữ liệu. Hợp lệ thì trả về Draft; không thì hiện lỗi màu đỏ và đưa focus tới chỗ sai.
         */
        Optional<Draft> submit() {
            stopEditing(); // ô đang gõ dở cũng phải được tính
            String name = nameField.getText();
            List<String> meanings = currentMeanings();
            Optional<DictionaryService.Problem> problem = DictionaryService.validate(name, meanings);
            if (problem.isEmpty()) {
                showError(null);
                return Optional.of(new Draft(name.strip(), meanings));
            }
            showError(problem.get());
            switch (problem.get()) {
                case NAME_EMPTY, NAME_INVALID, NAME_TOO_LONG -> {
                    nameField.requestFocusInWindow();
                    nameField.selectAll();
                }
                case NO_MEANING -> editRow(firstBlankRowOrNew());
                case MEANING_INVALID -> {
                    for (int row = 0; row < model.getRowCount(); row++) {
                        if (!DictionaryService.isValidMeaning(model.get(row))) {
                            editRow(row);
                            break;
                        }
                    }
                }
            }
            return Optional.empty();
        }

        private List<String> currentMeanings() {
            List<String> meanings = new ArrayList<>();
            for (int row = 0; row < model.getRowCount(); row++) {
                meanings.add(model.get(row));
            }
            return meanings;
        }

        private void showError(DictionaryService.Problem problem) {
            errorLabel.setText(problem == null ? " "
                    : problem == DictionaryService.Problem.NAME_TOO_LONG
                    ? Messages.format("editor.problem.name_too_long", DictionaryService.MAX_NAME_LENGTH)
                    : Messages.get("editor.problem." + problem.name().toLowerCase(java.util.Locale.ROOT)));
        }

        /** Báo trước khi tên đã có (sửa mà không đổi tên thì không báo). */
        private void updateDuplicateWarning() {
            String name = nameField.getText().strip();
            boolean renamed = !editing || !name.equalsIgnoreCase(originalName);
            int count = renamed && !name.isEmpty() ? sameNameCount.applyAsInt(name) : 0;
            duplicateLabel.setText(count > 0 ? "⚠ " + Messages.format("editor.duplicate", count) : " ");
        }

        private void updateButtons() {
            int selected = table.getSelectedRowCount();
            int row = table.getSelectedRow();
            removeButton.setEnabled(selected > 0);
            upButton.setEnabled(selected == 1 && row > 0);
            downButton.setEnabled(selected == 1 && row < model.getRowCount() - 1);
        }

        /** Focus vào ô tên và chọn sẵn toàn bộ chữ: gõ là thay tên, Enter là đi tiếp. */
        void focusFirstField() {
            nameField.requestFocusInWindow();
            nameField.selectAll();
        }

        void setSaveAction(Runnable action) {
            this.saveAction = Objects.requireNonNull(action, "action");
        }

        JLabel errorLabel() {
            return errorLabel;
        }

        private void applyTexts() {
            titleLabel.setText(Messages.get(editing ? "editor.title.edit" : "editor.title.add"));
            nameLabel.setText(Messages.get("editor.slang"));
            nameField.putClientProperty("JTextField.placeholderText", Messages.get("editor.slang.placeholder"));
            meaningsLabel.setText(Messages.get("editor.meanings"));
            hintLabel.setText(Messages.get("editor.meanings.hint"));
            addButton.setText(Messages.get("editor.meaning.add"));
            removeButton.setText(Messages.get("editor.meaning.remove"));
            upButton.setText(Messages.get("editor.meaning.up"));
            downButton.setText(Messages.get("editor.meaning.down"));
            addButton.setToolTipText(Messages.get("editor.meaning.add") + " (Insert)");
            removeButton.setToolTipText(Messages.get("editor.meaning.remove") + " (Delete)");
            upButton.setToolTipText(Messages.get("editor.meaning.up") + " (Alt+↑)");
            downButton.setToolTipText(Messages.get("editor.meaning.down") + " (Alt+↓)");
            table.getColumnModel().getColumn(0).setHeaderValue(Messages.get("editor.column.number"));
            table.getColumnModel().getColumn(1).setHeaderValue(Messages.get("editor.column.meaning"));
        }

        private void applyStyles() {
            Font font = UIManager.getFont("Label.font");
            if (font == null) {
                return;
            }
            titleLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 5f));
            nameLabel.setFont(font.deriveFont(Font.BOLD));
            meaningsLabel.setFont(font.deriveFont(Font.BOLD));
            nameField.setFont(font.deriveFont(font.getSize2D() + 2f));
            hintLabel.setFont(font.deriveFont(font.getSize2D() - 1f));
            hintLabel.setForeground(ThemeManager.color("Label.disabledForeground", Color.GRAY));
            duplicateLabel.setForeground(warningColor());
            errorLabel.setForeground(ThemeManager.color("Actions.Red", Color.RED));
            table.setRowHeight(Math.round(font.getSize2D() * 2f));
        }

        static Color warningColor() {
            return ThemeManager.current() == Theme.DARK ? new Color(0xE3B341) : new Color(0x9A6700);
        }

        // ----- cho test -----

        JTextField nameField() {
            return nameField;
        }

        JTable table() {
            return table;
        }

        String duplicateText() {
            return duplicateLabel.getText();
        }

        String errorText() {
            return errorLabel.getText();
        }

        List<String> meaningRows() {
            return currentMeanings();
        }
    }

    /** Bảng 2 cột: số thứ tự (tự tính) | nghĩa (sửa trực tiếp được). */
    private static final class MeaningTableModel extends AbstractTableModel {

        private final List<String> rows = new ArrayList<>();

        void setRows(List<String> meanings) {
            rows.clear();
            rows.addAll(meanings);
            fireTableDataChanged();
        }

        String get(int row) {
            return rows.get(row);
        }

        /** @return vị trí dòng mới */
        int add(String meaning) {
            rows.add(meaning);
            int row = rows.size() - 1;
            fireTableRowsInserted(row, row);
            return row;
        }

        void remove(int row) {
            rows.remove(row);
            fireTableDataChanged(); // số thứ tự của các dòng sau đổi theo
        }

        void swap(int a, int b) {
            String tmp = rows.get(a);
            rows.set(a, rows.get(b));
            rows.set(b, tmp);
            fireTableRowsUpdated(Math.min(a, b), Math.max(a, b));
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public Object getValueAt(int row, int column) {
            return column == 0 ? row + 1 : rows.get(row);
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 1) {
                rows.set(row, value == null ? "" : value.toString());
                fireTableCellUpdated(row, column);
            }
        }
    }
}
