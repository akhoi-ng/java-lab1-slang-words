package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.RandomService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.FeatureScreen;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.PlainText;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Màn hình F8 "Slang hôm nay" (On this day slang word).
 * <pre>
 * ┌───────────────────────────────────────────────────────────────────┐
 * │ [8] Slang hôm nay                                                  │
 * │ Thứ Tư, 11/11/2026 · mỗi ngày một slang, cả ngày giữ nguyên         │
 * │ ╭───────────────────────────────────────────────────────────────╮ │
 * │ │ SLANG CỦA NGÀY                     (hoặc SLANG NGẪU NHIÊN)      │ │
 * │ │ yeet                                                          │ │
 * │ │ 2 nghĩa                                                       │ │
 * │ │ 1. To throw something with force                              │ │
 * │ │ 2. An exclamation of excitement                               │ │
 * │ ╰───────────────────────────────────────────────────────────────╯ │
 * │ [Random khác (R)]  [Về slang hôm nay (H)]  [Tra trong từ điển ↵]   │
 * └───────────────────────────────────────────────────────────────────┘
 * </pre>
 * Slang hôm nay do {@link RandomService#onThisDay} chọn: cả ngày (kể cả tắt app mở lại) ra cùng một slang.
 * Từ điển thay đổi (thêm/sửa/xóa/Reset) thì màn hình tự cập nhật: slang đang hiện bị sửa thì hiện bản mới, bị xóa
 * thì chọn lại. Việc tính lại được hoãn tới lúc màn hình hiện ra, nên sửa từ điển ở màn hình khác không tốn gì.
 */
public class RandomPanel extends JPanel implements FeatureScreen {

    private final RandomService service;
    private final Consumer<String> lookup;
    private final Supplier<LocalDate> today;

    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel kindLabel = new JLabel();
    private final JLabel slangLabel = PlainText.of(new JLabel()); // tên slang hiện nguyên văn, kể cả "<html>…"
    private final JLabel countLabel = new JLabel();
    private final JTextArea meaningsText = new JTextArea();
    private final JButton randomButton = new JButton();
    private final JButton todayButton = new JButton();
    private final JButton lookupButton = new JButton();

    /** Slang đang hiện, null nếu từ điển trống (hoặc chưa hiện lần nào). */
    private SlangEntry shown;
    /** true: đang hiện slang của ngày; false: đang hiện slang random. */
    private boolean showingToday = true;
    /** Ngày của slang hôm nay đang hiện: qua nửa đêm thì lần hiện sau tự đổi sang slang của ngày mới. */
    private LocalDate shownDate;
    /** Từ điển đã đổi từ lần hiện trước, cần tính lại. */
    private boolean stale = true;

    /**
     * @param lookup tra slang đang hiện ở màn hình tìm kiếm (App truyền hàm gọi {@code searchPanel.search(...)})
     */
    public RandomPanel(RandomService service, Consumer<String> lookup) {
        this(service, lookup, LocalDate::now);
    }

    /** Cho test: ngày "hôm nay" do test quyết định. */
    RandomPanel(RandomService service, Consumer<String> lookup, Supplier<LocalDate> today) {
        super(new BorderLayout());
        this.service = Objects.requireNonNull(service, "service");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.today = Objects.requireNonNull(today, "today");

        // Thẻ cao vừa đủ nội dung, nút nằm ngay dưới thẻ; slang nhiều nghĩa thì cả vùng này cuộn được
        JPanel column = new JPanel(new BorderLayout());
        column.add(buildCard(), BorderLayout.CENTER);
        column.add(buildActions(), BorderLayout.SOUTH);
        WidthTrackingPanel body = new WidthTrackingPanel();
        body.add(column, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(buildHeader(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "random", this::showRandom);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "today", this::showToday);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "lookup", this::lookupShown);

        applyStyles();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    // ===================== FeatureScreen / listener =====================

    /** Mỗi lần mở màn hình: tính lại nếu dữ liệu đã đổi hoặc đã sang ngày mới. */
    @Override
    public void onShown(Feature via) {
        if (stale || (showingToday && !today.get().equals(shownDate))) {
            reconcile();
        }
        SwingUtilities.invokeLater(randomButton::requestFocusInWindow);
    }

    /** App nối vào {@code DictionaryService.addChangeListener}: thêm/sửa/xóa/Reset. */
    public void dictionaryChanged() {
        stale = true;
        if (isShowing()) {
            reconcile();
        }
    }

    /** Đưa màn hình về khớp với từ điển hiện tại mà không đổi "chế độ" người dùng đang xem. */
    private void reconcile() {
        if (showingToday || shown == null) {
            showToday(); // thường vẫn ra đúng slang cũ, xem RandomService.onThisDay
            return;
        }
        service.latest(shown).ifPresentOrElse(this::show, this::showRandom); // bị xóa thì random slang khác
    }

    // ===================== Thao tác =====================

    /** Slang của ngày hôm nay. */
    void showToday() {
        shownDate = today.get();
        showingToday = true;
        show(service.onThisDay(shownDate).orElse(null));
    }

    /** Một slang ngẫu nhiên khác slang đang hiện. */
    void showRandom() {
        showingToday = false;
        show(service.random(shown == null ? -1 : shown.getId()).orElse(null));
    }

    /** Tra slang đang hiện ở màn hình tìm kiếm (để xem kỹ, sửa hoặc xóa). */
    void lookupShown() {
        if (shown != null) {
            lookup.accept(shown.getSlang());
        }
    }

    private void show(SlangEntry entry) {
        shown = entry;
        stale = false;
        render();
    }

    // ===================== Dựng giao diện =====================

    private JComponent buildHeader() {
        title.setIcon(new NumberBadgeIcon(Feature.RANDOM.number()));
        title.setIconTextGap(10);
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        return header;
    }

    private JComponent buildCard() {
        meaningsText.setEditable(false);
        meaningsText.setLineWrap(true);
        meaningsText.setWrapStyleWord(true);
        meaningsText.setOpaque(false);
        meaningsText.setFocusable(false);
        meaningsText.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(kindLabel, BorderLayout.NORTH);
        top.add(slangLabel, BorderLayout.CENTER);
        top.add(countLabel, BorderLayout.SOUTH);

        RoundedCard card = new RoundedCard();
        card.add(top, BorderLayout.NORTH);
        card.add(meaningsText, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildActions() {
        randomButton.addActionListener(e -> showRandom());
        todayButton.addActionListener(e -> showToday());
        lookupButton.addActionListener(e -> lookupShown());
        // Nút đang giữ focus tự nhận Enter là "bấm nút" (FlatLaf), mà mở màn hình thì focus nằm ở "Random khác":
        // Enter sẽ random thay vì tra như tooltip ghi. Gỡ Enter khỏi các nút để phím tắt Enter của màn hình
        // (bindKey bên dưới constructor) luôn là "Tra trong từ điển"; Space vẫn bấm nút đang focus.
        for (JButton button : List.of(randomButton, todayButton, lookupButton)) {
            button.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
            button.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "none");
        }
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(randomButton);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(todayButton);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(lookupButton);
        actions.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        return actions;
    }

    /** Phím tắt dùng được khi focus nằm bất kỳ đâu trong màn hình này (không ảnh hưởng màn hình khác). */
    private void bindKey(KeyStroke key, String name, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ===================== Hiển thị =====================

    private void render() {
        LocalDate date = shownDate != null ? shownDate : today.get();
        String dateText = DateTimeFormatter.ofPattern(Messages.get("random.datePattern"), Messages.getLocale())
                .format(date);
        subtitle.setText(Messages.format("random.subtitle", capitalize(dateText)));
        kindLabel.setText(Messages.get(showingToday ? "random.kind.today" : "random.kind.random"));

        boolean hasEntry = shown != null;
        if (hasEntry) {
            slangLabel.setText(shown.getSlang());
            countLabel.setText(Messages.format("search.detail.meanings", shown.getMeanings().size()));
            meaningsText.setText(numbered(shown.getMeanings()));
            meaningsText.setCaretPosition(0);
        } else {
            slangLabel.setText("—");
            countLabel.setText(" ");
            meaningsText.setText(Messages.get("random.empty"));
        }
        randomButton.setEnabled(hasEntry);
        todayButton.setEnabled(hasEntry && !showingToday);
        lookupButton.setEnabled(hasEntry);
    }

    private static String numbered(List<String> meanings) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < meanings.size(); i++) {
            text.append(i == 0 ? "" : "\n").append(i + 1).append(". ").append(meanings.get(i));
        }
        return text.toString();
    }

    /** "thứ tư, …" → "Thứ tư, …" (tên thứ của một số locale viết thường ở đầu). */
    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void applyTexts() {
        title.setText(Messages.get(Feature.RANDOM.titleKey()));
        randomButton.setText(Messages.get("random.action.random"));
        todayButton.setText(Messages.get("random.action.today"));
        lookupButton.setText(Messages.get("random.action.lookup"));
        randomButton.setToolTipText(Messages.get("random.action.random.tip"));
        todayButton.setToolTipText(Messages.get("random.action.today.tip"));
        lookupButton.setToolTipText(Messages.get("random.action.lookup.tip"));
        render();
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
        subtitle.setForeground(muted);
        kindLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
        kindLabel.setForeground(ThemeManager.accentColor());
        slangLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 26f));
        countLabel.setForeground(muted);
        meaningsText.setFont(font.deriveFont(font.getSize2D() + 3f));
        randomButton.setFont(font.deriveFont(Font.BOLD));
    }

    // ===================== Cho test =====================

    SlangEntry shownEntry() {
        return shown;
    }

    JLabel slangLabel() {
        return slangLabel;
    }

    JButton randomButton() {
        return randomButton;
    }

    boolean showingToday() {
        return showingToday;
    }

    String slangText() {
        return slangLabel.getText();
    }

    String meaningsText() {
        return meaningsText.getText();
    }

    String subtitleText() {
        return subtitle.getText();
    }

    String kindText() {
        return kindLabel.getText();
    }

    boolean todayButtonEnabled() {
        return todayButton.isEnabled();
    }
}
