package lab01.slangdict._24127430.model;

import java.util.List;
import java.util.Objects;

/**
 * Một câu hỏi quiz với 4 đáp án.
 *
 * @param type         loại quiz
 * @param prompt       đề bài (slang hoặc definition tuỳ loại)
 * @param options      đúng 4 đáp án, đôi một khác nhau
 * @param correctIndex vị trí đáp án đúng (0..3)
 * @param answer       entry chứa đáp án đúng (để hiển thị đầy đủ sau khi trả lời)
 */
public record QuizQuestion(QuizType type, String prompt, List<String> options, int correctIndex,
                           SlangEntry answer) {

    public static final int OPTION_COUNT = 4;

    public QuizQuestion {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(answer, "answer");
        options = List.copyOf(options);
        if (options.size() != OPTION_COUNT) {
            throw new IllegalArgumentException("A quiz question needs exactly " + OPTION_COUNT + " options");
        }
        if (correctIndex < 0 || correctIndex >= OPTION_COUNT) {
            throw new IllegalArgumentException("correctIndex out of range: " + correctIndex);
        }
    }

    public boolean isCorrect(int chosenIndex) {
        return chosenIndex == correctIndex;
    }

    public String correctOption() {
        return options.get(correctIndex);
    }
}
