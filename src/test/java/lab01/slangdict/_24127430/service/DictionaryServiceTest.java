package lab01.slangdict._24127430.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.model.ConflictResolution;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.model.SlangEntry;
import lab01.slangdict._24127430.service.ConflictResolver.Conflict;
import lab01.slangdict._24127430.service.ConflictResolver.Decision;
import lab01.slangdict._24127430.service.ConflictResolver.Operation;
import lab01.slangdict._24127430.service.DictionaryService.Problem;
import lab01.slangdict._24127430.testutil.ScratchDir;

class DictionaryServiceTest {

    /** Không dùng @TempDir: xem {@link ScratchDir}. */
    private Path dir;
    private DictionaryStore store;
    private DictionaryService service;
    private final AtomicInteger changes = new AtomicInteger();
    /** Các lần resolver được hỏi. */
    private final List<Conflict> asked = new ArrayList<>();

    @BeforeEach
    void createService() throws IOException {
        dir = ScratchDir.create("slangdict-dictionary-service");
        store = new DictionaryStore(dir.resolve("dictionary.dat"), dir.resolve("slang.txt"));
        SlangDictionary dictionary = SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "BRB", List.of("Be right back")),
                new SlangEntry(2, "gg", List.of("Good game")),
                new SlangEntry(3, "GG", List.of("Gotta go"))));
        service = new DictionaryService(dictionary, store);
        service.addChangeListener(changes::incrementAndGet);
    }

    @AfterEach
    void closeStore() {
        store.close();
        ScratchDir.deleteQuietly(dir);
    }

    /** Resolver trả lời sẵn: chọn {@code resolution} với entry trùng tên thứ {@code index}. */
    private ConflictResolver choose(ConflictResolution resolution, int index) {
        return conflict -> {
            asked.add(conflict);
            return Optional.of(new Decision(resolution, conflict.existing().get(index)));
        };
    }

    private ConflictResolver choose(ConflictResolution resolution) {
        return choose(resolution, 0);
    }

    private final ConflictResolver cancel = conflict -> {
        asked.add(conflict);
        return Optional.empty();
    };

    private final ConflictResolver mustNotAsk = conflict -> {
        throw new AssertionError("resolver should not be asked, got " + conflict);
    };

    private List<String> meaningsOf(int id) {
        return service.get(id).getMeanings();
    }

    /** Đọc lại dictionary.dat như lần mở app sau. */
    private SlangDictionary reloadFromDisk() throws IOException {
        store.flush();
        return DictionaryStore.load(store.dictionaryFile());
    }

    // ===================== Thêm (F4) =====================

    @Test
    void addNewNameNeedsNoQuestionAndIsSaved() throws IOException {
        ChangeResult result = service.add("  yeet ", List.of(" To throw ", "", "to throw", "Excitement"), mustNotAsk);

        assertTrue(result.isChanged());
        assertNull(result.resolution());
        SlangEntry added = result.entry();
        assertEquals("yeet", added.getSlang());
        assertEquals(List.of("To throw", "Excitement"), added.getMeanings()); // bỏ trống, bỏ trùng hoa thường
        assertEquals(5, service.size());
        assertEquals(1, changes.get());

        SlangDictionary saved = reloadFromDisk();
        assertEquals(5, saved.size());
        assertEquals(added.getMeanings(), saved.get(added.getId()).getMeanings());
    }

    @Test
    void addExistingNameAsksWithEveryEntryOfThatName() {
        service.add("Gg", List.of("Great game"), cancel);

        assertEquals(1, asked.size());
        Conflict conflict = asked.get(0);
        assertEquals(Operation.ADD, conflict.operation());
        assertEquals("Gg", conflict.slang());
        assertEquals(List.of("Great game"), conflict.meanings());
        assertEquals(List.of(2, 3), conflict.existing().stream().map(SlangEntry::getId).toList());
        assertNull(conflict.editing());
    }

    @Test
    void addOverwriteReplacesMeaningsOfTheChosenEntryOnly() throws IOException {
        ChangeResult result = service.add("gG", List.of("Great game"), choose(ConflictResolution.OVERWRITE, 1));

        assertEquals(ConflictResolution.OVERWRITE, result.resolution());
        assertEquals(3, result.entry().getId()); // giữ id của entry đích
        assertEquals("gG", service.get(3).getSlang()); // tên theo cách viết mới
        assertEquals(List.of("Great game"), meaningsOf(3));
        assertEquals(List.of("Good game"), meaningsOf(2)); // entry cùng tên còn lại không bị đụng
        assertEquals(4, service.size());
        assertEquals(List.of("Great game"), reloadFromDisk().get(3).getMeanings());
    }

    @Test
    void addMeaningAppendsOnlyNewMeanings() {
        ChangeResult result = service.add("lol", List.of("LAUGHING OUT LOUD", "Lots of love"),
                choose(ConflictResolution.ADD_MEANING));

        assertTrue(result.isChanged());
        assertEquals(ConflictResolution.ADD_MEANING, result.resolution());
        assertEquals(List.of("Laughing out loud", "Lots of love"), meaningsOf(0));
        assertEquals("LOL", service.get(0).getSlang()); // giữ tên cũ
        assertEquals(1, service.current().idsWithWord("love").size()); // index nghĩa cũng được cập nhật
    }

    @Test
    void addMeaningThatIsAlreadyThereChangesNothing() {
        ChangeResult result = service.add("LOL", List.of("laughing out loud"), choose(ConflictResolution.ADD_MEANING));

        assertEquals(ChangeResult.Status.UNCHANGED, result.status());
        assertEquals(0, result.entry().getId());
        assertEquals(0, changes.get()); // không lưu, không báo listener
    }

    @Test
    void addNewEntryKeepsBothEntriesWithTheSameName() {
        ChangeResult result = service.add("brb", List.of("Bathroom break"), choose(ConflictResolution.NEW_ENTRY));

        assertEquals(ConflictResolution.NEW_ENTRY, result.resolution());
        assertEquals(2, service.entriesNamed("BRB").size());
        assertEquals(List.of("Be right back"), meaningsOf(1));
        assertEquals(List.of("Bathroom break"), result.entry().getMeanings());
        assertEquals(5, service.size());
    }

    @Test
    void cancelledAddChangesNothing() {
        ChangeResult result = service.add("LOL", List.of("Lots of love"), cancel);

        assertEquals(ChangeResult.Status.CANCELLED, result.status());
        assertEquals(4, service.size());
        assertEquals(List.of("Laughing out loud"), meaningsOf(0));
        assertEquals(0, changes.get());
    }

    @Test
    void resolverCannotPickAnEntryThatIsNotAConflict() {
        ConflictResolver wrongTarget = conflict -> Optional.of(
                new Decision(ConflictResolution.OVERWRITE, service.get(1)));

        assertThrows(IllegalArgumentException.class, () -> service.add("lol", List.of("x"), wrongTarget));
        assertEquals(List.of("Be right back"), meaningsOf(1));
    }

    // ===================== Sửa (F5) =====================

    @Test
    void editMeaningsAndCaseWithoutAsking() {
        ChangeResult result = service.edit(0, "lol", List.of("Laughing out loud", "Lots of love"), mustNotAsk);

        assertTrue(result.isChanged());
        assertEquals("lol", service.get(0).getSlang());
        assertEquals(List.of("Laughing out loud", "Lots of love"), meaningsOf(0));
        assertEquals(1, changes.get());
    }

    @Test
    void editOneOfTwoSameNameEntriesWithoutRenamingDoesNotAsk() {
        ChangeResult result = service.edit(2, "gg", List.of("Good game", "Well played"), mustNotAsk);

        assertTrue(result.isChanged());
        assertEquals(List.of("Good game", "Well played"), meaningsOf(2));
    }

    @Test
    void editWithoutChangesIsUnchanged() {
        ChangeResult result = service.edit(1, " BRB ", List.of("Be right back", "be right back"), mustNotAsk);

        assertEquals(ChangeResult.Status.UNCHANGED, result.status());
        assertEquals(0, changes.get());
    }

    @Test
    void renameToFreeNameUpdatesNameIndexes() {
        service.edit(1, "BRB2", List.of("Be right back"), mustNotAsk);

        assertTrue(service.entriesNamed("brb").isEmpty());
        assertEquals(1, service.entriesNamed("brb2").size());
        SearchService search = new SearchService(service::current);
        assertTrue(search.search(SearchTarget.SLANG, SearchMode.EXACT, "brb", 10).isEmpty());
        assertTrue(search.search(SearchTarget.SLANG, SearchMode.FUZZY, "brb", 10).results().stream()
                .anyMatch(r -> r.entry().getId() == 1));
    }

    @Test
    void renameToExistingNameAsksWithTheOtherEntriesOnly() {
        service.edit(2, "LOL", List.of("Good game"), cancel);

        Conflict conflict = asked.get(0);
        assertEquals(Operation.EDIT, conflict.operation());
        assertEquals(List.of(0), conflict.existing().stream().map(SlangEntry::getId).toList());
        assertEquals(2, conflict.editing().getId());
        assertEquals("gg", service.get(2).getSlang()); // hủy → không đổi gì
    }

    @Test
    void renameOverwriteRemovesTheOtherEntry() throws IOException {
        ChangeResult result = service.edit(1, "lol", List.of("Lots of love"), choose(ConflictResolution.OVERWRITE));

        assertEquals(ConflictResolution.OVERWRITE, result.resolution());
        assertNull(service.get(0));
        assertEquals(List.of(0), result.removed().stream().map(SlangEntry::getId).toList());
        assertEquals("lol", service.get(1).getSlang());
        assertEquals(List.of(service.get(1)), service.entriesNamed("LOL"));
        assertEquals(3, service.size());
        assertTrue(service.current().idsWithWord("laughing").isEmpty());

        SlangDictionary saved = reloadFromDisk();
        assertNull(saved.get(0));
        assertEquals(List.of("Lots of love"), saved.get(1).getMeanings());
    }

    @Test
    void renameAddMeaningMergesIntoTheOtherEntryAndRemovesTheEditedOne() {
        ChangeResult result = service.edit(1, "lol", List.of("Lots of love", "laughing out LOUD"),
                choose(ConflictResolution.ADD_MEANING));

        assertEquals(ConflictResolution.ADD_MEANING, result.resolution());
        assertEquals(0, result.entry().getId());
        assertNull(service.get(1));
        assertEquals(List.of(1), result.removed().stream().map(SlangEntry::getId).toList());
        assertEquals("LOL", service.get(0).getSlang()); // giữ tên của entry đích
        assertEquals(List.of("Laughing out loud", "Lots of love"), meaningsOf(0));
        assertTrue(service.current().idsWithWord("back").isEmpty()); // entry #1 đã ra khỏi index nghĩa
        assertEquals(3, service.size());
    }

    @Test
    void renameAddMeaningWithNothingNewStillRemovesTheEditedEntry() {
        ChangeResult result = service.edit(1, "LOL", List.of("Laughing out loud"),
                choose(ConflictResolution.ADD_MEANING));

        assertTrue(result.isChanged());
        assertNull(service.get(1));
        assertEquals(List.of("Laughing out loud"), meaningsOf(0));
    }

    @Test
    void renameNewEntryKeepsBoth() {
        ChangeResult result = service.edit(1, "lol", List.of("Be right back"), choose(ConflictResolution.NEW_ENTRY));

        assertEquals(ConflictResolution.NEW_ENTRY, result.resolution());
        assertEquals(List.of(0, 1), service.entriesNamed("lol").stream().map(SlangEntry::getId).toList());
        assertEquals(4, service.size());
    }

    @Test
    void renameWithSeveralSameNameEntriesActsOnTheChosenOne() {
        service.edit(0, "GG", List.of("Laughing out loud"), choose(ConflictResolution.OVERWRITE, 1));

        assertEquals("gg", service.get(2).getSlang()); // không bị chọn → giữ nguyên
        assertNull(service.get(3));
        assertEquals(List.of(0, 2), service.entriesNamed("gg").stream().map(SlangEntry::getId).toList());
    }

    @Test
    void editUnknownIdFails() {
        assertThrows(NoSuchElementException.class, () -> service.edit(99, "x", List.of("y"), mustNotAsk));
    }

    // ===================== Xóa (F6) =====================

    @Test
    void deleteRemovesEntryFromEveryIndexAndSaves() throws IOException {
        ChangeResult result = service.delete(0);

        assertTrue(result.isChanged());
        assertEquals("LOL", result.entry().getSlang());
        assertNull(service.get(0));
        assertTrue(service.entriesNamed("lol").isEmpty());
        assertTrue(service.current().idsWithWord("laughing").isEmpty());
        assertEquals(1, changes.get());
        assertNull(reloadFromDisk().get(0));
        assertThrows(NoSuchElementException.class, () -> service.delete(0));
    }

    // ===================== Kiểm tra dữ liệu nhập =====================

    @Test
    void validateReportsTheFirstProblem() {
        assertEquals(Optional.of(Problem.NAME_EMPTY), DictionaryService.validate("  ", List.of("x")));
        assertEquals(Optional.of(Problem.NAME_INVALID), DictionaryService.validate("a`b", List.of("x")));
        assertEquals(Optional.of(Problem.NAME_INVALID), DictionaryService.validate("a\nb", List.of("x")));
        assertEquals(Optional.of(Problem.NO_MEANING), DictionaryService.validate("ok", List.of(" ", "")));
        assertEquals(Optional.of(Problem.NO_MEANING), DictionaryService.validate("ok", List.of()));
        assertEquals(Optional.of(Problem.MEANING_INVALID), DictionaryService.validate("ok", List.of("a | b")));
        // ":|" là tên slang có thật trong slang.txt: '|' được phép trong tên
        assertEquals(Optional.empty(), DictionaryService.validate(":|", List.of("Disgust")));
        assertEquals(Optional.empty(), DictionaryService.validate("ok", Arrays.asList(null, "fine")));
    }

    @Test
    void invalidInputIsRejectedWithoutChangingAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.add("", List.of("x"), mustNotAsk));
        assertThrows(IllegalArgumentException.class, () -> service.edit(0, "LOL", List.of("a|b"), mustNotAsk));
        assertEquals(4, service.size());
        assertEquals(0, changes.get());
    }

    @Test
    void countOthersNamedExcludesTheEditedEntry() {
        assertEquals(2, service.countOthersNamed("GG", -1));
        assertEquals(1, service.countOthersNamed("gg", 2));
        assertEquals(0, service.countOthersNamed("nothing", -1));
        assertEquals(0, service.countOthersNamed(" ", -1));
    }

    @Test
    void currentIsTheLiveDictionary() {
        SlangDictionary before = service.current();
        service.add("new", List.of("meaning"), mustNotAsk);
        assertSame(before, service.current());
        assertFalse(service.current().idsByName("new").isEmpty());
    }
}
