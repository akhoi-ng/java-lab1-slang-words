package lab01.slangdict._24127430.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void slangEntryStripsAndValidates() {
        SlangEntry e = new SlangEntry(1, "  LOL ", List.of(" Laugh out loud ", "lots of love"));
        assertEquals("LOL", e.getSlang());
        assertEquals(List.of("Laugh out loud", "lots of love"), e.getMeanings());
        assertEquals("Laugh out loud | lots of love", e.getMeaningText());

        assertThrows(IllegalArgumentException.class, () -> new SlangEntry(1, " ", List.of("x")));
        assertThrows(IllegalArgumentException.class, () -> new SlangEntry(1, "LOL", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SlangEntry(1, "LOL", List.of("ok", "")));
        assertThrows(UnsupportedOperationException.class, () -> e.getMeanings().add("x"));
    }

    @Test
    void entriesAreEqualByIdAndCopiesKeepId() {
        SlangEntry a = new SlangEntry(7, "LOL", List.of("Laugh"));
        SlangEntry renamed = a.withSlang("LMAO");
        assertEquals(a, renamed);
        assertEquals(7, renamed.getId());
        assertEquals("LMAO", renamed.getSlang());
        assertNotEquals(a, new SlangEntry(8, "LOL", List.of("Laugh")));
    }

    @Test
    void searchResultsSortByScoreThenSlangThenId() {
        SearchResult low = new SearchResult(new SlangEntry(1, "aaa", List.of("x")), 1);
        SearchResult highB = new SearchResult(new SlangEntry(2, "B", List.of("x")), 5);
        SearchResult highA2 = new SearchResult(new SlangEntry(4, "a", List.of("x")), 5);
        SearchResult highA1 = new SearchResult(new SlangEntry(3, "A", List.of("x")), 5);

        List<SearchResult> list = new ArrayList<>(List.of(low, highB, highA2, highA1));
        Collections.sort(list);
        assertEquals(List.of(highA1, highA2, highB, low), list);
    }

    @Test
    void quizQuestionNeedsFourOptions() {
        SlangEntry e = new SlangEntry(1, "LOL", List.of("Laugh"));
        QuizQuestion q = new QuizQuestion(QuizType.SLANG_TO_DEFINITION, "LOL", List.of("a", "Laugh", "c", "d"), 1, e);
        assertTrue(q.isCorrect(1));
        assertEquals("Laugh", q.correctOption());
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(QuizType.SLANG_TO_DEFINITION, "LOL", List.of("a", "b"), 0, e));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(QuizType.SLANG_TO_DEFINITION, "LOL", List.of("a", "b", "c", "d"), 4, e));
    }
}
