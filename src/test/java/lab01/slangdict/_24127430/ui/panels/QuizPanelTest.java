package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.QuizQuestion;
import lab01.slangdict._24127430.model.QuizType;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.QuizService;
import lab01.slangdict._24127430.ui.Feature;
import lab01.slangdict._24127430.ui.panels.QuizPanel.OptionState;

/** Màn hình F9/F10, không mở cửa sổ (panel không hiện nên việc kiểm tra lại câu hỏi diễn ra ở onShown). */
class QuizPanelTest {

    private final AtomicReference<SlangDictionary> dictionary = new AtomicReference<>();
    private QuizPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        Messages.setLocale(Messages.VIETNAMESE);
        List<SlangEntry> entries = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            entries.add(new SlangEntry(i, "slang" + i, List.of("first meaning " + i, "second meaning " + i)));
        }
        dictionary.set(SlangDictionary.build(entries));
        panel = create(QuizType.SLANG_TO_DEFINITION);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    private QuizPanel create(QuizType type) throws Exception {
        return get(() -> new QuizPanel(type, new QuizService(dictionary::get)));
    }

    private void open() throws Exception {
        onEdt(() -> panel.onShown(Feature.QUIZ_SLANG));
    }

    private QuizQuestion question() throws Exception {
        return get(panel::currentQuestion);
    }

    private void answer(boolean right) throws Exception {
        QuizQuestion q = question();
        int index = right ? q.correctIndex() : (q.correctIndex() + 1) % 4;
        onEdt(() -> panel.choose(index));
    }

    // ===================== Một câu hỏi =====================

    @Test
    void opensWithAQuestionAndNothingAnswered() throws Exception {
        open();

        QuizQuestion q = question();
        assertNotNull(q);
        assertEquals(QuizType.SLANG_TO_DEFINITION, q.type());
        assertEquals(q.prompt(), get(panel::promptShown));
        assertTrue(q.prompt().startsWith("slang"));
        for (int i = 0; i < 4; i++) {
            int index = i;
            assertEquals(q.options().get(i), get(() -> panel.optionShown(index)));
            assertEquals(OptionState.NORMAL, get(() -> panel.optionState(index)));
        }
        assertEquals(Messages.get("quiz.hint"), get(panel::feedbackShown));
        assertFalse(get(panel::nextButtonEnabled), "cannot skip before answering");
        assertEquals("1", get(panel::questionStatShown));
    }

    @Test
    void rightAnswerTurnsGreenAndCountsTheStreak() throws Exception {
        open();
        QuizQuestion q = question();

        answer(true);

        for (int i = 0; i < 4; i++) {
            int index = i;
            assertEquals(i == q.correctIndex() ? OptionState.CORRECT : OptionState.MUTED,
                    get(() -> panel.optionState(index)));
        }
        assertEquals(Messages.get("quiz.feedback.correct"), get(panel::feedbackShown));
        assertEquals(1, get(panel::correctCount));
        assertEquals(1, get(panel::streak));
        assertTrue(get(panel::nextButtonEnabled));
        // Các nghĩa của slang được liệt kê sau khi trả lời (slang có 2 nghĩa)
        assertEquals("\"" + q.prompt() + "\" có 2 nghĩa:\n1. " + q.answer().getMeanings().get(0) + "\n2. "
                + q.answer().getMeanings().get(1), get(panel::detailShown));

        onEdt(() -> panel.choose((q.correctIndex() + 1) % 4)); // đã trả lời rồi thì bấm nữa cũng không tính
        assertEquals(1, get(panel::answeredCount));
        assertEquals(q.correctIndex(), get(panel::chosenIndex));
    }

    @Test
    void wrongAnswerShowsTheChosenOneRedAndTheRightOneGreen() throws Exception {
        open();
        QuizQuestion q = question();
        int wrong = (q.correctIndex() + 2) % 4;

        onEdt(() -> panel.choose(wrong));

        assertEquals(OptionState.WRONG, get(() -> panel.optionState(wrong)));
        assertEquals(OptionState.CORRECT, get(() -> panel.optionState(q.correctIndex())));
        assertEquals("✗ Chưa đúng. Đáp án đúng là số " + (q.correctIndex() + 1) + ".", get(panel::feedbackShown));
        assertEquals(0, get(panel::correctCount));
        assertEquals(0, get(panel::streak));
        assertEquals(1, get(panel::answeredCount));
    }

    @Test
    void keys1To4ChooseAndEnterGoesToTheNextQuestion() throws Exception {
        open();
        QuizQuestion first = question();
        int key = KeyEvent.VK_1 + first.correctIndex();

        onEdt(() -> runKey(KeyStroke.getKeyStroke(key, 0)));
        assertEquals(first.correctIndex(), get(panel::chosenIndex));

        onEdt(() -> runKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        QuizQuestion second = question();
        assertNotEquals(first.answer().getId(), second.answer().getId(), "the next question asks another slang");
        assertEquals(-1, get(panel::chosenIndex));
        assertEquals("2", get(panel::questionStatShown));

        int numpad = KeyEvent.VK_NUMPAD1 + (second.correctIndex() + 1) % 4; // phím số bên phải cũng được
        onEdt(() -> runKey(KeyStroke.getKeyStroke(numpad, 0)));
        assertEquals((second.correctIndex() + 1) % 4, get(panel::chosenIndex));
    }

    @Test
    void enterBeforeAnsweringDoesNothing() throws Exception {
        open();
        QuizQuestion q = question();
        onEdt(panel::advance);
        assertSame(q, question());
        assertEquals(0, get(panel::answeredCount));
    }

    // ===================== Điểm, chuỗi, lượt N câu =====================

    @Test
    void streakResetsOnAWrongAnswerButTheBestStreakIsKeptAcrossRestarts() throws Exception {
        open();
        for (int i = 1; i <= 4; i++) {
            answer(true);
            assertEquals(i, get(panel::streak));
            // Từ câu đúng thứ 3 liên tiếp thì khen thêm chuỗi đúng
            assertEquals(i < 3 ? "✓ Chính xác!" : "✓ Chính xác! " + i + " câu đúng liên tiếp.",
                    get(panel::feedbackShown));
            onEdt(panel::advance);
        }
        answer(false);
        assertEquals(0, get(panel::streak));
        assertEquals(4, get(panel::bestStreak));

        onEdt(panel::restart);
        assertEquals(0, get(panel::answeredCount));
        assertEquals(0, get(panel::correctCount));
        assertEquals(4, get(panel::bestStreak));
        assertEquals(-1, get(panel::chosenIndex));
    }

    @Test
    void roundOfTenEndsWithASummaryAndEnterStartsANewRound() throws Exception {
        open();
        answer(true); // câu ở chế độ không giới hạn: đổi chế độ là bắt đầu lượt mới
        onEdt(() -> panel.setRoundLength(10));
        assertEquals(0, get(panel::answeredCount));
        assertEquals("1/10", get(panel::questionStatShown));

        List<QuizQuestion> missed = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean right = i % 2 == 0;
            if (!right) {
                missed.add(question());
            }
            answer(right);
            assertEquals(i < 9 ? Messages.get("quiz.action.next") : Messages.get("quiz.action.finish"),
                    get(panel::nextButtonText));
            onEdt(panel::advance);
        }

        assertTrue(get(panel::showingSummary));
        assertEquals("5/10 · Đúng 50% · chuỗi đúng dài nhất 1 câu · Tạm ổn, luyện thêm chút nữa nhé.",
                get(panel::summaryShown));
        String mistakes = get(panel::mistakesShown);
        assertTrue(mistakes.startsWith("Các câu trả lời sai:\n• "), mistakes);
        assertEquals(5, mistakes.lines().filter(line -> line.startsWith("• ")).count());
        QuizQuestion firstMiss = missed.get(0);
        assertTrue(mistakes.contains(firstMiss.prompt() + " → " + firstMiss.correctOption() + " (bạn chọn: "
                + firstMiss.options().get((firstMiss.correctIndex() + 1) % 4) + ")"), mistakes);

        onEdt(() -> panel.choose(0)); // bảng tổng kết: phím số không làm gì
        assertTrue(get(panel::showingSummary));
        onEdt(panel::advance); // Enter = chơi lượt mới
        assertFalse(get(panel::showingSummary));
        assertEquals(0, get(panel::answeredCount));
        assertEquals("1/10", get(panel::questionStatShown));
    }

    @Test
    void perfectRoundSaysSo() throws Exception {
        open();
        onEdt(() -> panel.setRoundLength(10));
        for (int i = 0; i < 10; i++) {
            answer(true);
            onEdt(panel::advance);
        }
        assertEquals("10/10 · Đúng 100% · chuỗi đúng dài nhất 10 câu · Xuất sắc!", get(panel::summaryShown));
        assertEquals("Không sai câu nào.\n", get(panel::mistakesShown));
    }

    // ===================== Từ điển thay đổi =====================

    @Test
    void anUnansweredQuestionIsReplacedOnlyWhenAnEditBreaksIt() throws Exception {
        open();
        QuizQuestion q = question();

        dictionary.get().add("brand new", List.of("Unrelated meaning"));
        onEdt(panel::dictionaryChanged);
        open();
        assertSame(q, question(), "an unrelated change keeps the question");

        dictionary.get().remove(q.answer().getId()); // slang của đề bị xóa
        onEdt(panel::dictionaryChanged);
        open();
        assertNotEquals(q.answer().getId(), question().answer().getId());
        assertEquals(0, get(panel::answeredCount), "a replaced question is not counted");
    }

    @Test
    void anAnsweredQuestionStaysOnScreenAfterAnEdit() throws Exception {
        open();
        QuizQuestion q = question();
        answer(true);

        dictionary.get().remove(q.answer().getId());
        onEdt(panel::dictionaryChanged);
        open();
        assertSame(q, question());
        assertEquals(q.correctIndex(), get(panel::chosenIndex));
    }

    @Test
    void tooSmallDictionaryShowsAHintUntilEnoughSlangIsAdded() throws Exception {
        dictionary.set(SlangDictionary.build(List.of(
                new SlangEntry(0, "a", List.of("one")),
                new SlangEntry(1, "b", List.of("two")),
                new SlangEntry(2, "c", List.of("three")))));
        open();
        assertNull(question());
        assertTrue(get(panel::showingUnavailable));
        assertFalse(get(panel::nextButtonEnabled));
        onEdt(() -> panel.choose(0)); // không có câu hỏi: bấm phím số không lỗi
        assertEquals(0, get(panel::answeredCount));

        dictionary.get().add("d", List.of("four")); // như thêm slang bằng Ctrl+4
        onEdt(panel::dictionaryChanged);
        open();
        assertNotNull(question());
        assertFalse(get(panel::showingUnavailable));
    }

    @Test
    void usesTheNewDictionaryAfterReset() throws Exception {
        open();
        answer(true);
        List<SlangEntry> fresh = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fresh.add(new SlangEntry(i, "fresh" + i, List.of("fresh meaning " + i)));
        }
        dictionary.set(SlangDictionary.build(fresh));
        onEdt(panel::dictionaryChanged);
        onEdt(panel::advance);
        assertTrue(question().prompt().startsWith("fresh"), question().prompt());
    }

    // ===================== F10, ngôn ngữ =====================

    @Test
    void definitionToSlangAsksAMeaningAndOffersSlangWords() throws Exception {
        panel = create(QuizType.DEFINITION_TO_SLANG);
        onEdt(() -> panel.onShown(Feature.QUIZ_DEFINITION));

        QuizQuestion q = question();
        assertEquals(QuizType.DEFINITION_TO_SLANG, q.type());
        assertTrue(q.prompt().contains("meaning"), q.prompt());
        assertEquals(q.prompt(), get(panel::promptShown));
        for (String option : q.options()) {
            assertTrue(option.startsWith("slang"), option);
        }
        assertEquals(q.answer().getSlang(), q.correctOption());
    }

    @Test
    void switchingLanguageUpdatesTheTexts() throws Exception {
        open();
        onEdt(() -> panel.setRoundLength(20));
        onEdt(() -> Messages.setLocale(Messages.ENGLISH));

        assertEquals("Questions:", get(panel::modeLabelShown));
        assertEquals("Click an answer or press 1–4.", get(panel::feedbackShown));
        assertEquals("1/20", get(panel::questionStatShown));
        answer(false);
        assertTrue(get(panel::feedbackShown).startsWith("✗ Not quite."), get(panel::feedbackShown));
    }

    // ===================== Hỗ trợ =====================

    /** Chạy đúng action mà phím {@code key} được gán trên màn hình (như khi người dùng bấm phím đó). */
    private void runKey(KeyStroke key) {
        Object name = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
        assertNotNull(name, "no binding for " + key);
        panel.getActionMap().get(name).actionPerformed(null);
    }

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
