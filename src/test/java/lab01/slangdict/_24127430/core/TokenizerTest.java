package lab01.slangdict._24127430.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class TokenizerTest {

    @Test
    void splitsOnNonLetterOrDigitAndKeepsOrder() {
        assertEquals(List.of("laugh", "out", "loud"), Tokenizer.tokens("Laugh Out Loud!"));
        assertEquals(List.of("24", "7"), Tokenizer.tokens("24/7"));
        assertEquals(List.of("i", "don", "t", "know"), Tokenizer.tokens("I don't know"));
        assertEquals(List.of("so", "so", "good"), Tokenizer.tokens("so-so, good"), "giữ cả từ lặp");
    }

    @Test
    void dropsEmptyPartsAndKeepsUnicodeLetters() {
        assertEquals(List.of("eller", "noe", "sånt"), Tokenizer.tokens("  ...Eller noe sånt?? "));
        assertEquals(List.of("tiếng", "việt"), Tokenizer.tokens("Tiếng Việt"));
        assertEquals(List.of(), Tokenizer.tokens(":) !!"));
        assertEquals(List.of(), Tokenizer.tokens(""));
        assertEquals(List.of(), Tokenizer.tokens(null));
    }

    @Test
    void lowercasingDoesNotDependOnDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr")); // "I" → "ı" nếu dùng locale mặc định
            assertEquals(List.of("idiot"), Tokenizer.tokens("IDIOT"));
            assertEquals("lol", Tokenizer.normalizeName("LOL"));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void normalizeNameStripsAndLowercasesButKeepsSymbols() {
        assertEquals("lol", Tokenizer.normalizeName("  LoL \t"));
        assertEquals(":|", Tokenizer.normalizeName(":|"));
        assertEquals("$_$", Tokenizer.normalizeName(" $_$ "));
    }
}
