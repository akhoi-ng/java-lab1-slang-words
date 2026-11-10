package lab01.slangdict._24127430.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.i18n.Messages;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.RandomService;
import lab01.slangdict._24127430.ui.Feature;

/** Màn hình F8, không mở cửa sổ (panel không hiện nên mọi lần tính lại diễn ra ở onShown). */
class RandomPanelTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11); // thứ Tư

    private final AtomicReference<SlangDictionary> dictionary = new AtomicReference<>();
    private final AtomicReference<LocalDate> today = new AtomicReference<>(DAY);
    private final List<String> lookedUp = new ArrayList<>();
    private RandomService service;
    private RandomPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        Messages.setLocale(Messages.VIETNAMESE);
        List<SlangEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(new SlangEntry(i, "slang" + i, List.of("first meaning " + i, "second meaning " + i)));
        }
        dictionary.set(SlangDictionary.build(entries));
        service = new RandomService(dictionary::get);
        onEdt(() -> panel = new RandomPanel(service, lookedUp::add, today::get));
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Messages.DEFAULT_LOCALE);
    }

    private SlangEntry todaysPick() {
        return service.onThisDay(today.get()).orElseThrow();
    }

    private void open() throws Exception {
        onEdt(() -> panel.onShown(Feature.RANDOM));
    }

    @Test
    void opensOnTheSlangOfTheDay() throws Exception {
        open();

        SlangEntry pick = todaysPick();
        assertEquals(pick, get(panel::shownEntry));
        assertEquals(pick.getSlang(), get(panel::slangText));
        assertEquals("1. " + pick.getMeanings().get(0) + "\n2. " + pick.getMeanings().get(1), get(panel::meaningsText));
        assertEquals(Messages.get("random.kind.today"), get(panel::kindText));
        assertTrue(get(panel::subtitleText).startsWith("Thứ Tư, 11/11/2026"), get(panel::subtitleText));
        assertFalse(get(panel::todayButtonEnabled), "already showing today");
    }

    @Test
    void randomShowsAnotherSlangAndTodayComesBack() throws Exception {
        open();
        SlangEntry pick = todaysPick();

        onEdt(panel::showRandom);
        assertNotEquals(pick, get(panel::shownEntry));
        assertFalse(get(panel::showingToday));
        assertEquals(Messages.get("random.kind.random"), get(panel::kindText));
        assertTrue(get(panel::todayButtonEnabled));

        onEdt(panel::showToday);
        assertEquals(pick, get(panel::shownEntry));
        assertTrue(get(panel::showingToday));
    }

    @Test
    void movesToTheNewDayAfterMidnight() throws Exception {
        open();
        today.set(DAY.plusDays(1));

        open();

        assertEquals(todaysPick(), get(panel::shownEntry));
        assertTrue(get(panel::subtitleText).contains("12/11/2026"));
    }

    @Test
    void editedSlangIsShownWithItsNewMeanings() throws Exception {
        open();
        SlangEntry pick = todaysPick();

        onEdt(() -> {
            dictionary.get().replace(pick.withMeanings(List.of("brand new meaning")));
            panel.dictionaryChanged();
        });
        open();

        assertEquals(pick, get(panel::shownEntry));
        assertEquals("1. brand new meaning", get(panel::meaningsText));
    }

    @Test
    void deletedRandomSlangIsReplacedByAnotherRandomOne() throws Exception {
        open();
        onEdt(panel::showRandom);
        SlangEntry shown = get(panel::shownEntry);

        onEdt(() -> {
            dictionary.get().remove(shown.getId());
            panel.dictionaryChanged();
        });
        open();

        assertNotEquals(shown, get(panel::shownEntry));
        assertFalse(get(panel::showingToday), "stays in random mode");
    }

    @Test
    void afterAResetTheSlangOfTheDayComesFromTheNewDictionary() throws Exception {
        open();
        onEdt(() -> {
            dictionary.set(SlangDictionary.build(List.of(new SlangEntry(0, "only", List.of("one")))));
            panel.dictionaryChanged();
        });
        open();

        assertEquals("only", get(panel::slangText));
    }

    @Test
    void lookUpOpensTheShownSlangInSearch() throws Exception {
        open();
        onEdt(panel::lookupShown);
        assertEquals(List.of(todaysPick().getSlang()), lookedUp);
    }

    @Test
    void emptyDictionaryShowsAHint() throws Exception {
        dictionary.set(new SlangDictionary());
        open();

        assertEquals("—", get(panel::slangText));
        assertEquals(Messages.get("random.empty"), get(panel::meaningsText));
        onEdt(panel::lookupShown);
        assertTrue(lookedUp.isEmpty());
    }

    @Test
    void switchingLanguageTranslatesTheDate() throws Exception {
        open();
        onEdt(() -> Messages.setLocale(Messages.ENGLISH));
        assertTrue(get(panel::subtitleText).startsWith("Wednesday, November 11, 2026"), get(panel::subtitleText));
        assertEquals("SLANG OF THE DAY", get(panel::kindText));
    }

    // ===================== Hỗ trợ =====================

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
