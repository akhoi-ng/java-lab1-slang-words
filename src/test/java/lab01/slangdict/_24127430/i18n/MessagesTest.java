package lab01.slangdict._24127430.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    /** Dấu ' đứng một mình (không phải '') — MessageFormat sẽ nuốt mất phần chữ phía sau nó. */
    private static final Pattern LONE_QUOTE = Pattern.compile("(?<!')'(?!')");

    @AfterEach
    void restoreDefaultLocale() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    @Test
    void bothLanguagesHaveExactlyTheSameKeys() {
        Set<String> vi = new TreeSet<>(Messages.keys(Messages.VIETNAMESE));
        Set<String> en = new TreeSet<>(Messages.keys(Messages.ENGLISH));
        Set<String> onlyVi = new TreeSet<>(vi);
        onlyVi.removeAll(en);
        Set<String> onlyEn = new TreeSet<>(en);
        onlyEn.removeAll(vi);
        assertTrue(onlyVi.isEmpty() && onlyEn.isEmpty(),
                "keys only in vi: " + onlyVi + ", keys only in en: " + onlyEn);
    }

    @Test
    void noValueIsBlankAndFormatPatternsHaveNoLoneQuote() {
        for (Locale locale : Messages.supportedLocales()) {
            for (String key : Messages.keys(locale)) {
                String value = Messages.raw(locale, key);
                assertFalse(value.isBlank(), locale + " " + key + " is blank");
                if (value.contains("{")) {
                    assertFalse(LONE_QUOTE.matcher(value).find(),
                            locale + " " + key + " has a lone ' (write '' in MessageFormat patterns)");
                }
            }
        }
    }

    @Test
    void vietnameseIsReadAsUtf8() {
        Messages.setLocale(Messages.VIETNAMESE);
        assertEquals("Từ điển Slang", Messages.get("app.title"));
    }

    @Test
    void switchingLocaleChangesTextsAndNotifiesListenersOnce() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        Messages.addListener(listener);
        try {
            Messages.setLocale(Messages.ENGLISH);
            assertEquals("Slang Dictionary", Messages.get("app.title"));
            assertEquals(1, calls.get());

            Messages.setLocale(Messages.ENGLISH); // chọn lại đúng ngôn ngữ đang dùng → không báo
            assertEquals(1, calls.get());

            Messages.setLocale(Messages.VIETNAMESE);
            assertEquals("Từ điển Slang", Messages.get("app.title"));
            assertEquals(2, calls.get());
        } finally {
            Messages.removeListener(listener);
        }
        Messages.setLocale(Messages.ENGLISH);
        assertEquals(2, calls.get(), "removed listener must not be called");
    }

    @Test
    void resolveMapsByLanguageAndFallsBackToDefault() {
        assertEquals(Messages.VIETNAMESE, Messages.resolve(Locale.forLanguageTag("vi-VN")));
        assertEquals(Messages.ENGLISH, Messages.resolve(Locale.US));
        assertEquals(Messages.DEFAULT_LOCALE, Messages.resolve(Locale.JAPANESE));
        assertEquals(Messages.DEFAULT_LOCALE, Messages.resolve(null));
        assertEquals(Messages.VIETNAMESE, Messages.DEFAULT_LOCALE);
    }

    @Test
    void formatUsesLocaleNumberGrouping() {
        Messages.setLocale(Messages.VIETNAMESE);
        assertTrue(Messages.format("status.loaded.dat", 7638, 85).startsWith("7.638 slang"));
        Messages.setLocale(Messages.ENGLISH);
        assertTrue(Messages.format("status.loaded.dat", 7638, 85).startsWith("7,638 slang"));
    }

    @Test
    void missingKeyIsVisibleInsteadOfCrashing() {
        assertEquals("!no.such.key!", Messages.get("no.such.key"));
    }
}
