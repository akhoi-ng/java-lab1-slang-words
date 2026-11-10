package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.model.QuizQuestion;
import lab01.slangdict._24127430.model.QuizType;
import lab01.slangdict._24127430.model.SlangEntry;

class QuizServiceTest {

    private static SlangDictionary original;

    @BeforeAll
    static void loadOriginal() throws Exception {
        try (InputStream in = QuizServiceTest.class.getResourceAsStream("/data/slang.txt")) {
            original = SlangDictionary.build(SlangFileParser.parse(in));
        }
    }

    private static SlangDictionary dictionary(String... nameMeaningPairs) {
        List<SlangEntry> entries = new ArrayList<>();
        for (int i = 0; i < nameMeaningPairs.length; i += 2) {
            entries.add(new SlangEntry(i / 2, nameMeaningPairs[i], List.of(nameMeaningPairs[i + 1].split("\\|"))));
        }
        return SlangDictionary.build(entries);
    }

    // ===================== Kiểm tra độc lập (không dùng code của QuizService) =====================

    /** "Laugh out loud!" và "laugh  out loud" là một; nghĩa chỉ có ký hiệu thì so nguyên chuỗi. */
    private static String key(String meaning) {
        String words = meaning.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return words.isEmpty() ? meaning.strip().toLowerCase(Locale.ROOT) : words;
    }

    /** Duyệt tuần tự: tên slang (chữ thường) → key của mọi nghĩa của mọi entry mang tên đó. */
    private static Map<String, Set<String>> meaningsByName(SlangDictionary dictionary) {
        Map<String, Set<String>> map = new HashMap<>();
        for (SlangEntry entry : dictionary.all()) {
            Set<String> keys = map.computeIfAbsent(entry.getSlang().strip().toLowerCase(Locale.ROOT),
                    name -> new HashSet<>());
            entry.getMeanings().forEach(meaning -> keys.add(key(meaning)));
        }
        return map;
    }

    /** Một đáp án có "đúng" theo dữ liệu thật không, tính bằng cách duyệt tuần tự. */
    private static boolean isRightAnswer(Map<String, Set<String>> byName, QuizQuestion question, String option) {
        if (question.type() == QuizType.SLANG_TO_DEFINITION) {
            return byName.getOrDefault(question.prompt().strip().toLowerCase(Locale.ROOT), Set.of())
                    .contains(key(option));
        }
        return byName.getOrDefault(option.strip().toLowerCase(Locale.ROOT), Set.of()).contains(key(question.prompt()));
    }

    /** 4 đáp án đôi một khác nhau, đúng 1 đáp án đúng và nằm ở correctIndex, entry đáp án khớp với đề. */
    private static void assertWellFormed(Map<String, Set<String>> byName, QuizQuestion question) {
        List<String> options = question.options();
        assertEquals(4, options.size());
        Set<String> distinct = new HashSet<>();
        for (String option : options) {
            String k = question.type() == QuizType.SLANG_TO_DEFINITION ? key(option)
                    : option.strip().toLowerCase(Locale.ROOT);
            assertTrue(distinct.add(k), "duplicate option " + option + " in " + question);
        }
        for (int i = 0; i < options.size(); i++) {
            assertEquals(question.isCorrect(i), isRightAnswer(byName, question, options.get(i)),
                    "option " + i + " (" + options.get(i) + ") of " + question);
        }
        SlangEntry answer = question.answer();
        if (question.type() == QuizType.SLANG_TO_DEFINITION) {
            assertEquals(answer.getSlang(), question.prompt());
            assertTrue(answer.getMeanings().contains(question.correctOption()));
        } else {
            assertEquals(answer.getSlang(), question.correctOption());
            assertTrue(answer.getMeanings().contains(question.prompt()));
        }
    }

    // ===================== Trên dữ liệu gốc =====================

    @ParameterizedTest
    @EnumSource(QuizType.class)
    void thousandRandomQuestionsOnTheOriginalDataAreAllUnambiguous(QuizType type) {
        QuizService service = new QuizService(() -> original, new Random(42));
        Map<String, Set<String>> byName = meaningsByName(original);
        int[] correctAt = new int[4];
        Set<Integer> answers = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            QuizQuestion question = service.next(type);
            assertEquals(type, question.type());
            assertWellFormed(byName, question);
            assertTrue(service.isValid(question));
            correctAt[question.correctIndex()]++;
            answers.add(question.answer().getId());
        }
        for (int count : correctAt) { // đáp án đúng không dồn về một vị trí (kỳ vọng ~250)
            assertTrue(count > 180, "correct answer positions: " + java.util.Arrays.toString(correctAt));
        }
        assertTrue(answers.size() > 850,"questions should come from many different slang words: " + answers.size());
    }

    @Test
    void theOriginalDataReallyHasSlangsSharingAMeaning() {
        // Dữ liệu gốc có nhiều slang khác tên cùng một nghĩa, nên test 1.000 câu ở trên thật sự kiểm tra luật
        // "đáp án nhiễu không được có nghĩa của đề". (Tên trùng thì file gốc không có, test trên dữ liệu nhỏ ở dưới.)
        Map<String, Set<String>> namesByMeaning = new HashMap<>();
        for (SlangEntry entry : original.all()) {
            for (String meaning : entry.getMeanings()) {
                namesByMeaning.computeIfAbsent(key(meaning), k -> new HashSet<>())
                        .add(entry.getSlang().toLowerCase(Locale.ROOT));
            }
        }
        long shared = namesByMeaning.values().stream().filter(set -> set.size() > 1).count();
        assertTrue(shared > 100, "meanings shared by several slang words: " + shared);
    }

    // ===================== Luật "không mơ hồ" trên dữ liệu nhỏ =====================

    @Test
    void meaningsOfAnotherEntryWithTheSameNameAreNeverDistractors() {
        // "lol" và "LOL" là hai entry khác nhau cùng tên: hỏi "lol" thì cả 2 nghĩa đều đúng
        SlangDictionary dictionary = dictionary(
                "lol", "Laugh out loud",
                "LOL", "Lots of love",
                "brb", "Be right back",
                "afk", "Away from keyboard",
                "gg", "Good game",
                "tbh", "To be honest");
        QuizService service = new QuizService(() -> dictionary, new Random(1));
        Map<String, Set<String>> byName = meaningsByName(dictionary);
        int lolQuestions = 0;
        for (int i = 0; i < 300; i++) {
            QuizQuestion question = service.next(QuizType.SLANG_TO_DEFINITION);
            assertWellFormed(byName, question);
            if (question.prompt().equalsIgnoreCase("lol")) {
                lolQuestions++;
                long loveOrLaugh = question.options().stream()
                        .filter(o -> o.equals("Laugh out loud") || o.equals("Lots of love")).count();
                assertEquals(1, loveOrLaugh, question.toString());
            }
        }
        assertTrue(lolQuestions > 50, "lol should be asked often enough to be tested: " + lolQuestions);
    }

    @Test
    void slangsSharingTheAskedMeaningAreNeverDistractors() {
        // "lol" và "lul" cùng nghĩa (chỉ khác hoa thường và dấu câu) → hỏi nghĩa đó thì không được có cả hai
        SlangDictionary dictionary = dictionary(
                "lol", "Laughing out loud",
                "lul", "laughing out loud!",
                "brb", "Be right back",
                "afk", "Away from keyboard",
                "gg", "Good game",
                "tbh", "To be honest");
        QuizService service = new QuizService(() -> dictionary, new Random(2));
        Map<String, Set<String>> byName = meaningsByName(dictionary);
        int laughQuestions = 0;
        for (int i = 0; i < 300; i++) {
            QuizQuestion question = service.next(QuizType.DEFINITION_TO_SLANG);
            assertWellFormed(byName, question);
            if (key(question.prompt()).equals("laughing out loud")) {
                laughQuestions++;
                assertFalse(question.options().contains("lol") && question.options().contains("lul"),
                        question.toString());
            }
        }
        assertTrue(laughQuestions > 50, "the shared meaning should be asked often enough: " + laughQuestions);
    }

    @Test
    void duplicateMeaningsAndSameNamesAreNotCountedAsDifferentOptions() {
        // Chỉ có 3 tên khác nhau (lol = LOL) → không thể hỏi nghĩa → slang
        SlangDictionary dictionary = dictionary(
                "lol", "Laugh out loud",
                "LOL", "Lots of love",
                "brb", "Be right back",
                "afk", "Away from keyboard");
        QuizService service = new QuizService(() -> dictionary, new Random(3));
        QuizService.NotEnoughDataException error = assertThrows(QuizService.NotEnoughDataException.class,
                () -> service.next(QuizType.DEFINITION_TO_SLANG));
        assertEquals(QuizType.DEFINITION_TO_SLANG, error.type());
        assertEquals(4, error.dictionarySize());

        // Hỏi slang → nghĩa thì được, nhưng chỉ khi đề là brb/afk (đề lol thì chỉ còn 2 nghĩa của slang khác)
        Map<String, Set<String>> byName = meaningsByName(dictionary);
        for (int i = 0; i < 50; i++) {
            QuizQuestion question = service.next(QuizType.SLANG_TO_DEFINITION);
            assertWellFormed(byName, question);
            assertNotEquals("lol", question.prompt().toLowerCase(Locale.ROOT));
        }

        // 4 nghĩa nhưng 2 nghĩa chỉ khác dấu câu → chỉ có 3 nghĩa khác nhau
        SlangDictionary sameMeanings = dictionary(
                "a", "Good game", "b", "good game!", "c", "Be right back", "d", "Away from keyboard");
        assertThrows(QuizService.NotEnoughDataException.class,
                () -> new QuizService(() -> sameMeanings).next(QuizType.SLANG_TO_DEFINITION));
    }

    @ParameterizedTest
    @EnumSource(QuizType.class)
    void needsAtLeastFourUsableSlangWords(QuizType type) {
        assertThrows(QuizService.NotEnoughDataException.class,
                () -> new QuizService(SlangDictionary::new).next(type));
        SlangDictionary three = dictionary("a", "one", "b", "two", "c", "three");
        assertThrows(QuizService.NotEnoughDataException.class, () -> new QuizService(() -> three).next(type));

        SlangDictionary four = dictionary("a", "one", "b", "two", "c", "three", "d", "four");
        QuizService service = new QuizService(() -> four, new Random(4));
        Map<String, Set<String>> byName = meaningsByName(four);
        for (int i = 0; i < 50; i++) { // vừa đủ: mọi entry khác đều là đáp án nhiễu
            assertWellFormed(byName, service.next(type));
        }
    }

    // ===================== Câu tiếp, từ điển hiện tại, câu hỏi còn hợp lệ không =====================

    @ParameterizedTest
    @EnumSource(QuizType.class)
    void nextQuestionNeverRepeatsThePreviousPrompt(QuizType type) {
        for (SlangDictionary dictionary : List.of(original,
                dictionary("a", "one", "b", "two", "c", "three", "d", "four", "e", "five"))) {
            QuizService service = new QuizService(() -> dictionary, new Random(5));
            QuizQuestion previous = service.next(type);
            for (int i = 0; i < 200; i++) {
                QuizQuestion question = service.next(type, previous.answer().getId());
                assertNotEquals(previous.answer().getId(), question.answer().getId());
                previous = question;
            }
        }
        // Chỉ "x" làm đề được (hỏi "lol" thì 3 nghĩa của lol/LOL/Lol đều đúng, chỉ còn 1 nghĩa sai) → đành hỏi lại
        SlangDictionary onlyOne = dictionary("lol", "one", "LOL", "two", "Lol", "three", "x", "four");
        QuizService service = new QuizService(() -> onlyOne, new Random(6));
        QuizQuestion question = service.next(QuizType.SLANG_TO_DEFINITION);
        assertEquals(question.answer().getId(),
                service.next(QuizType.SLANG_TO_DEFINITION, question.answer().getId()).answer().getId());
    }

    @Test
    void alwaysUsesTheCurrentDictionaryLikeAfterReset() {
        AtomicReference<SlangDictionary> current = new AtomicReference<>(
                dictionary("a", "one", "b", "two", "c", "three", "d", "four"));
        QuizService service = new QuizService(current::get, new Random(7));
        service.next(QuizType.SLANG_TO_DEFINITION);

        current.set(dictionary("w", "alpha", "x", "beta", "y", "gamma", "z", "delta"));
        for (int i = 0; i < 20; i++) {
            QuizQuestion question = service.next(QuizType.SLANG_TO_DEFINITION);
            assertTrue(Set.of("w", "x", "y", "z").contains(question.prompt()), question.prompt());
        }
    }

    @Test
    void isValidNoticesWhenAnEditMakesTheQuestionWrongOrAmbiguous() {
        SlangDictionary dictionary = dictionary(
                "brb", "Be right back", "afk", "Away from keyboard", "gg", "Good game", "tbh", "To be honest",
                "imo", "In my opinion");
        QuizService service = new QuizService(() -> dictionary, new Random(8));

        // Slang → nghĩa: thêm entry trùng tên đề mang một đáp án nhiễu → đáp án đó cũng đúng
        QuizQuestion question = service.next(QuizType.SLANG_TO_DEFINITION);
        dictionary.add("unrelated", List.of("Something else"));
        assertTrue(service.isValid(question), "an unrelated change keeps the question");
        String distractor = question.options().get((question.correctIndex() + 1) % 4);
        SlangEntry twin = dictionary.add(question.prompt().toUpperCase(Locale.ROOT), List.of(distractor + "!"));
        assertFalse(service.isValid(question));
        dictionary.remove(twin.getId());
        assertTrue(service.isValid(question));
        dictionary.remove(question.answer().getId()); // xóa slang của đề → đáp án đúng không còn đúng
        assertFalse(service.isValid(question));

        // Nghĩa → slang: sửa một đáp án nhiễu để nó cũng có nghĩa của đề
        QuizQuestion reverse = service.next(QuizType.DEFINITION_TO_SLANG);
        assertTrue(service.isValid(reverse));
        String wrongName = reverse.options().get((reverse.correctIndex() + 1) % 4);
        SlangEntry wrong = dictionary.get(dictionary.idsByName(wrongName).iterator().next());
        dictionary.replace(wrong.withMeanings(List.of(wrong.getMeanings().get(0), reverse.prompt().toLowerCase())));
        assertFalse(service.isValid(reverse));
    }

    @Test
    void meaningKeyIgnoresCaseAndPunctuationButKeepsSymbolOnlyMeanings() {
        assertEquals(QuizService.meaningKey("laugh out loud"), QuizService.meaningKey("  Laugh, out LOUD!"));
        assertNotEquals(QuizService.meaningKey("laugh out loud"), QuizService.meaningKey("laugh out"));
        assertEquals(":)", QuizService.meaningKey(" :) "));
        assertNotEquals(QuizService.meaningKey(":)"), QuizService.meaningKey(":("));
    }
}
