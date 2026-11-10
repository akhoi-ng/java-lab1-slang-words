package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.model.SlangEntry;

class RandomServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    private static SlangDictionary original() throws Exception {
        try (InputStream in = RandomServiceTest.class.getResourceAsStream("/data/slang.txt")) {
            return SlangDictionary.build(SlangFileParser.parse(in));
        }
    }

    /** Từ điển nhỏ có {@code size} entry, id 0 … size-1. */
    private static SlangDictionary small(int size) {
        List<SlangEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new SlangEntry(i, "s" + i, List.of("meaning " + i)));
        }
        return SlangDictionary.build(entries);
    }

    private static int idOn(RandomService service, LocalDate date) {
        return service.onThisDay(date).orElseThrow().getId();
    }

    // ===================== Slang của ngày =====================

    @Test
    void sameDayAlwaysGivesTheSameEntryEvenAfterRestartingTheApp() throws Exception {
        SlangDictionary dictionary = original();
        int first = idOn(new RandomService(() -> dictionary), DAY);

        RandomService service = new RandomService(() -> dictionary);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, idOn(service, DAY));
        }
        // "Mở lại app": từ điển được build lại từ file, thứ tự duyệt HashMap có thể khác
        assertEquals(first, idOn(new RandomService(() -> {
            try {
                return original();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }), DAY));
    }

    @Test
    void differentDaysGiveDifferentEntries() throws Exception {
        SlangDictionary dictionary = original();
        RandomService service = new RandomService(() -> dictionary);
        Set<Integer> picks = new HashSet<>();
        for (int day = 0; day < 60; day++) {
            picks.add(idOn(service, DAY.plusDays(day)));
        }
        // 60 ngày trên 7.638 slang: trùng nhau gần như không xảy ra
        assertTrue(picks.size() >= 55, "only " + picks.size() + " different picks in 60 days");
    }

    @Test
    void everyEntryHasTheSameChanceToBeTheSlangOfTheDay() {
        SlangDictionary dictionary = small(10);
        RandomService service = new RandomService(() -> dictionary);
        Map<Integer, Integer> counts = new HashMap<>();
        int days = 5000;
        for (int day = 0; day < days; day++) {
            counts.merge(idOn(service, DAY.plusDays(day)), 1, Integer::sum);
        }
        assertEquals(10, counts.size());
        // Kỳ vọng 500 lần mỗi entry, độ lệch chuẩn ~21: khoảng 380–620 là rất rộng
        counts.forEach((id, count) -> assertTrue(count > 380 && count < 620, "#" + id + " picked " + count + " times"));
    }

    @Test
    void addingASlangChangesTheDailyPickOnlyWhenTheNewSlangWins() throws Exception {
        SlangDictionary dictionary = original();
        RandomService service = new RandomService(() -> dictionary);
        int days = 365;
        int[] before = new int[days];
        for (int d = 0; d < days; d++) {
            before[d] = idOn(service, DAY.plusDays(d));
        }

        int added = dictionary.add("yeet", List.of("To throw")).getId();

        int changed = 0;
        for (int d = 0; d < days; d++) {
            int after = idOn(service, DAY.plusDays(d));
            if (after != before[d]) {
                assertEquals(added, after, "a day may only switch to the new slang");
                changed++;
            }
        }
        // Kỳ vọng 365 / 7.639 ≈ 0,05 ngày bị đổi. (Cách new Random(ngày).nextInt(size) sẽ đổi gần như mọi ngày.)
        assertTrue(changed <= 3, changed + " days changed");
    }

    @Test
    void editingTheSlangOfTheDayKeepsItAndShowsTheNewMeanings() throws Exception {
        SlangDictionary dictionary = original();
        RandomService service = new RandomService(() -> dictionary);
        SlangEntry pick = service.onThisDay(DAY).orElseThrow();

        dictionary.replace(new SlangEntry(pick.getId(), pick.getSlang() + "!", List.of("edited meaning")));

        SlangEntry again = service.onThisDay(DAY).orElseThrow();
        assertEquals(pick.getId(), again.getId());
        assertEquals(List.of("edited meaning"), again.getMeanings());
    }

    @Test
    void deletingOnlyChangesTheDayWhoseSlangWasDeleted() throws Exception {
        SlangDictionary dictionary = original();
        RandomService service = new RandomService(() -> dictionary);
        int pick = idOn(service, DAY);
        int tomorrow = idOn(service, DAY.plusDays(1));
        assertNotEquals(pick, tomorrow); // ngày cố định trong test: hai ngày liên tiếp khác nhau

        dictionary.remove(pick);

        assertNotEquals(pick, idOn(service, DAY));
        assertEquals(tomorrow, idOn(service, DAY.plusDays(1)));
    }

    @Test
    void emptyDictionaryGivesNothing() {
        RandomService service = new RandomService(SlangDictionary::new);
        assertEquals(Optional.empty(), service.onThisDay(DAY));
        assertEquals(Optional.empty(), service.random());
        assertEquals(Optional.empty(), service.random(3));
    }

    @Test
    void scoreDependsOnlyOnDayAndId() {
        assertEquals(RandomService.score(20_000, 42), RandomService.score(20_000, 42));
        assertNotEquals(RandomService.score(20_000, 42), RandomService.score(20_001, 42));
        assertNotEquals(RandomService.score(20_000, 42), RandomService.score(20_000, 43));
    }

    // ===================== Random khác =====================

    @Test
    void randomNeverRepeatsTheSlangOnScreen() {
        SlangDictionary dictionary = small(3);
        RandomService service = new RandomService(() -> dictionary, new Random(7));
        int current = 0;
        for (int i = 0; i < 1000; i++) {
            int next = service.random(current).orElseThrow().getId();
            assertNotEquals(current, next);
            current = next;
        }
    }

    @Test
    void randomReachesEveryEntry() {
        SlangDictionary dictionary = small(20);
        RandomService service = new RandomService(() -> dictionary, new Random(1));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(service.random().orElseThrow().getId());
        }
        assertEquals(20, seen.size());
    }

    @Test
    void randomWithASingleEntryStillReturnsIt() {
        SlangDictionary dictionary = small(1);
        RandomService service = new RandomService(() -> dictionary);
        assertEquals(0, service.random(0).orElseThrow().getId());
    }

    // ===================== Từ điển hiện tại =====================

    @Test
    void alwaysUsesTheCurrentDictionaryLikeAfterAReset() {
        AtomicReference<SlangDictionary> current = new AtomicReference<>(small(5));
        RandomService service = new RandomService(current::get, new Random(3));
        SlangEntry shown = service.onThisDay(DAY).orElseThrow();

        current.set(SlangDictionary.build(List.of(new SlangEntry(100, "only", List.of("one")))));

        assertEquals(100, idOn(service, DAY));
        assertEquals(100, service.random().orElseThrow().getId());
        assertEquals(Optional.empty(), service.latest(shown), "the entry is not in the new dictionary");
    }

    @Test
    void latestReturnsTheEditedVersion() {
        SlangDictionary dictionary = small(5);
        RandomService service = new RandomService(() -> dictionary);
        SlangEntry shown = dictionary.get(2);
        dictionary.replace(shown.withMeanings(List.of("new")));

        assertEquals(List.of("new"), service.latest(shown).orElseThrow().getMeanings());
        dictionary.remove(2);
        assertEquals(Optional.empty(), service.latest(shown));
    }
}
