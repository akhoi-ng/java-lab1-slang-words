package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchResult;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.model.Theme;
import lab01.slangdict._24127430.service.SearchOutcome;
import lab01.slangdict._24127430.service.SearchService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.FeatureScreen;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.Sidebar;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Màn hình tìm kiếm, dùng chung cho F1 (theo slang) và F2 (theo definition).
 * <pre>
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │ [ Slang ] [ Định nghĩa ]                       ← chọn target (F1 / F2) │
 * │ [🔍 ô tìm kiếm ...................  ✕]  Kiểu khớp: [Tiền tố ▾]  [Tìm]  │
 * │ gợi ý về mode đang chọn                                                │
 * ├───────────────────────────────────────────────────────────────────────┤
 * │ 3 kết quả · 0,42 ms   Có phải bạn muốn tìm: lol    [Thêm] [Sửa] [Xóa]  │
 * │ ┌ Slang ─┬ Nghĩa ──────────────┐ ┌ chi tiết slang đang chọn ─────────┐ │
 * │ │ LOL    │ Laughing out loud   │ │ LOL                               │ │
 * │ │ ...    │ ...                 │ │ 1. Laughing out loud              │ │
 * │ └────────┴─────────────────────┘ └───────────────────────────────────┘ │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 * <b>Luồng:</b> việc tìm chạy trên một luồng nền riêng ({@link #executor}), kết quả được đưa về EDT bằng
 * {@link SwingUtilities#invokeLater}. Mỗi lần tìm có một số thứ tự ({@link #generation}); kết quả của lần tìm
 * cũ về muộn (người dùng đã gõ tiếp) bị bỏ qua, nên bảng luôn khớp với ô tìm kiếm.
 * <p>
 * <b>Tìm khi đang gõ:</b> chỉ ở SLANG + PREFIX (kiểu "autocomplete"), chờ người dùng ngừng gõ
 * {@value #LIVE_DELAY_MS} ms rồi mới tìm (debounce). Các mode khác tìm khi nhấn Enter hoặc nút Tìm.
 * Chỉ lần tìm "thật" (Enter, nút Tìm, bấm gợi ý) mới được báo cho {@link #setSearchListener} để ghi lịch sử.
 */
public class SearchPanel extends JPanel implements FeatureScreen {

    /** Số dòng tối đa đưa lên bảng; tổng số kết quả vẫn hiện đầy đủ trên dòng trạng thái. */
    static final int RESULT_LIMIT = 1000;
    /** Số gợi ý "Có phải bạn muốn tìm" tối đa. */
    static final int SUGGESTION_LIMIT = 5;
    /** Debounce khi tìm lúc đang gõ. */
    static final int LIVE_DELAY_MS = 150;
    /** Chỉ hiện "Đang tìm…" nếu việc tìm lâu hơn mức này, tránh chữ nhấp nháy với các query chỉ mất vài ms. */
    static final int BUSY_DELAY_MS = 120;

    /** Một yêu cầu tìm kiếm. */
    record Request(SearchTarget target, SearchMode mode, String query) {
    }

    /**
     * Dòng được chọn sẵn khi kết quả về.
     *
     * @param id              chọn entry có id này (sau khi thêm/sửa); -1 nếu không cần
     * @param row             không tìm thấy id (hoặc id = -1) thì chọn dòng này, kẹp vào trong bảng (sau khi xóa)
     * @param revealIfMissing kết quả không chứa entry {@code id} thì tìm theo tên của entry này để nó hiện ra
     */
    record Selection(int id, int row, SlangEntry revealIfMissing) {
        /** Mặc định: chọn kết quả tốt nhất. */
        static final Selection BEST = new Selection(-1, 0, null);
    }

    /** Dòng thông báo sau khi thêm/sửa/xóa: thành công (xanh) hay hướng dẫn (màu nhấn). */
    private enum NoticeKind { SUCCESS, INFO }

    private enum Status { IDLE, SEARCHING, DONE, ERROR }

    private final SearchService service;
    private final Consumer<Feature> navigator;
    private final Executor executor;
    private Consumer<HistoryItem> searchListener = item -> { };

    // ----- điều khiển phía trên -----
    private final JToggleButton slangTab = new JToggleButton();
    private final JToggleButton definitionTab = new JToggleButton();
    private final JTextField queryField = new JTextField();
    private final JLabel modeLabel = new JLabel();
    private final JComboBox<SearchMode> modeCombo = new JComboBox<>(SearchMode.values());
    private final JButton searchButton = new JButton();
    private final JLabel hintLabel = new JLabel();

    // ----- dòng trạng thái + gợi ý + nút thao tác -----
    private final JLabel statusLabel = new JLabel();
    private final JLabel noticeLabel = new JLabel();
    private final JLabel suggestLabel = new JLabel();
    private final JPanel suggestionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final JButton addButton = new JButton();
    private final JButton editButton = new JButton();
    private final JButton deleteButton = new JButton();

    // ----- bảng kết quả + chi tiết -----
    private final ResultTableModel tableModel = new ResultTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailCount = new JLabel();
    private final JTextArea detailText = new JTextArea();

    // ----- trạng thái -----
    private SearchTarget target = SearchTarget.SLANG;
    /** Mỗi target nhớ mode riêng: slang mặc định PREFIX (gõ tới đâu ra tới đó), definition mặc định EXACT. */
    private final Map<SearchTarget, SearchMode> modeByTarget = new EnumMap<>(SearchTarget.class);
    private final Timer liveTimer;
    private final Timer busyTimer;
    private final AtomicLong generation = new AtomicLong();
    /** Yêu cầu của kết quả đang hiện (null nếu bảng đang trống vì chưa tìm). */
    private Request shownRequest;
    private SearchOutcome shownOutcome;
    private List<String> shownSuggestions = List.of();
    private String errorMessage;
    private Status status = Status.IDLE;
    /** Thông báo đang hiện (key trong .properties + tham số, để đổi ngôn ngữ vẫn dịch lại được); null nếu không có. */
    private String noticeKey;
    private Object[] noticeArgs = {};
    private NoticeKind noticeKind = NoticeKind.SUCCESS;
    /** true khi code tự đặt giá trị cho các điều khiển, để listener không coi đó là thao tác của người dùng. */
    private boolean syncing;

    /**
     * @param service   dịch vụ tìm kiếm
     * @param navigator chuyển tới một chức năng (truyền {@code mainFrame::showFeature}): dùng khi người dùng đổi
     *                  Slang ↔ Định nghĩa (để Sidebar và tiêu đề đổi theo F1 ↔ F2) và cho các nút Thêm/Sửa/Xóa
     */
    public SearchPanel(SearchService service, Consumer<Feature> navigator) {
        this(service, navigator, newSearchThread());
    }

    /** Cho test: truyền {@code Runnable::run} để tìm ngay trên luồng gọi. */
    SearchPanel(SearchService service, Consumer<Feature> navigator, Executor executor) {
        super(new BorderLayout());
        this.service = Objects.requireNonNull(service, "service");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.executor = Objects.requireNonNull(executor, "executor");
        modeByTarget.put(SearchTarget.SLANG, SearchMode.PREFIX);
        modeByTarget.put(SearchTarget.DEFINITION, SearchMode.EXACT);

        liveTimer = new Timer(LIVE_DELAY_MS, e -> runSearch(currentRequest(), Selection.BEST));
        liveTimer.setRepeats(false);
        busyTimer = new Timer(BUSY_DELAY_MS, e -> setStatus(Status.SEARCHING));
        busyTimer.setRepeats(false);

        add(buildSearchBar(), BorderLayout.NORTH);
        add(buildResultArea(), BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        syncControls();
        applyStyles();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    private static Executor newSearchThread() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "search");
            thread.setDaemon(true); // không giữ JVM lại khi thoát app
            return thread;
        });
    }

    // ===================== API cho MainFrame / các bước sau =====================

    /** F1 → tìm theo slang, F2 → tìm theo definition. Gọi mỗi lần màn hình được hiện. */
    @Override
    public void onShown(Feature via) {
        SearchTarget wanted = via == Feature.SEARCH_DEFINITION ? SearchTarget.DEFINITION : SearchTarget.SLANG;
        if (wanted != target) {
            changeTarget(wanted, false);
        }
        SwingUtilities.invokeLater(queryField::requestFocusInWindow);
    }

    /** Nhận mỗi lần tìm "thật" (Enter, nút Tìm, bấm gợi ý). Bước 9 nối vào HistoryService. */
    public void setSearchListener(Consumer<HistoryItem> listener) {
        searchListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Tìm theo yêu cầu từ nơi khác (bước 9: bấm đúp một dòng lịch sử): chuyển sang màn hình Search đúng F1/F2,
     * đặt đúng target, mode, query rồi tìm như khi người dùng nhấn Enter.
     */
    public void search(SearchTarget newTarget, SearchMode mode, String query) {
        liveTimer.stop();
        target = newTarget;
        modeByTarget.put(newTarget, mode);
        setQueryQuietly(query);
        syncControls();
        updateHint();
        navigator.accept(featureOf(target)); // MainFrame đổi card + Sidebar, rồi gọi onShown (target đã khớp)
        submit();
    }

    /** Chạy lại lần tìm đang hiện (sau khi thêm/sửa/xóa/reset dữ liệu), không ghi lịch sử. */
    public void refresh() {
        if (shownRequest != null) {
            runSearch(shownRequest, Selection.BEST);
        }
    }

    /** Slang đang chọn trong bảng (dùng cho Sửa/Xóa). Có thể là bản cũ: lấy lại bản mới theo id trước khi sửa. */
    public Optional<SlangEntry> selectedEntry() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(tableModel.get(row).entry());
    }

    /** F1 hay F2, theo target đang chọn: để chuyển về đúng màn hình Search mà không đổi target. */
    public Feature currentFeature() {
        return featureOf(target);
    }

    /** Chữ đang gõ ở ô tìm nếu đang tìm theo slang (gợi ý sẵn tên cho Thêm), ngược lại là chuỗi rỗng. */
    public String slangQuery() {
        return target == SearchTarget.SLANG ? queryField.getText().strip() : "";
    }

    /**
     * Sau khi thêm/sửa: hiện thông báo, tìm lại và chọn sẵn {@code entry}. Đang có kết quả thì giữ nguyên lần tìm đó
     * (người dùng không mất chỗ đang xem); nếu entry không nằm trong đó (ví dụ vừa đổi tên) hoặc chưa tìm gì thì
     * tìm theo tên của entry để nó hiện ra. Không ghi lịch sử.
     */
    public void showChange(SlangEntry entry, String noticeKey, Object... args) {
        Objects.requireNonNull(entry, "entry");
        setNotice(NoticeKind.SUCCESS, noticeKey, args);
        if (shownRequest != null) {
            runSearch(shownRequest, new Selection(entry.getId(), 0, entry));
        } else {
            reveal(entry);
        }
    }

    /** Sau khi xóa: hiện thông báo, tìm lại và chọn dòng ở đúng chỗ vừa xóa (nhấn Delete liên tiếp được). */
    public void showRemoval(String noticeKey, Object... args) {
        setNotice(NoticeKind.SUCCESS, noticeKey, args);
        if (shownRequest != null) {
            runSearch(shownRequest, new Selection(-1, Math.max(0, table.getSelectedRow()), null));
        }
    }

    /** Hướng dẫn ngắn (ví dụ "hãy chọn một slang trước"), đưa focus về ô tìm. */
    public void showHint(String noticeKey, Object... args) {
        setNotice(NoticeKind.INFO, noticeKey, args);
        SwingUtilities.invokeLater(queryField::requestFocusInWindow);
    }

    /** Tìm entry theo tên của nó (target Slang, giữ mode đang chọn: mode nào cũng có tên trùng khớp ở đầu). */
    private void reveal(SlangEntry entry) {
        liveTimer.stop();
        target = SearchTarget.SLANG;
        setQueryQuietly(entry.getSlang());
        syncControls();
        updateHint();
        navigator.accept(Feature.SEARCH_SLANG);
        runSearch(currentRequest(), new Selection(entry.getId(), 0, null));
    }

    // ===================== Dựng giao diện =====================

    private JComponent buildSearchBar() {
        // Hai tab chọn target: ButtonGroup đảm bảo luôn đúng một tab được chọn
        ButtonGroup targets = new ButtonGroup();
        for (JToggleButton tab : List.of(slangTab, definitionTab)) {
            tab.putClientProperty("JButton.buttonType", "tab");
            tab.setFocusable(false);
            targets.add(tab);
        }
        slangTab.addActionListener(e -> changeTarget(SearchTarget.SLANG, true));
        definitionTab.addActionListener(e -> changeTarget(SearchTarget.DEFINITION, true));
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabs.add(slangTab);
        tabs.add(definitionTab);

        // Ô tìm kiếm: placeholder + nút ✕ + icon kính lúp của FlatLaf
        queryField.putClientProperty("JTextField.showClearButton", true);
        if (ThemeManager.isThemeSwitchingSupported()) {
            queryField.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        }
        queryField.addActionListener(e -> submit()); // Enter
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onQueryEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onQueryEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onQueryEdited();
            }
        });
        bindKey(queryField, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "focusResults", this::focusResults);
        bindKey(queryField, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearQuery", () -> queryField.setText(""));

        modeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof SearchMode mode) {
                    setText(Messages.get(modeKey(mode)));
                }
                return this;
            }
        });
        modeCombo.addActionListener(e -> onModeChanged());
        modeLabel.setLabelFor(modeCombo);
        searchButton.addActionListener(e -> submit());

        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        row.add(queryField, gbc);
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 12, 0, 0);
        row.add(modeLabel, gbc);
        gbc.insets = new Insets(0, 6, 0, 0);
        row.add(modeCombo, gbc);
        gbc.insets = new Insets(0, 8, 0, 0);
        row.add(searchButton, gbc);

        JPanel bar = new JPanel(new BorderLayout(0, 6));
        bar.add(tabs, BorderLayout.NORTH);
        bar.add(row, BorderLayout.CENTER);
        bar.add(hintLabel, BorderLayout.SOUTH);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return bar;
    }

    private JComponent buildResultArea() {
        // Dòng trạng thái + gợi ý (trái) và nút thao tác (phải)
        // Bề rộng ưu tiên = 0: thông báo/gợi ý dài chỉ bị cắt bớt, không được kéo giãn cả màn hình (làm co ô tìm, combo)
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, super.getPreferredSize().height);
            }
        };
        left.add(statusLabel);
        left.add(noticeLabel);
        left.add(suggestionPanel);
        noticeLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        suggestionPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        configureActionButton(addButton, Feature.ADD);
        configureActionButton(editButton, Feature.EDIT);
        configureActionButton(deleteButton, Feature.DELETE);
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deleteButton);

        JPanel info = new JPanel(new BorderLayout());
        info.add(left, BorderLayout.CENTER);
        info.add(actions, BorderLayout.EAST);
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        // Bảng kết quả
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false); // giữ thứ tự xếp hạng của SearchService
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new TooltipCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(480);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected", () -> {
            if (table.getSelectedRow() >= 0) {
                navigator.accept(Feature.DELETE);
            }
        });
        // Bấm đúp một dòng = Sửa (F5)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)
                        && table.rowAtPoint(e.getPoint()) >= 0) {
                    navigator.accept(Feature.EDIT);
                }
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);

        // Chi tiết slang đang chọn
        detailText.setEditable(false);
        detailText.setLineWrap(true);
        detailText.setWrapStyleWord(true);
        JPanel detailHeader = new JPanel(new BorderLayout(0, 4));
        detailHeader.add(detailTitle, BorderLayout.NORTH);
        detailHeader.add(detailCount, BorderLayout.CENTER);
        detailHeader.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        JScrollPane detailScroll = new JScrollPane(detailText);
        detailScroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        JPanel detail = new JPanel(new BorderLayout());
        detail.add(detailHeader, BorderLayout.NORTH);
        detail.add(detailScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detail);
        split.setResizeWeight(0.65);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());

        JPanel area = new JPanel(new BorderLayout());
        area.add(info, BorderLayout.NORTH);
        area.add(split, BorderLayout.CENTER);
        return area;
    }

    private void configureActionButton(JButton button, Feature feature) {
        button.setIcon(new NumberBadgeIcon(feature.number()));
        button.addActionListener(e -> navigator.accept(feature));
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

    // ===================== Xử lý thao tác =====================

    private void changeTarget(SearchTarget newTarget, boolean byUser) {
        target = newTarget;
        syncControls();
        updateHint();
        if (byUser) {
            clearNotice();
            // Báo MainFrame để Sidebar + tiêu đề cửa sổ đổi theo F1 ↔ F2 (MainFrame sẽ gọi lại onShown, lúc đó
            // target đã khớp nên không làm gì thêm)
            navigator.accept(featureOf(target));
        }
        rerunIfShowing();
    }

    private static Feature featureOf(SearchTarget searchTarget) {
        return searchTarget == SearchTarget.SLANG ? Feature.SEARCH_SLANG : Feature.SEARCH_DEFINITION;
    }

    private void onModeChanged() {
        if (syncing) {
            return;
        }
        modeByTarget.put(target, (SearchMode) modeCombo.getSelectedItem());
        clearNotice();
        updateHint();
        rerunIfShowing();
    }

    /** Đổi target hoặc mode khi đang có kết quả → tìm lại luôn với query hiện tại (không ghi lịch sử). */
    private void rerunIfShowing() {
        liveTimer.stop();
        if (queryField.getText().isBlank()) {
            clearResults();
        } else if (shownRequest != null || isLiveMode()) {
            runSearch(currentRequest(), Selection.BEST);
        }
    }

    private void onQueryEdited() {
        if (syncing) {
            return;
        }
        clearNotice();
        if (queryField.getText().isBlank()) {
            liveTimer.stop();
            clearResults();
        } else if (isLiveMode()) {
            liveTimer.restart(); // gõ tiếp trong vòng LIVE_DELAY_MS thì hoãn tiếp
        }
    }

    private boolean isLiveMode() {
        return target == SearchTarget.SLANG && currentMode() == SearchMode.PREFIX;
    }

    /** Tìm "thật": Enter, nút Tìm, bấm gợi ý. Được ghi vào lịch sử. */
    private void submit() {
        liveTimer.stop();
        clearNotice();
        String query = queryField.getText().strip();
        if (query.isEmpty()) {
            clearResults();
            queryField.requestFocusInWindow();
            return;
        }
        Request request = currentRequest();
        searchListener.accept(new HistoryItem(request.target(), request.mode(), query, LocalDateTime.now()));
        runSearch(request, Selection.BEST);
    }

    private Request currentRequest() {
        return new Request(target, currentMode(), queryField.getText().strip());
    }

    private SearchMode currentMode() {
        return modeByTarget.get(target);
    }

    /** Gửi việc tìm sang luồng nền; kết quả về EDT ở {@link #showOutcome}, chọn sẵn dòng theo {@code selection}. */
    private void runSearch(Request request, Selection selection) {
        if (request.query().isEmpty()) {
            clearResults();
            return;
        }
        long ticket = generation.incrementAndGet();
        busyTimer.restart();
        executor.execute(() -> {
            if (generation.get() != ticket) {
                return; // đã có yêu cầu mới hơn trong lúc chờ → khỏi tìm
            }
            SearchOutcome outcome = null;
            List<String> suggestions = List.of();
            RuntimeException error = null;
            try {
                outcome = service.search(request.target(), request.mode(), request.query(), RESULT_LIMIT);
                if (outcome.isEmpty() && request.mode() != SearchMode.FUZZY) {
                    suggestions = service.suggestions(request.target(), request.query(), SUGGESTION_LIMIT);
                }
            } catch (RuntimeException e) {
                error = e;
            }
            SearchOutcome finalOutcome = outcome;
            List<String> finalSuggestions = suggestions;
            RuntimeException finalError = error;
            SwingUtilities.invokeLater(() -> {
                if (generation.get() == ticket) { // kết quả cũ về muộn thì bỏ
                    showOutcome(request, finalOutcome, finalSuggestions, finalError, selection);
                }
            });
        });
    }

    private void showOutcome(Request request, SearchOutcome outcome, List<String> suggestions,
                             RuntimeException error, Selection selection) {
        busyTimer.stop();
        if (error == null && selection.revealIfMissing() != null && indexOfId(outcome, selection.id()) < 0) {
            // Entry vừa sửa không còn khớp lần tìm đang hiện (ví dụ vừa đổi tên) → tìm theo tên mới để nó hiện ra
            reveal(selection.revealIfMissing());
            return;
        }
        shownRequest = request;
        if (error != null) {
            shownOutcome = null;
            shownSuggestions = List.of();
            tableModel.setResults(List.of());
            errorMessage = String.valueOf(error.getMessage());
            setStatus(Status.ERROR);
        } else {
            shownOutcome = outcome;
            shownSuggestions = suggestions;
            tableModel.setResults(outcome.results());
            setStatus(Status.DONE);
        }
        renderSuggestions();
        int rows = tableModel.getRowCount();
        if (rows > 0) {
            // Mặc định chọn sẵn kết quả tốt nhất → khung chi tiết hiện ngay; sau thêm/sửa/xóa thì chọn theo selection
            int byId = error == null ? indexOfId(outcome, selection.id()) : -1;
            int row = byId >= 0 ? byId : Math.min(Math.max(selection.row(), 0), rows - 1);
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        } else {
            onSelectionChanged();
        }
    }

    /** Vị trí của entry {@code id} trong các kết quả được đưa lên bảng, -1 nếu không có. */
    private static int indexOfId(SearchOutcome outcome, int id) {
        if (id < 0) {
            return -1;
        }
        List<SearchResult> results = outcome.results();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).entry().getId() == id) {
                return i;
            }
        }
        return -1;
    }

    private void clearResults() {
        generation.incrementAndGet(); // bỏ mọi kết quả đang chờ
        busyTimer.stop();
        shownRequest = null;
        shownOutcome = null;
        shownSuggestions = List.of();
        tableModel.setResults(List.of());
        setStatus(Status.IDLE);
        renderSuggestions();
        onSelectionChanged();
    }

    private void applySuggestion(String suggestion) {
        setQueryQuietly(suggestion);
        submit();
    }

    private void focusResults() {
        if (tableModel.getRowCount() == 0) {
            return;
        }
        if (table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        }
        table.requestFocusInWindow();
    }

    private void onSelectionChanged() {
        boolean selected = table.getSelectedRow() >= 0;
        editButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        showDetail(selectedEntry().orElse(null));
    }

    /** Đặt chữ vào ô tìm mà không kích hoạt tìm-khi-gõ. */
    private void setQueryQuietly(String text) {
        syncing = true;
        try {
            queryField.setText(text);
        } finally {
            syncing = false;
        }
    }

    /** Đưa tab + combo về đúng target/mode hiện tại. */
    private void syncControls() {
        syncing = true;
        try {
            slangTab.setSelected(target == SearchTarget.SLANG);
            definitionTab.setSelected(target == SearchTarget.DEFINITION);
            modeCombo.setSelectedItem(currentMode());
        } finally {
            syncing = false;
        }
    }

    // ===================== Hiển thị =====================

    private void setStatus(Status newStatus) {
        status = newStatus;
        renderStatus();
    }

    private void renderStatus() {
        Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
        switch (status) {
            case IDLE -> {
                statusLabel.setText(Messages.get("search.status.idle"));
                statusLabel.setForeground(muted);
            }
            case SEARCHING -> {
                statusLabel.setText(Messages.get("search.status.searching"));
                statusLabel.setForeground(muted);
            }
            case ERROR -> {
                statusLabel.setText(Messages.format("search.status.error", errorMessage));
                statusLabel.setForeground(ThemeManager.color("Actions.Red", Color.RED));
            }
            case DONE -> {
                double ms = shownOutcome.elapsedMillis();
                if (shownOutcome.isEmpty()) {
                    statusLabel.setText(Messages.format("search.status.none", ms));
                } else if (shownOutcome.isTruncated()) {
                    statusLabel.setText(Messages.format("search.status.truncated",
                            shownOutcome.results().size(), shownOutcome.totalMatches(), ms));
                } else {
                    statusLabel.setText(Messages.format("search.status.results", shownOutcome.totalMatches(), ms));
                }
                statusLabel.setForeground(ThemeManager.color("Label.foreground", Color.BLACK));
            }
        }
    }

    private void setNotice(NoticeKind kind, String key, Object... args) {
        noticeKind = kind;
        noticeKey = Objects.requireNonNull(key, "key");
        noticeArgs = args;
        renderNotice();
    }

    /** Người dùng tự tìm/gõ tiếp → thông báo của lần thêm/sửa/xóa trước không còn liên quan. */
    private void clearNotice() {
        if (noticeKey != null) {
            noticeKey = null;
            renderNotice();
        }
    }

    private void renderNotice() {
        if (noticeKey == null) {
            noticeLabel.setText("");
            noticeLabel.setVisible(false);
            return;
        }
        String mark = noticeKind == NoticeKind.SUCCESS ? "✓ " : "→ ";
        noticeLabel.setText(mark + Messages.format(noticeKey, noticeArgs));
        noticeLabel.setForeground(noticeKind == NoticeKind.SUCCESS
                ? ThemeManager.color("Actions.Green", new Color(0x2E7D32)) : ThemeManager.accentColor());
        noticeLabel.setVisible(true);
    }

    private void renderSuggestions() {
        suggestionPanel.removeAll();
        if (!shownSuggestions.isEmpty()) {
            suggestLabel.setText(Messages.get("search.suggest"));
            suggestionPanel.add(suggestLabel);
            for (String suggestion : shownSuggestions) {
                JButton link = new JButton(suggestion);
                link.putClientProperty("JButton.buttonType", "borderless");
                link.setForeground(ThemeManager.accentColor());
                link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                link.setMargin(new Insets(0, 4, 0, 4));
                link.addActionListener(e -> applySuggestion(suggestion));
                suggestionPanel.add(link);
            }
        }
        suggestionPanel.revalidate();
        suggestionPanel.repaint();
    }

    private void showDetail(SlangEntry entry) {
        detailText.getHighlighter().removeAllHighlights();
        if (entry == null) {
            detailTitle.setText(" ");
            detailCount.setText(Messages.get("search.detail.empty"));
            detailText.setText("");
            return;
        }
        detailTitle.setText(entry.getSlang());
        detailCount.setText(Messages.format("search.detail.meanings", entry.getMeanings().size()));
        StringBuilder text = new StringBuilder();
        List<String> meanings = entry.getMeanings();
        for (int i = 0; i < meanings.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(i + 1).append(". ").append(meanings.get(i));
        }
        detailText.setText(text.toString());
        detailText.setCaretPosition(0);

        // Tô các từ khóa khớp trong nghĩa (chỉ khi tìm theo definition)
        if (shownRequest != null && shownRequest.target() == SearchTarget.DEFINITION) {
            DefaultHighlighter.DefaultHighlightPainter painter =
                    new DefaultHighlighter.DefaultHighlightPainter(highlightColor());
            for (int[] range : matchRanges(detailText.getText(), shownRequest.mode(), shownRequest.query())) {
                try {
                    detailText.getHighlighter().addHighlight(range[0], range[1], painter);
                } catch (BadLocationException ignored) {
                    // không xảy ra: vị trí lấy từ chính đoạn text này
                }
            }
        }
    }

    private static Color highlightColor() {
        return ThemeManager.current() == Theme.DARK ? new Color(0x6B5A12) : new Color(0xFFE58A);
    }

    /**
     * Vị trí các đoạn trong {@code text} khớp với từ khóa của query, theo đúng kiểu khớp của mode:
     * EXACT khớp nguyên từ, PREFIX khớp đầu từ, SUBSTRING khớp ở bất kỳ đâu; FUZZY không tô (từ khớp có thể
     * khác hẳn chữ đã gõ). Không phân biệt hoa thường.
     *
     * @return danh sách cặp {@code [start, end)}
     */
    static List<int[]> matchRanges(String text, SearchMode mode, String query) {
        List<String> tokens = new ArrayList<>(new LinkedHashSet<>(Tokenizer.tokens(query)));
        if (tokens.isEmpty() || mode == SearchMode.FUZZY) {
            return List.of();
        }
        tokens.sort(Comparator.comparingInt(String::length).reversed()); // ưu tiên từ dài khi chồng nhau
        String alternatives = tokens.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        String wordChar = "[\\p{L}\\p{N}]";
        String regex = switch (mode) {
            case EXACT -> "(?<!" + wordChar + ")(?:" + alternatives + ")(?!" + wordChar + ")";
            case PREFIX -> "(?<!" + wordChar + ")(?:" + alternatives + ")";
            default -> "(?:" + alternatives + ")";
        };
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        List<int[]> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[] {matcher.start(), matcher.end()});
        }
        return ranges;
    }

    private void updateHint() {
        String suffix = target.name().toLowerCase(Locale.ROOT);
        queryField.putClientProperty("JTextField.placeholderText", Messages.get("search.placeholder." + suffix));
        hintLabel.setText(Messages.get("search.hint." + suffix + "." + currentMode().name().toLowerCase(Locale.ROOT)));
        modeCombo.setToolTipText(hintLabel.getText());
        queryField.repaint();
    }

    private static String modeKey(SearchMode mode) {
        return "search.mode." + mode.name().toLowerCase(Locale.ROOT);
    }

    private void applyTexts() {
        slangTab.setText(Messages.get("search.target.slang"));
        definitionTab.setText(Messages.get("search.target.definition"));
        slangTab.setToolTipText(featureTooltip(Feature.SEARCH_SLANG));
        definitionTab.setToolTipText(featureTooltip(Feature.SEARCH_DEFINITION));
        modeLabel.setText(Messages.get("search.mode.label"));
        modeCombo.repaint();
        searchButton.setText(Messages.get("search.button"));
        addButton.setText(Messages.get("search.action.add"));
        editButton.setText(Messages.get("search.action.edit"));
        deleteButton.setText(Messages.get("search.action.delete"));
        addButton.setToolTipText(featureTooltip(Feature.ADD));
        editButton.setToolTipText(featureTooltip(Feature.EDIT));
        deleteButton.setToolTipText(featureTooltip(Feature.DELETE));
        table.getColumnModel().getColumn(0).setHeaderValue(tableModel.getColumnName(0));
        table.getColumnModel().getColumn(1).setHeaderValue(tableModel.getColumnName(1));
        table.getTableHeader().repaint();
        updateHint();
        renderStatus();
        renderNotice();
        renderSuggestions();
        showDetail(selectedEntry().orElse(null));
    }

    private static String featureTooltip(Feature feature) {
        return Messages.get(feature.descriptionKey()) + " (" + Sidebar.acceleratorText(feature.accelerator()) + ")";
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (detailTitle != null) { // updateUI được gọi cả trong constructor của JPanel, lúc field chưa có
            applyStyles();
            renderStatus();
            renderNotice();
            renderSuggestions();
            showDetail(selectedEntry().orElse(null));
        }
    }

    private void applyStyles() {
        Font font = UIManager.getFont("Label.font");
        if (font == null) {
            return;
        }
        Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
        hintLabel.setFont(font.deriveFont(font.getSize2D() - 1f));
        hintLabel.setForeground(muted);
        detailTitle.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 8f));
        detailCount.setForeground(muted);
        detailText.setFont(font.deriveFont(font.getSize2D() + 1f));
        detailText.setMargin(new Insets(4, 4, 4, 4));
        queryField.setFont(font.deriveFont(font.getSize2D() + 2f));
        Font tabFont = font.deriveFont(Font.BOLD);
        slangTab.setFont(tabFont);
        definitionTab.setFont(tabFont);
        table.setRowHeight(Math.round(font.getSize2D() * 2f));
    }

    // ===================== Cho test =====================

    JTextField queryField() {
        return queryField;
    }

    JComboBox<SearchMode> modeCombo() {
        return modeCombo;
    }

    JToggleButton definitionTab() {
        return definitionTab;
    }

    JTable table() {
        return table;
    }

    SearchTarget target() {
        return target;
    }

    List<String> shownSuggestions() {
        return shownSuggestions;
    }

    String statusText() {
        return statusLabel.getText();
    }

    String detailText() {
        return detailText.getText();
    }

    /** Thông báo đang hiện ("" nếu không có). */
    String noticeText() {
        return noticeLabel.getText();
    }

    /** Các slang đang hiện trên bảng, theo thứ tự. */
    List<String> slangColumn() {
        List<String> slangs = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            slangs.add(tableModel.get(row).entry().getSlang());
        }
        return slangs;
    }

    /** Chọn dòng như người dùng bấm vào. */
    void selectRow(int row) {
        table.setRowSelectionInterval(row, row);
    }

    /** Bấm gợi ý thứ {@code index} như người dùng. */
    void clickSuggestion(int index) {
        applySuggestion(shownSuggestions.get(index));
    }

    // ===================== Model của bảng =====================

    /** Bảng 2 cột Slang | Nghĩa, dữ liệu là danh sách kết quả đã xếp hạng. */
    private static final class ResultTableModel extends AbstractTableModel {

        private List<SearchResult> results = List.of();

        void setResults(List<SearchResult> newResults) {
            results = newResults;
            fireTableDataChanged();
        }

        SearchResult get(int row) {
            return results.get(row);
        }

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return Messages.get(column == 0 ? "search.column.slang" : "search.column.meanings");
        }

        @Override
        public Object getValueAt(int row, int column) {
            SlangEntry entry = results.get(row).entry();
            return column == 0 ? entry.getSlang() : entry.getMeaningText();
        }
    }

    /** Ô bị cắt bớt ("…") thì rê chuột vào sẽ thấy đủ nội dung. */
    private static final class TooltipCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            int width = table.getColumnModel().getColumn(column).getWidth();
            setToolTipText(getFontMetrics(getFont()).stringWidth(text) > width - 8 ? text : null);
            return this;
        }
    }
}
