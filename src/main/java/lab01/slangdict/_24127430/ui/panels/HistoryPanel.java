package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.service.HistoryService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.FeatureScreen;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Màn hình Lịch sử tìm kiếm (F3).
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ [3] Lịch sử tìm kiếm                       [Tìm lại] [Xóa dòng chọn] [Xóa tất cả] │
 * │ 12 lượt tìm · bấm đúp hoặc nhấn Enter ở một dòng để tìm lại               │
 * │ ┌ Thời gian ──────────┬ Loại ──────┬ Kiểu khớp ┬ Từ khóa ───────────────┐ │
 * │ │ 11/11/2026 14:30:05 │ Slang      │ Tiền tố   │ lol                    │ │
 * │ │ 11/11/2026 14:29:41 │ Định nghĩa │ Chính xác │ laugh out loud         │ │
 * │ └─────────────────────┴────────────┴───────────┴────────────────────────┘ │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Bảng đọc thẳng từ {@link HistoryService} (không giữ bản sao). Service báo thay đổi qua listener nên bảng luôn
 * đúng, kể cả khi lịch sử được ghi thêm từ màn hình Search lúc màn hình này đang ẩn.
 * Không có lịch sử thì hiện màn hình trống có hướng dẫn thay cho bảng rỗng (CardLayout nhỏ bên trong).
 */
public class HistoryPanel extends JPanel implements FeatureScreen {

    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

    private final HistoryService service;
    private final Consumer<HistoryItem> replayer;
    /** Hỏi xác nhận trước khi xóa tất cả; test thay bằng hàm trả lời sẵn để không mở dialog. */
    private Predicate<String> confirmer = this::confirmWithDialog;

    private final JLabel title = new JLabel();
    private final JLabel countLabel = new JLabel();
    private final JButton replayButton = new JButton();
    private final JButton deleteButton = new JButton();
    private final JButton clearButton = new JButton();

    private final HistoryTableModel tableModel = new HistoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final CardLayout bodyLayout = new CardLayout();
    private final JPanel body = new JPanel(bodyLayout);
    private final JLabel emptyTitle = new JLabel();
    private final JLabel emptyHint = new JLabel();

    /**
     * @param service  lịch sử tìm kiếm
     * @param replayer chạy lại một lần tìm (App truyền hàm gọi {@code searchPanel.search(...)}: chuyển sang màn hình
     *                 Search đúng F1/F2 và tìm lại)
     */
    public HistoryPanel(HistoryService service, Consumer<HistoryItem> replayer) {
        super(new BorderLayout());
        this.service = Objects.requireNonNull(service, "service");
        this.replayer = Objects.requireNonNull(replayer, "replayer");

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        service.addChangeListener(this::onHistoryChanged);
        applyStyles();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    // ===================== FeatureScreen =====================

    /** Mở màn hình: chọn sẵn dòng mới nhất và đặt focus vào bảng, để nhấn Enter là tìm lại được ngay. */
    @Override
    public void onShown(Feature via) {
        if (table.getSelectedRow() < 0 && tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        SwingUtilities.invokeLater(table::requestFocusInWindow);
    }

    // ===================== Dựng giao diện =====================

    private JComponent buildHeader() {
        title.setIcon(new NumberBadgeIcon(Feature.HISTORY.number()));
        title.setIconTextGap(10);

        replayButton.addActionListener(e -> replaySelected());
        deleteButton.addActionListener(e -> deleteSelected());
        clearButton.addActionListener(e -> clearAll());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(replayButton);
        actions.add(deleteButton);
        actions.add(clearButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(top, BorderLayout.NORTH);
        header.add(countLabel, BorderLayout.CENTER);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return header;
    }

    private JComponent buildBody() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); // Ctrl/Shift để xóa nhiều dòng
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {180, 110, 110, 420};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && row >= 0) {
                    replay(row);
                }
            }
        });
        // WHEN_FOCUSED được ưu tiên hơn phím Enter mặc định của JTable (xuống dòng dưới)
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "replay", this::replaySelected);
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected", this::deleteSelected);

        JPanel empty = new JPanel(new GridBagLayout());
        emptyTitle.setHorizontalAlignment(SwingConstants.CENTER);
        emptyHint.setHorizontalAlignment(SwingConstants.CENTER);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(4, 24, 4, 24);
        empty.add(emptyTitle, gbc);
        empty.add(emptyHint, gbc);

        body.add(new JScrollPane(table), CARD_TABLE);
        body.add(empty, CARD_EMPTY);
        return body;
    }

    private static void bindKey(JComponent component, KeyStroke key, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ===================== Thao tác =====================

    /** Tìm lại dòng đang chọn (dòng trên cùng nếu chọn nhiều). */
    void replaySelected() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            replay(row);
        }
    }

    private void replay(int row) {
        // Lấy item trước: tìm lại sẽ ghi thêm một dòng vào đầu lịch sử, làm các dòng dịch xuống
        HistoryItem item = service.get(row);
        replayer.accept(item);
    }

    /** Xóa các dòng đang chọn (không hỏi lại: chỉ là lịch sử, xóa nhầm vài dòng không mất dữ liệu từ điển). */
    void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        int first = rows[0];
        service.remove(rows); // → onHistoryChanged vẽ lại bảng
        // Chọn dòng kế tiếp ở đúng chỗ vừa xóa, để nhấn Delete liên tiếp xóa được nhiều dòng
        int next = Math.min(first, tableModel.getRowCount() - 1);
        if (next >= 0) {
            table.setRowSelectionInterval(next, next);
        }
    }

    /** Xóa toàn bộ lịch sử sau khi người dùng xác nhận. */
    void clearAll() {
        if (service.size() == 0) {
            return;
        }
        if (confirmer.test(Messages.format("history.confirm.clear", service.size()))) {
            service.clear();
        }
    }

    private boolean confirmWithDialog(String message) {
        return JOptionPane.showConfirmDialog(this, message, Messages.get("history.confirm.clear.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Lịch sử vừa thay đổi (thêm từ màn hình Search, xóa ở đây, Reset...). */
    private void onHistoryChanged() {
        tableModel.fireTableDataChanged(); // bảng đọc lại từ service; vùng chọn bị xóa
        updateState();
    }

    // ===================== Hiển thị =====================

    private void updateState() {
        boolean hasItems = tableModel.getRowCount() > 0;
        bodyLayout.show(body, hasItems ? CARD_TABLE : CARD_EMPTY);
        countLabel.setText(hasItems ? Messages.format("history.count", tableModel.getRowCount()) : " ");
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        replayButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        clearButton.setEnabled(tableModel.getRowCount() > 0);
    }

    private void applyTexts() {
        title.setText(Messages.get(Feature.HISTORY.titleKey()));
        replayButton.setText(Messages.get("history.action.replay"));
        deleteButton.setText(Messages.get("history.action.delete"));
        clearButton.setText(Messages.get("history.action.clear"));
        replayButton.setToolTipText(Messages.get("history.action.replay.tip"));
        deleteButton.setToolTipText(Messages.get("history.action.delete.tip"));
        emptyTitle.setText(Messages.get("history.empty"));
        emptyHint.setText(Messages.get("history.empty.hint"));

        tableModel.updateFormatter();
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderValue(tableModel.getColumnName(i));
        }
        table.getTableHeader().repaint();
        table.repaint(); // cột Loại, Kiểu khớp, Thời gian đổi theo ngôn ngữ
        updateState();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) { // updateUI được gọi cả trong constructor của JPanel, lúc field chưa có
            applyStyles();
        }
    }

    private void applyStyles() {
        Font font = UIManager.getFont("Label.font");
        if (font == null) {
            return;
        }
        Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
        title.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 6f));
        countLabel.setForeground(muted);
        emptyTitle.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 4f));
        emptyHint.setForeground(muted);
        table.setRowHeight(Math.round(font.getSize2D() * 2f));
    }

    // ===================== Cho test =====================

    JTable table() {
        return table;
    }

    String countText() {
        return countLabel.getText();
    }

    boolean showingEmptyState() {
        return tableModel.getRowCount() == 0;
    }

    void setConfirmer(Predicate<String> confirmer) {
        this.confirmer = Objects.requireNonNull(confirmer, "confirmer");
    }

    // ===================== Model của bảng =====================

    // [THAM KHẢO] Tự viết table model bằng cách kế thừa AbstractTableModel, theo Oracle Java Tutorials,
    //   "How to Use Tables" (mục Creating a Table Model) https://docs.oracle.com/javase/tutorial/uiswing/components/table.html
    /** 4 cột Thời gian | Loại | Kiểu khớp | Từ khóa, đọc thẳng từ service. */
    private final class HistoryTableModel extends AbstractTableModel {

        private static final String[] COLUMN_KEYS = {
                "history.column.time", "history.column.target", "history.column.mode", "history.column.query"};

        private DateTimeFormatter timeFormat;

        /** Định dạng thời gian theo ngôn ngữ đang chọn (mẫu nằm trong file .properties). */
        void updateFormatter() {
            timeFormat = DateTimeFormatter.ofPattern(Messages.get("history.timePattern"), Messages.getLocale());
        }

        @Override
        public int getRowCount() {
            return service.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_KEYS.length;
        }

        @Override
        public String getColumnName(int column) {
            return Messages.get(COLUMN_KEYS[column]);
        }

        @Override
        public Object getValueAt(int row, int column) {
            HistoryItem item = service.get(row);
            return switch (column) {
                case 0 -> timeFormat.format(item.time());
                case 1 -> Messages.get("search.target." + item.target().name().toLowerCase(Locale.ROOT));
                case 2 -> Messages.get("search.mode." + item.mode().name().toLowerCase(Locale.ROOT));
                default -> item.query();
            };
        }
    }
    // [HẾT THAM KHẢO]
}
