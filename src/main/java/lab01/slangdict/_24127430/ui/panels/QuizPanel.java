package lab01.slangdict._24127430.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.QuizQuestion;
import lab01.slangdict._24127430.model.QuizType;
import lab01.slangdict._24127430.model.Theme;
import lab01.slangdict._24127430.service.QuizService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.FeatureScreen;
import lab01.slangdict._24127430.ui.NumberBadgeIcon;
import lab01.slangdict._24127430.ui.ThemeManager;

/**
 * Màn hình đố vui F9 (slang → nghĩa) và F10 (nghĩa → slang); App tạo hai object, mỗi loại một màn hình.
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │ [9] Đố: slang → nghĩa                          Số câu: [10 câu ▾]   │
 * │ Chọn nghĩa đúng của slang · phím 1–4 để chọn, Enter sang câu tiếp    │
 * │ CÂU 3/10      ĐÚNG 2/2      CHUỖI ĐÚNG 2      KỶ LỤC 5               │
 * │ ╭──────────────────────────────────────────────────────────────────╮ │
 * │ │ SLANG NÀY NGHĨA LÀ GÌ?                                            │ │
 * │ │ yeet                                                             │ │
 * │ ╰──────────────────────────────────────────────────────────────────╯ │
 * │ [1  To throw something with force          ]  ← xanh: đáp án đúng    │
 * │ [2  Laugh out loud                         ]  ← đỏ: đã chọn mà sai   │
 * │ [3  …                                      ]                         │
 * │ [4  …                                      ]                         │
 * │ ✗ Chưa đúng. Đáp án đúng là 1.                                       │
 * │ [Câu tiếp ↵]  [Chơi lại]                                             │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Chế độ "Không giới hạn" hỏi mãi; chế độ 10/20 câu hết lượt thì hiện bảng tổng kết (điểm, %, chuỗi dài nhất, các câu
 * sai). Đổi chế độ là bắt đầu lượt mới. Kỷ lục chuỗi đúng giữ suốt lúc app chạy.
 * <p>
 * Từ điển đổi (thêm/sửa/xóa/Reset) thì câu <b>chưa trả lời</b> được kiểm tra lại bằng {@link QuizService#isValid}:
 * nếu đáp án đúng không còn đúng hoặc một đáp án nhiễu trở thành đúng thì đổi sang câu khác (không tính điểm).
 */
public class QuizPanel extends JPanel implements FeatureScreen {

    /** Các lựa chọn số câu một lượt; 0 = không giới hạn. */
    static final List<Integer> ROUND_LENGTHS = List.of(0, 10, 20);

    private final QuizType type;
    private final Feature feature;
    private final QuizService service;

    // ----- giao diện -----
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel modeLabel = new JLabel();
    private final JComboBox<Integer> modeCombo = new JComboBox<>(ROUND_LENGTHS.toArray(new Integer[0]));
    private final StatChip questionStat = new StatChip();
    private final StatChip correctStat = new StatChip();
    private final StatChip streakStat = new StatChip();
    private final StatChip bestStat = new StatChip();
    private final WidthTrackingPanel body = new WidthTrackingPanel();

    private final JComponent playView;
    private final JLabel kindLabel = new JLabel();
    private final JTextArea promptText = wrappingText();
    private final List<OptionButton> optionButtons = new ArrayList<>();
    private final JLabel feedbackLabel = new JLabel(" ");
    private final JTextArea detailText = wrappingText();
    private final JButton nextButton = new JButton();
    private final JButton restartButton = new JButton();

    private final JComponent summaryView;
    private final JLabel summaryKind = new JLabel();
    private final JLabel summaryScore = new JLabel();
    private final JLabel summaryStats = new JLabel();
    private final JLabel summaryGrade = new JLabel();
    private final JLabel mistakesTitle = new JLabel();
    private final JTextArea mistakesText = wrappingText();
    private final JButton playAgainButton = new JButton();

    private final JComponent emptyView;
    private final JTextArea emptyText = wrappingText();

    // ----- trạng thái -----
    /** Câu đang hỏi; null khi từ điển không đủ dữ liệu hoặc chưa hiện lần nào. */
    private QuizQuestion question;
    /** Đáp án đã chọn cho câu đang hỏi, -1 nếu chưa trả lời. */
    private int chosen = -1;
    /** Số câu một lượt (0 = không giới hạn). */
    private int roundLength;
    private int answered;
    private int correct;
    private int streak;
    private int roundBestStreak;
    /** Chuỗi đúng dài nhất từ lúc mở app, không bị xóa khi chơi lại. */
    private int bestStreak;
    private final List<Mistake> mistakes = new ArrayList<>();
    private boolean showingSummary;
    /** Từ điển không đủ dữ liệu để tạo câu hỏi. */
    private boolean unavailable;
    /** Từ điển đã đổi từ lần kiểm tra trước. */
    private boolean stale;

    /** Một câu trả lời sai, để liệt kê trong bảng tổng kết. */
    private record Mistake(QuizQuestion question, int chosen) {
    }

    public QuizPanel(QuizType type, QuizService service) {
        super(new BorderLayout());
        this.type = Objects.requireNonNull(type, "type");
        this.service = Objects.requireNonNull(service, "service");
        this.feature = type == QuizType.SLANG_TO_DEFINITION ? Feature.QUIZ_SLANG : Feature.QUIZ_DEFINITION;

        playView = buildPlayView();
        summaryView = buildSummaryView();
        emptyView = buildEmptyView();

        JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.add(buildHeader(), BorderLayout.NORTH);
        top.add(buildStats(), BorderLayout.CENTER);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setFocusable(true); // nhận phím 1–4 / Enter khi chưa có nút nào giữ focus
        showView(playView);

        for (int i = 0; i < QuizQuestion.OPTION_COUNT; i++) {
            int index = i;
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, 0), "choose" + i, () -> choose(index));
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1 + i, 0), "chooseNumpad" + i, () -> choose(index));
        }
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next", this::advance);

        applyStyles();
        applyTexts();
        Messages.addListener(this::applyTexts);
    }

    // ===================== FeatureScreen / listener =====================

    @Override
    public void onShown(Feature via) {
        if (question == null || stale) {
            reconcile();
        }
        focusDefault();
    }

    /** App nối vào {@code DictionaryService.addChangeListener}: thêm/sửa/xóa/Reset. */
    public void dictionaryChanged() {
        stale = true;
        if (isShowing()) {
            reconcile();
        }
    }

    /** Đưa câu đang hỏi về khớp với từ điển hiện tại. Câu đã trả lời và bảng tổng kết giữ nguyên. */
    private void reconcile() {
        stale = false;
        if (showingSummary) {
            return;
        }
        if (question == null) {
            newQuestion();
        } else if (chosen < 0 && !service.isValid(question)) {
            newQuestion();
        }
    }

    // ===================== Thao tác =====================

    /** Chọn đáp án {@code index} (0..3). Bỏ qua nếu đã trả lời hoặc chưa có câu hỏi. */
    void choose(int index) {
        if (question == null || chosen >= 0 || showingSummary) {
            return;
        }
        chosen = index;
        answered++;
        if (question.isCorrect(index)) {
            correct++;
            streak++;
            roundBestStreak = Math.max(roundBestStreak, streak);
            bestStreak = Math.max(bestStreak, streak);
        } else {
            streak = 0;
            mistakes.add(new Mistake(question, index));
        }
        render();
        SwingUtilities.invokeLater(nextButton::requestFocusInWindow);
    }

    /** Enter: đã trả lời thì sang câu tiếp (hoặc bảng tổng kết), đang ở bảng tổng kết thì chơi lại. */
    void advance() {
        if (showingSummary) {
            restart();
        } else if (question != null && chosen >= 0) {
            if (roundFinished()) {
                showingSummary = true;
                showView(summaryView);
                render();
                SwingUtilities.invokeLater(playAgainButton::requestFocusInWindow);
            } else {
                newQuestion();
                focusDefault();
            }
        }
    }

    /** Bắt đầu lượt mới: điểm, chuỗi đúng, các câu sai về 0; kỷ lục giữ nguyên. */
    void restart() {
        answered = 0;
        correct = 0;
        streak = 0;
        roundBestStreak = 0;
        mistakes.clear();
        showingSummary = false;
        stale = false;
        newQuestion();
        focusDefault();
    }

    /** Đổi số câu một lượt (0 = không giới hạn); bắt đầu lượt mới. */
    void setRoundLength(int length) {
        modeCombo.setSelectedItem(length); // listener của combo gọi restart
    }

    private void onModeSelected() {
        Integer selected = (Integer) modeCombo.getSelectedItem();
        int length = selected == null ? 0 : selected;
        if (length != roundLength) {
            roundLength = length;
            restart();
        }
    }

    private boolean roundFinished() {
        return roundLength > 0 && answered >= roundLength;
    }

    private void newQuestion() {
        int avoid = question == null ? -1 : question.answer().getId();
        try {
            question = service.next(type, avoid);
            unavailable = false;
        } catch (QuizService.NotEnoughDataException e) {
            question = null;
            unavailable = true;
        }
        chosen = -1;
        showView(unavailable ? emptyView : playView);
        render();
    }

    private void focusDefault() {
        SwingUtilities.invokeLater(() -> {
            if (showingSummary) {
                playAgainButton.requestFocusInWindow();
            } else if (chosen >= 0) {
                nextButton.requestFocusInWindow();
            } else {
                requestFocusInWindow(); // các nút đáp án không giữ focus, chọn bằng chuột hoặc phím 1–4
            }
        });
    }

    // ===================== Dựng giao diện =====================

    private JComponent buildHeader() {
        title.setIcon(new NumberBadgeIcon(feature.number()));
        title.setIconTextGap(10);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(title, BorderLayout.NORTH);
        left.add(subtitle, BorderLayout.CENTER);

        modeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setText(roundLengthText((Integer) value));
                return this;
            }
        });
        modeCombo.addActionListener(e -> onModeSelected());
        modeLabel.setLabelFor(modeCombo);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(modeLabel);
        right.add(modeCombo);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildStats() {
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (StatChip chip : List.of(questionStat, correctStat, streakStat, bestStat)) {
            stats.add(chip);
            stats.add(Box.createHorizontalStrut(36));
        }
        return stats;
    }

    private JComponent buildPlayView() {
        RoundedCard card = new RoundedCard();
        JPanel cardContent = new JPanel(new BorderLayout(0, 6));
        cardContent.setOpaque(false);
        cardContent.add(kindLabel, BorderLayout.NORTH);
        cardContent.add(promptText, BorderLayout.CENTER);
        card.add(cardContent, BorderLayout.CENTER);

        JPanel options = new JPanel(new GridLayout(0, 1, 0, 8));
        for (int i = 0; i < QuizQuestion.OPTION_COUNT; i++) {
            OptionButton button = new OptionButton(i);
            optionButtons.add(button);
            options.add(button);
        }
        options.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        detailText.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JPanel feedback = new JPanel(new BorderLayout());
        feedback.add(feedbackLabel, BorderLayout.NORTH);
        feedback.add(detailText, BorderLayout.CENTER);
        feedback.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        nextButton.addActionListener(e -> advance());
        restartButton.addActionListener(e -> restart());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(nextButton);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(restartButton);
        actions.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        JPanel south = new JPanel(new BorderLayout());
        south.add(feedback, BorderLayout.NORTH);
        south.add(actions, BorderLayout.SOUTH);

        JPanel view = new JPanel(new BorderLayout());
        view.add(card, BorderLayout.NORTH);
        view.add(options, BorderLayout.CENTER);
        view.add(south, BorderLayout.SOUTH);
        return view;
    }

    private JComponent buildSummaryView() {
        summaryScore.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
        top.setOpaque(false);
        top.add(summaryKind);
        top.add(summaryScore);
        top.add(summaryStats);
        top.add(summaryGrade);

        mistakesText.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JPanel mistakesPanel = new JPanel(new BorderLayout());
        mistakesPanel.setOpaque(false);
        mistakesPanel.add(mistakesTitle, BorderLayout.NORTH);
        mistakesPanel.add(mistakesText, BorderLayout.CENTER);
        mistakesPanel.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));

        RoundedCard card = new RoundedCard();
        card.add(top, BorderLayout.NORTH);
        card.add(mistakesPanel, BorderLayout.CENTER);

        playAgainButton.addActionListener(e -> restart());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(playAgainButton);
        actions.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        JPanel view = new JPanel(new BorderLayout());
        view.add(card, BorderLayout.NORTH);
        view.add(actions, BorderLayout.CENTER);
        return view;
    }

    private JComponent buildEmptyView() {
        RoundedCard card = new RoundedCard();
        card.add(emptyText, BorderLayout.CENTER);
        return card;
    }

    /** Đổi phần thân màn hình (đang chơi / tổng kết / không đủ dữ liệu). */
    private void showView(JComponent view) {
        if (body.getComponentCount() == 1 && body.getComponent(0) == view) {
            return;
        }
        body.removeAll();
        body.add(view, BorderLayout.NORTH);
        body.revalidate();
        body.repaint();
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

    private static JTextArea wrappingText() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setHighlighter(null);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    // ===================== Hiển thị =====================

    private void render() {
        renderStats();
        if (showingSummary) {
            renderSummary();
        } else if (unavailable) {
            emptyText.setText(Messages.get("quiz.empty"));
        } else {
            renderQuestion();
        }
    }

    private void renderStats() {
        int current = answered + (question != null && chosen < 0 && !showingSummary ? 1 : 0);
        questionStat.set(Messages.get("quiz.stat.question"),
                roundLength > 0 ? Math.min(current, roundLength) + "/" + roundLength : String.valueOf(current));
        correctStat.set(Messages.get("quiz.stat.correct"), correct + "/" + answered);
        streakStat.set(Messages.get("quiz.stat.streak"), String.valueOf(streak));
        bestStat.set(Messages.get("quiz.stat.best"), String.valueOf(bestStreak));
    }

    private void renderQuestion() {
        boolean hasQuestion = question != null;
        boolean isAnswered = hasQuestion && chosen >= 0;
        promptText.setText(hasQuestion ? question.prompt() : "");
        for (int i = 0; i < optionButtons.size(); i++) {
            OptionButton button = optionButtons.get(i);
            button.setOption(i, hasQuestion ? question.options().get(i) : "");
            button.setEnabled(hasQuestion);
            button.paintState(!isAnswered ? OptionState.NORMAL
                    : question.isCorrect(i) ? OptionState.CORRECT
                    : i == chosen ? OptionState.WRONG
                    : OptionState.MUTED);
        }

        Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
        if (!isAnswered) {
            feedbackLabel.setText(Messages.get("quiz.hint"));
            feedbackLabel.setForeground(muted);
            detailText.setText("");
        } else {
            if (question.isCorrect(chosen)) {
                feedbackLabel.setText(streak >= 3 ? Messages.format("quiz.feedback.streak", streak)
                        : Messages.get("quiz.feedback.correct"));
                feedbackLabel.setForeground(green());
            } else {
                feedbackLabel.setText(Messages.format("quiz.feedback.wrong", question.correctIndex() + 1));
                feedbackLabel.setForeground(red());
            }
            List<String> meanings = question.answer().getMeanings();
            detailText.setText(meanings.size() < 2 ? ""
                    : Messages.format("quiz.detail", question.answer().getSlang(), meanings.size()) + "\n"
                    + numbered(meanings));
        }
        detailText.setForeground(muted);
        nextButton.setEnabled(isAnswered);
        nextButton.setText(Messages.get(isAnswered && roundFinished() ? "quiz.action.finish" : "quiz.action.next"));
        restartButton.setEnabled(answered > 0);
    }

    private void renderSummary() {
        int percent = answered == 0 ? 0 : Math.round(100f * correct / answered);
        summaryScore.setText(correct + "/" + answered);
        summaryStats.setText(Messages.format("quiz.summary.stats", percent, roundBestStreak));
        String grade = percent >= 90 ? "great" : percent >= 70 ? "good" : percent >= 50 ? "ok" : "low";
        summaryGrade.setText(Messages.get("quiz.summary.grade." + grade));
        summaryGrade.setForeground(percent >= 50 ? green() : red());
        if (mistakes.isEmpty()) {
            mistakesTitle.setText(Messages.get("quiz.summary.perfect"));
            mistakesText.setText("");
        } else {
            mistakesTitle.setText(Messages.get("quiz.summary.mistakes"));
            StringBuilder text = new StringBuilder();
            for (Mistake mistake : mistakes) {
                QuizQuestion q = mistake.question();
                text.append(text.length() == 0 ? "" : "\n").append("• ").append(Messages.format("quiz.summary.mistake",
                        q.prompt(), q.correctOption(), q.options().get(mistake.chosen())));
            }
            mistakesText.setText(text.toString());
            mistakesText.setCaretPosition(0);
        }
    }

    private static String numbered(List<String> meanings) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < meanings.size(); i++) {
            text.append(i == 0 ? "" : "\n").append(i + 1).append(". ").append(meanings.get(i));
        }
        return text.toString();
    }

    private static String roundLengthText(Integer length) {
        return length == null || length == 0 ? Messages.get("quiz.mode.endless")
                : Messages.format("quiz.mode.count", length);
    }

    private void applyTexts() {
        String key = type.name().toLowerCase(Locale.ROOT);
        title.setText(Messages.get(feature.titleKey()));
        subtitle.setText(Messages.get("quiz.subtitle." + key));
        kindLabel.setText(Messages.get("quiz.kind." + key));
        modeLabel.setText(Messages.get("quiz.mode.label"));
        nextButton.setToolTipText(Messages.get("quiz.action.next.tip"));
        restartButton.setText(Messages.get("quiz.action.restart"));
        restartButton.setToolTipText(Messages.get("quiz.action.restart.tip"));
        playAgainButton.setText(Messages.get("quiz.action.playAgain"));
        summaryKind.setText(Messages.get("quiz.summary.kind"));
        modeCombo.repaint();
        render();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null && playView != null) { // updateUI được gọi cả trong constructor của JPanel, lúc field chưa có
            // Đổi theme chỉ cập nhật component đang nằm trong cửa sổ. Các phần thân đang ẩn (ví dụ bảng tổng kết lúc
            // đang chơi) phải tự cập nhật, nếu không lần hiện sau sẽ còn màu của theme cũ.
            for (JComponent view : List.of(playView, summaryView, emptyView)) {
                if (view.getParent() == null) {
                    SwingUtilities.updateComponentTreeUI(view);
                }
            }
            applyStyles();
            render(); // màu xanh/đỏ của đáp án tính theo theme mới
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
        // Đề là tên slang (ngắn) thì chữ rất to; đề là một nghĩa (có thể dài) thì nhỏ hơn
        promptText.setFont(type == QuizType.SLANG_TO_DEFINITION
                ? font.deriveFont(Font.BOLD, font.getSize2D() + 22f)
                : font.deriveFont(Font.BOLD, font.getSize2D() + 7f));
        feedbackLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));
        detailText.setFont(font);
        nextButton.setFont(font.deriveFont(Font.BOLD));
        for (StatChip chip : List.of(questionStat, correctStat, streakStat, bestStat)) {
            chip.applyStyles(font, muted);
        }
        summaryKind.setFont(kindLabel.getFont());
        summaryKind.setForeground(ThemeManager.accentColor());
        summaryScore.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 26f));
        summaryStats.setFont(font.deriveFont(font.getSize2D() + 2f));
        summaryGrade.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));
        mistakesTitle.setFont(font.deriveFont(Font.BOLD));
        mistakesText.setFont(font);
        emptyText.setFont(font.deriveFont(font.getSize2D() + 2f));
        for (OptionButton button : optionButtons) {
            button.applyStyles(font);
        }
    }

    private static Color green() {
        return ThemeManager.color("Actions.Green", new Color(0x2E7D32));
    }

    private static Color red() {
        return ThemeManager.color("Actions.Red", new Color(0xC62828));
    }

    /** Trộn {@code a} vào {@code b} theo tỉ lệ {@code amount} (0 = b, 1 = a). */
    private static Color blend(Color a, Color b, float amount) {
        return new Color(
                Math.round(a.getRed() * amount + b.getRed() * (1 - amount)),
                Math.round(a.getGreen() * amount + b.getGreen() * (1 - amount)),
                Math.round(a.getBlue() * amount + b.getBlue() * (1 - amount)));
    }

    // ===================== Cho test =====================

    QuizQuestion currentQuestion() {
        return question;
    }

    int chosenIndex() {
        return chosen;
    }

    int answeredCount() {
        return answered;
    }

    int correctCount() {
        return correct;
    }

    int streak() {
        return streak;
    }

    int bestStreak() {
        return bestStreak;
    }

    boolean showingSummary() {
        return showingSummary;
    }

    boolean showingUnavailable() {
        return unavailable && body.getComponentCount() == 1 && body.getComponent(0) == emptyView;
    }

    String promptShown() {
        return promptText.getText();
    }

    String optionShown(int index) {
        return optionButtons.get(index).text.getText();
    }

    OptionState optionState(int index) {
        return optionButtons.get(index).state;
    }

    String feedbackShown() {
        return feedbackLabel.getText();
    }

    String detailShown() {
        return detailText.getText();
    }

    String questionStatShown() {
        return questionStat.value.getText();
    }

    String nextButtonText() {
        return nextButton.getText();
    }

    boolean nextButtonEnabled() {
        return nextButton.isEnabled();
    }

    String summaryShown() {
        return summaryScore.getText() + " · " + summaryStats.getText() + " · " + summaryGrade.getText();
    }

    String mistakesShown() {
        return mistakesTitle.getText() + "\n" + mistakesText.getText();
    }

    String modeLabelShown() {
        return modeLabel.getText();
    }

    // ===================== Thành phần con =====================

    /** Trạng thái tô màu của một nút đáp án. */
    enum OptionState {
        /** Chưa trả lời. */
        NORMAL,
        /** Đáp án đúng (tô xanh, dù có chọn hay không). */
        CORRECT,
        /** Đáp án đã chọn mà sai (tô đỏ). */
        WRONG,
        /** Các đáp án sai còn lại (chữ mờ đi). */
        MUTED
    }

    /**
     * Nút đáp án: số thứ tự (phím tắt) bên trái, chữ đáp án tự xuống dòng bên phải. JButton chỉ vẽ được chữ một dòng
     * (hoặc HTML với độ rộng cố định), nên nút chứa một JTextArea làm con. JTextArea có bộ nghe chuột riêng sẽ "nuốt"
     * cú bấm, nên nó trả {@code contains = false}: Swing coi như chuột không nằm trên nó và giao sự kiện cho nút.
     */
    private final class OptionButton extends JButton {

        private final JLabel badge = new JLabel();
        private final JTextArea text = new JTextArea() {
            @Override
            public boolean contains(int x, int y) {
                return false; // để cú bấm và hover rơi xuống nút cha
            }
        };
        private OptionState state = OptionState.NORMAL;

        OptionButton(int index) {
            setLayout(new BorderLayout(14, 0));
            setFocusable(false); // chọn bằng chuột hoặc phím 1–4; Enter không vô tình chọn đáp án đang có focus
            setHorizontalAlignment(SwingConstants.LEFT);
            setMargin(new Insets(10, 14, 10, 14));
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setOpaque(false);
            text.setFocusable(false);
            text.setHighlighter(null);
            text.setBorder(BorderFactory.createEmptyBorder());
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setVerticalAlignment(SwingConstants.TOP);
            add(badge, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            addActionListener(e -> choose(index));
        }

        void setOption(int index, String option) {
            badge.setText(String.valueOf(index + 1));
            text.setText(option);
        }

        void paintState(OptionState newState) {
            state = newState;
            Color normalBg = UIManager.getColor("Button.background"); // UIResource: FlatLaf coi như màu mặc định
            Color foreground = ThemeManager.color("Button.foreground", Color.BLACK);
            Color muted = ThemeManager.color("Label.disabledForeground", Color.GRAY);
            Color base = ThemeManager.color("Panel.background", Color.WHITE);
            float tint = ThemeManager.current() == Theme.DARK ? 0.35f : 0.18f;
            switch (newState) {
                case CORRECT -> {
                    setBackground(blend(green(), base, tint));
                    putClientProperty("JComponent.outline", green());
                    text.setForeground(foreground);
                    badge.setText("✓");
                    badge.setForeground(green());
                }
                case WRONG -> {
                    setBackground(blend(red(), base, tint));
                    putClientProperty("JComponent.outline", red());
                    text.setForeground(foreground);
                    badge.setText("✗");
                    badge.setForeground(red());
                }
                case MUTED -> {
                    setBackground(normalBg);
                    putClientProperty("JComponent.outline", null);
                    text.setForeground(muted);
                    badge.setForeground(muted);
                }
                default -> {
                    setBackground(normalBg);
                    putClientProperty("JComponent.outline", null);
                    text.setForeground(foreground);
                    badge.setForeground(ThemeManager.accentColor());
                }
            }
        }

        void applyStyles(Font font) {
            text.setFont(font.deriveFont(font.getSize2D() + 2f));
            badge.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));
            badge.setPreferredSize(null);
            Dimension size = badge.getPreferredSize();
            // Rộng cố định để chữ của 4 đáp án thẳng hàng dù badge là "1" hay "✓"
            badge.setPreferredSize(new Dimension(Math.max(size.width, font.getSize() * 2), size.height));
        }

        /** Kích thước theo layout (badge + chữ xuống dòng), không theo chữ của chính JButton (rỗng). */
        @Override
        public Dimension getPreferredSize() {
            return getLayout().preferredLayoutSize(this);
        }

        @Override
        public Dimension getMinimumSize() {
            return getLayout().minimumLayoutSize(this);
        }
    }

    /** Một ô thống kê: nhãn nhỏ (CÂU, ĐÚNG, …) ở trên, số lớn ở dưới. */
    private static final class StatChip extends JPanel {

        private final JLabel caption = new JLabel();
        private final JLabel value = new JLabel();

        StatChip() {
            super(new BorderLayout(0, 2));
            add(caption, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        void set(String captionText, String valueText) {
            caption.setText(captionText);
            value.setText(valueText);
        }

        void applyStyles(Font font, Color muted) {
            caption.setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 2f));
            caption.setForeground(muted);
            value.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 6f));
        }
    }
}
