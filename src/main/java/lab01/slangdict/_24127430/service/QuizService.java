package lab01.slangdict._24127430.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.model.QuizQuestion;
import lab01.slangdict._24127430.model.QuizType;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * F9, F10: sinh câu hỏi đố vui 4 đáp án, trong đó <b>đúng một</b> đáp án đúng.
 * <p>
 * <b>SLANG_TO_DEFINITION</b> (đề là tên slang S): đáp án đúng là một nghĩa ngẫu nhiên của entry được chọn. 3 đáp án
 * nhiễu là nghĩa của entry khác, nhưng <b>không</b> được là nghĩa của bất kỳ entry nào cũng tên S (có thể có nhiều
 * entry trùng tên, mục 1 của PLAN), nếu không người chơi chọn nó cũng là đúng.
 * <p>
 * <b>DEFINITION_TO_SLANG</b> (đề là một nghĩa D): đáp án đúng là tên slang. 3 đáp án nhiễu là tên slang khác, và
 * <b>không</b> entry nào mang tên đó có nghĩa D (ví dụ nhiều slang cùng nghĩa "Laughing out loud").
 * <p>
 * Hai nghĩa được coi là "giống nhau" khi giống nhau sau khi bỏ hoa thường và dấu câu ({@link #meaningKey}): "Laugh
 * out loud!" và "laugh out loud" hiện ra gần như y hệt, để cả hai làm đáp án sẽ gây khó hiểu. Hai tên slang so sánh
 * không phân biệt hoa thường ({@link Tokenizer#normalizeName}), đúng như khi tra.
 * <p>
 * <b>Cách chọn đáp án nhiễu:</b> bốc ngẫu nhiên entry trong O(1) ({@link SlangDictionary#randomEntry}), bỏ qua entry
 * không hợp lệ. Với 7.638 slang thường chỉ cần 3–4 lần bốc. Nếu bốc {@value #RANDOM_DRAWS} lần vẫn chưa đủ (từ điển
 * rất nhỏ hoặc gần như toàn slang trùng tên) thì duyệt hết từ điển, nên kết luận "không đủ dữ liệu" luôn chính xác.
 * <p>
 * Lấy từ điển qua {@code Supplier} (truyền {@code dictionaryService::current}) để sau Reset tự dùng bản mới. Gọi trên
 * EDT, cùng luồng với mọi thao tác sửa từ điển.
 */
public class QuizService {

    /** Số lần bốc ngẫu nhiên đáp án nhiễu trước khi chuyển sang duyệt hết từ điển. */
    static final int RANDOM_DRAWS = 64;
    /** Số entry tối đa thử làm đề trước khi kết luận không tạo được câu hỏi. */
    static final int PROMPT_TRIES = 20;

    private static final int DISTRACTORS = QuizQuestion.OPTION_COUNT - 1;

    /** Từ điển không đủ slang / nghĩa khác nhau để tạo câu hỏi 4 đáp án không mơ hồ. */
    public static class NotEnoughDataException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final QuizType type;
        private final int dictionarySize;

        public NotEnoughDataException(QuizType type, int dictionarySize) {
            super("Cannot build a " + type + " question with 4 unambiguous options from " + dictionarySize
                    + " slang words");
            this.type = type;
            this.dictionarySize = dictionarySize;
        }

        public QuizType type() {
            return type;
        }

        public int dictionarySize() {
            return dictionarySize;
        }
    }

    private final Supplier<SlangDictionary> dictionary;
    private final Random random;

    public QuizService(Supplier<SlangDictionary> dictionary) {
        this(dictionary, new Random());
    }

    /** Cho test: truyền {@code Random} có seed để kết quả lặp lại được. */
    QuizService(Supplier<SlangDictionary> dictionary, Random random) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Một câu hỏi mới.
     *
     * @throws NotEnoughDataException nếu từ điển không đủ dữ liệu cho loại quiz này
     */
    public QuizQuestion next(QuizType type) {
        return next(type, -1);
    }

    /**
     * Một câu hỏi mới mà đề không lấy từ entry {@code avoidId} (đề của câu vừa hỏi), để "Câu tiếp" không ra lại
     * đúng câu cũ. Chỉ dùng lại entry đó khi không còn cách nào khác.
     *
     * @throws NotEnoughDataException nếu từ điển không đủ dữ liệu cho loại quiz này
     */
    public QuizQuestion next(QuizType type, int avoidId) {
        Objects.requireNonNull(type, "type");
        SlangDictionary dict = dictionary.get();
        for (SlangEntry entry : promptCandidates(dict, avoidId)) {
            QuizQuestion question = type == QuizType.SLANG_TO_DEFINITION
                    ? slangToDefinition(dict, entry)
                    : definitionToSlang(dict, entry);
            if (question != null) {
                return question;
            }
        }
        throw new NotEnoughDataException(type, dict.size());
    }

    /**
     * Câu hỏi còn đúng với từ điển hiện tại không (sau khi người dùng thêm/sửa/xóa/Reset): đáp án đúng vẫn đúng,
     * và không đáp án nhiễu nào trở thành đúng. Màn hình quiz dùng để quyết định có phải đổi câu đang hỏi không.
     */
    public boolean isValid(QuizQuestion question) {
        SlangDictionary dict = dictionary.get();
        List<String> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            boolean correct = question.type() == QuizType.SLANG_TO_DEFINITION
                    ? meaningKeysNamed(dict, question.prompt()).contains(meaningKey(options.get(i)))
                    : hasMeaning(dict, options.get(i), meaningKey(question.prompt()));
            if (correct != question.isCorrect(i)) {
                return false;
            }
        }
        return true;
    }

    // ===================== Hai loại câu hỏi =====================

    /** Đề: tên slang của {@code entry}. Null nếu không đủ đáp án nhiễu hợp lệ. */
    private QuizQuestion slangToDefinition(SlangDictionary dict, SlangEntry entry) {
        String correct = pick(entry.getMeanings());
        // Mọi nghĩa của mọi entry cùng tên đều là đáp án đúng → không được làm đáp án nhiễu
        Set<String> used = meaningKeysNamed(dict, entry.getSlang());
        used.add(meaningKey(correct));
        List<String> distractors = new ArrayList<>(DISTRACTORS);

        for (int draw = 0; draw < RANDOM_DRAWS && distractors.size() < DISTRACTORS; draw++) {
            String meaning = pick(dict.randomEntry(random).getMeanings());
            if (used.add(meaningKey(meaning))) {
                distractors.add(meaning);
            }
        }
        if (distractors.size() < DISTRACTORS) { // hiếm: duyệt hết để chắc chắn
            scan:
            for (SlangEntry other : dict.all()) {
                for (String meaning : other.getMeanings()) {
                    if (used.add(meaningKey(meaning))) {
                        distractors.add(meaning);
                        if (distractors.size() == DISTRACTORS) {
                            break scan;
                        }
                    }
                }
            }
        }
        return build(QuizType.SLANG_TO_DEFINITION, entry.getSlang(), correct, distractors, entry);
    }

    /** Đề: một nghĩa của {@code entry}. Null nếu không đủ đáp án nhiễu hợp lệ. */
    private QuizQuestion definitionToSlang(SlangDictionary dict, SlangEntry entry) {
        String prompt = pick(entry.getMeanings());
        String promptKey = meaningKey(prompt);
        Set<String> seenNames = new HashSet<>(); // tên đã xét (nhận hoặc loại), không xét lại
        seenNames.add(Tokenizer.normalizeName(entry.getSlang()));
        List<String> distractors = new ArrayList<>(DISTRACTORS);

        for (int draw = 0; draw < RANDOM_DRAWS && distractors.size() < DISTRACTORS; draw++) {
            SlangEntry other = dict.randomEntry(random);
            if (isNewWrongName(dict, other.getSlang(), promptKey, seenNames)) {
                distractors.add(other.getSlang());
            }
        }
        if (distractors.size() < DISTRACTORS) { // hiếm: duyệt hết để chắc chắn
            for (SlangEntry other : dict.all()) {
                if (isNewWrongName(dict, other.getSlang(), promptKey, seenNames)) {
                    distractors.add(other.getSlang());
                    if (distractors.size() == DISTRACTORS) {
                        break;
                    }
                }
            }
        }
        return build(QuizType.DEFINITION_TO_SLANG, prompt, entry.getSlang(), distractors, entry);
    }

    /** Ghép đáp án đúng với 3 đáp án nhiễu rồi xáo vị trí. Null nếu chưa đủ 3 đáp án nhiễu. */
    private QuizQuestion build(QuizType type, String prompt, String correct, List<String> distractors,
                               SlangEntry answer) {
        if (distractors.size() < DISTRACTORS) {
            return null;
        }
        List<String> options = new ArrayList<>(QuizQuestion.OPTION_COUNT);
        options.add(correct);
        options.addAll(distractors);
        Collections.shuffle(options, random);
        // Các đáp án đôi một khác nhau nên indexOf tìm đúng vị trí của đáp án đúng
        return new QuizQuestion(type, prompt, options, options.indexOf(correct), answer);
    }

    // ===================== Hỗ trợ =====================

    /**
     * Các entry thử làm đề, theo thứ tự ngẫu nhiên, entry {@code avoidId} xếp cuối. Từ điển nhỏ thì thử hết (để
     * kết luận "không đủ dữ liệu" là chắc chắn), từ điển lớn thì thử tối đa {@value #PROMPT_TRIES} entry.
     */
    private List<SlangEntry> promptCandidates(SlangDictionary dict, int avoidId) {
        List<SlangEntry> candidates;
        if (dict.size() <= PROMPT_TRIES) {
            candidates = new ArrayList<>(dict.all());
            Collections.shuffle(candidates, random);
        } else {
            Set<SlangEntry> drawn = new LinkedHashSet<>();
            while (drawn.size() < PROMPT_TRIES) {
                drawn.add(dict.randomEntry(random));
            }
            candidates = new ArrayList<>(drawn);
        }
        SlangEntry avoided = dict.get(avoidId);
        if (avoided != null && candidates.remove(avoided) && dict.size() <= PROMPT_TRIES) {
            candidates.add(avoided); // từ điển nhỏ: vẫn thử entry đó, nhưng sau cùng
        }
        return candidates;
    }

    /** Key so sánh của mọi nghĩa thuộc các entry có tên {@code slang} (không phân biệt hoa thường). */
    private static Set<String> meaningKeysNamed(SlangDictionary dict, String slang) {
        Set<String> keys = new HashSet<>();
        for (int id : dict.idsByName(slang)) {
            for (String meaning : dict.get(id).getMeanings()) {
                keys.add(meaningKey(meaning));
            }
        }
        return keys;
    }

    /** Tên {@code slang} chưa xét lần nào và không entry nào mang tên đó có nghĩa của đề → dùng làm đáp án nhiễu. */
    private static boolean isNewWrongName(SlangDictionary dict, String slang, String promptKey, Set<String> seenNames) {
        return seenNames.add(Tokenizer.normalizeName(slang)) && !hasMeaning(dict, slang, promptKey);
    }

    /** Có entry nào tên {@code slang} mang nghĩa có key {@code meaningKey} không. */
    private static boolean hasMeaning(SlangDictionary dict, String slang, String meaningKey) {
        for (int id : dict.idsByName(slang)) {
            for (String meaning : dict.get(id).getMeanings()) {
                if (meaningKey(meaning).equals(meaningKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Key để so hai nghĩa: dãy từ chữ thường, bỏ dấu câu ("Laugh out loud!" → "laugh out loud"). Nghĩa chỉ gồm ký hiệu
     * (không có từ nào) thì dùng chính nó viết thường.
     */
    static String meaningKey(String meaning) {
        List<String> tokens = Tokenizer.tokens(meaning);
        return tokens.isEmpty() ? meaning.strip().toLowerCase(Locale.ROOT) : String.join(" ", tokens);
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }
}
