package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.core.SlangDictionary;
import lab01.slangdict._24127430.model.SlangEntry;

class DictionaryStoreTest {

    /**
     * Thư mục tạm tự quản thay cho @TempDir: trên Windows, trình diệt virus đôi khi còn giữ file .dat
     * vừa ghi trong giây lát, khiến @TempDir dọn dẹp thất bại và đánh fail cả test (lỗi chập chờn).
     * Ở đây dọn kiểu "cố gắng hết mức": file sót lại trong %TEMP% không ảnh hưởng gì.
     */
    private Path dir;

    @BeforeEach
    void createDir() throws IOException {
        dir = Files.createTempDirectory("slangdict-store-test");
    }

    @AfterEach
    void deleteDir() {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | UncheckedIOException ignored) {
            // bỏ qua: xem chú thích ở field dir
        }
    }

    private static SlangDictionary small() {
        return SlangDictionary.build(List.of(
                new SlangEntry(0, "LOL", List.of("Laughing out loud")),
                new SlangEntry(1, "lol", List.of("Lots of love")),
                new SlangEntry(2, ":|", List.of("Disgust", "not funny"))));
    }

    private static void assertSameContent(SlangDictionary expected, SlangDictionary actual) {
        assertEquals(expected.size(), actual.size());
        for (SlangEntry e : expected.all()) {
            SlangEntry other = actual.get(e.getId());
            assertNotNull(other);
            assertEquals(e.getSlang(), other.getSlang());
            assertEquals(e.getMeanings(), other.getMeanings());
        }
        assertEquals(expected.names(), actual.names());
        assertEquals(expected.vocabulary(), actual.vocabulary());
        assertEquals(expected.nextId(), actual.nextId());
    }

    private static DictionaryStore store(Path dir) {
        return new DictionaryStore(dir.resolve("dictionary.dat"), dir.resolve("slang.txt"));
    }

    // ---------------- save / load ----------------

    @Test
    void saveThenLoadRoundTripsWithHeader() throws Exception {
        Path file = dir.resolve("sub").resolve("dictionary.dat"); // thư mục cha chưa có → tự tạo
        SlangDictionary original = small();
        DictionaryStore.save(original, file);

        ByteBuffer header = ByteBuffer.wrap(Files.readAllBytes(file), 0, 8);
        assertEquals(DictionaryStore.MAGIC, header.getInt());
        assertEquals(DictionaryStore.FORMAT_VERSION, header.getInt());
        assertFalse(Files.exists(dir.resolve("sub").resolve("dictionary.dat.tmp")), "không để lại file tạm");

        SlangDictionary loaded = DictionaryStore.load(file);
        assertSameContent(original, loaded);
        assertEquals(original.idsByName("LOL"), loaded.idsByName("lol"));
        assertEquals(original.idsWithWord("love"), loaded.idsWithWord("love"));
    }

    @Test
    void saveOverwritesExistingFile() throws Exception {
        Path file = dir.resolve("dictionary.dat");
        SlangDictionary d = small();
        DictionaryStore.save(d, file);
        d.add("brb", List.of("Be right back"));
        DictionaryStore.save(d, file);
        assertEquals(4, DictionaryStore.load(file).size());
    }

    @Test
    void invalidFilesThrowFormatException() throws Exception {
        Path file = dir.resolve("dictionary.dat");
        DictionaryStore.save(small(), file);
        byte[] valid = Files.readAllBytes(file);

        // sai magic
        Files.write(file, "hello, not a dictionary".getBytes(StandardCharsets.UTF_8));
        assertThrows(DictionaryFormatException.class, () -> DictionaryStore.load(file));

        // sai phiên bản
        byte[] wrongVersion = valid.clone();
        ByteBuffer.wrap(wrongVersion).putInt(4, DictionaryStore.FORMAT_VERSION + 1);
        Files.write(file, wrongVersion);
        DictionaryFormatException version = assertThrows(DictionaryFormatException.class,
                () -> DictionaryStore.load(file));
        assertTrue(version.getMessage().contains("version"));

        // bị cắt cụt (ví dụ mất điện giữa lúc ghi), kể cả cắt ngay trong header
        for (int length : new int[] {valid.length / 2, valid.length - 1, 12, 4}) {
            Files.write(file, Arrays.copyOf(valid, length));
            assertThrows(DictionaryFormatException.class, () -> DictionaryStore.load(file), "length " + length);
        }

        // đổi 1 bit trong payload: checksum được kiểm tra trước khi deserialize nên báo đúng lý do
        byte[] flipped = valid.clone();
        flipped[valid.length - 2] ^= 1;
        Files.write(file, flipped);
        DictionaryFormatException checksum = assertThrows(DictionaryFormatException.class,
                () -> DictionaryStore.load(file));
        assertTrue(checksum.getMessage().contains("Checksum"));

        // file rỗng
        Files.write(file, new byte[0]);
        assertThrows(DictionaryFormatException.class, () -> DictionaryStore.load(file));

        // file không tồn tại là lỗi IO thường, không phải lỗi định dạng
        IOException missing = assertThrows(IOException.class, () -> DictionaryStore.load(dir.resolve("none.dat")));
        assertInstanceOf(NoSuchFileException.class, missing);
    }

    @Test
    void rejectsUnexpectedObjectsAndForeignClasses() throws Exception {
        // header + checksum đúng nhưng bên trong là object khác
        assertThrows(DictionaryFormatException.class,
                () -> DictionaryStore.deserialize(new ByteArrayInputStream(framed(new ArrayList<>(List.of("x"))))));

        // class ngoài danh sách cho phép bị filter chặn trước khi được khởi tạo
        DictionaryFormatException foreign = assertThrows(DictionaryFormatException.class,
                () -> DictionaryStore.deserialize(new ByteArrayInputStream(framed(new java.text.SimpleDateFormat()))));
        assertInstanceOf(java.io.InvalidClassException.class, foreign.getCause(), "bị filter chặn");
    }

    /** File hợp lệ về header/checksum nhưng chứa object tuỳ ý. */
    private static byte[] framed(Object object) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(payload)) {
            out.writeObject(object);
        }
        return DictionaryStore.frame(payload.toByteArray());
    }

    // ---------------- loadOrBuild ----------------

    @Test
    void firstRunBuildsFromBundledTextThenSecondRunReadsDat() throws Exception {
        try (DictionaryStore store = store(dir)) {
            DictionaryStore.LoadResult first = store.loadOrBuild();
            assertEquals(DictionaryStore.Source.TEXT, first.source());
            assertEquals(7638, first.dictionary().size());
            assertTrue(Files.isRegularFile(dir.resolve("dictionary.dat")), "đã lưu .dat sau khi build");
            assertTrue(store.originalSourceDescription().startsWith("jar:"));
        }
        try (DictionaryStore store = store(dir)) {
            DictionaryStore.LoadResult second = store.loadOrBuild();
            assertEquals(DictionaryStore.Source.DAT, second.source());
            assertEquals(7638, second.dictionary().size());
            assertTrue(second.elapsedNanos() > 0);
        }
    }

    @Test
    void corruptedDatIsRebuiltFromText() throws Exception {
        Path dat = dir.resolve("dictionary.dat");
        Files.write(dat, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
        try (DictionaryStore store = store(dir)) {
            DictionaryStore.LoadResult result = store.loadOrBuild();
            assertEquals(DictionaryStore.Source.TEXT, result.source());
            assertEquals(7638, result.dictionary().size());
        }
        assertEquals(7638, DictionaryStore.load(dat).size(), ".dat hỏng đã được ghi lại bản tốt");
    }

    @Test
    void datKeepsUserEditsAndExternalTextIsPreferredForOriginal() throws Exception {
        Files.writeString(dir.resolve("slang.txt"), "Slag`Meaning\nLOL`Laughing out loud\nBRB`Be right back\n",
                StandardCharsets.UTF_8);
        try (DictionaryStore store = store(dir)) {
            DictionaryStore.LoadResult first = store.loadOrBuild();
            assertEquals(DictionaryStore.Source.TEXT, first.source());
            assertEquals(2, first.dictionary().size(), "dùng data/slang.txt thay vì bản trong jar");
            assertEquals(dir.resolve("slang.txt").toString(), store.originalSourceDescription());

            SlangDictionary d = first.dictionary();
            d.add("AFK", List.of("Away from keyboard"));
            store.saveInBackground(d);
        }
        try (DictionaryStore store = store(dir)) {
            DictionaryStore.LoadResult second = store.loadOrBuild();
            assertEquals(DictionaryStore.Source.DAT, second.source());
            assertEquals(3, second.dictionary().size(), "đọc .dat có chỉnh sửa, không build lại từ text");
            assertEquals(2, store.loadOriginal().size(), "Reset vẫn lấy từ file gốc");
        }
    }

    @Test
    void badOriginalTextReportsLineNumber() throws Exception {
        Files.writeString(dir.resolve("slang.txt"), "Slag`Meaning\nLOL`Laughing\nbroken line\n",
                StandardCharsets.UTF_8);
        try (DictionaryStore store = store(dir)) {
            SlangFormatException e = assertThrows(SlangFormatException.class, store::loadOrBuild);
            assertEquals(3, e.getLineNumber());
        }
    }

    // ---------------- ghi nền ----------------

    @Test
    void burstOfSavesEndsWithLatestState() throws Exception {
        Path dat = dir.resolve("dictionary.dat");
        try (DictionaryStore store = store(dir)) {
            SlangDictionary d = small();
            for (int i = 0; i < 50; i++) {
                d.add("x" + i, List.of("meaning " + i));
                store.saveInBackground(d);
            }
            store.flush();
            assertSameContent(d, DictionaryStore.load(dat));
            assertFalse(Files.exists(dir.resolve("dictionary.dat.tmp")));
        }
    }

    /** Index của file đọc lại phải khớp với chính các entry trong file (không bị chụp lúc đang sửa dở). */
    private static void assertInternallyConsistent(SlangDictionary loaded) {
        SlangDictionary rebuilt = SlangDictionary.build(new ArrayList<>(loaded.all()));
        assertEquals(rebuilt.names(), loaded.names());
        assertEquals(rebuilt.vocabulary(), loaded.vocabulary());
        for (String name : loaded.names()) {
            assertEquals(rebuilt.idsByName(name), loaded.idsByName(name), name);
        }
    }

    @Test
    void savingWhileEditingNeverWritesHalfUpdatedData() throws Exception {
        Path dat = dir.resolve("dictionary.dat");
        List<IOException> errors = new CopyOnWriteArrayList<>();
        try (DictionaryStore store = store(dir)) {
            store.setErrorListener(errors::add);
            SlangDictionary d = store.loadOriginal(); // đủ lớn để serialize mất vài chục ms
            Random random = new Random(5);
            for (int round = 0; round < 15; round++) {
                store.saveInBackground(d);
                for (int i = 0; i < 300; i++) { // sửa liên tục trong lúc luồng nền đang serialize
                    if (random.nextBoolean()) {
                        d.add("race" + random.nextInt(50), List.of("concurrent edit " + i));
                    } else {
                        d.remove(d.randomEntry(random).getId());
                    }
                }
                store.flush();
                assertInternallyConsistent(DictionaryStore.load(dat));
            }
            store.saveInBackground(d);
            store.flush();
            assertSameContent(d, DictionaryStore.load(dat));
        }
        assertEquals(List.of(), errors);
    }

    @Test
    void backgroundSaveFailureGoesToErrorListener() throws Exception {
        Path notADirectory = Files.createFile(dir.resolve("blocker"));
        List<IOException> errors = new CopyOnWriteArrayList<>();
        try (DictionaryStore store = new DictionaryStore(notADirectory.resolve("dictionary.dat"),
                dir.resolve("slang.txt"))) {
            store.setErrorListener(errors::add);
            store.saveInBackground(small());
            store.flush();
        }
        assertEquals(1, errors.size());
    }

    @Test
    void everyDamagedByteIsDetected() throws Exception {
        Path file = dir.resolve("dictionary.dat");
        DictionaryStore.save(small(), file);
        byte[] valid = Files.readAllBytes(file);
        Random random = new Random(11);
        for (int i = 0; i < 2000; i++) {
            byte[] damaged = valid.clone();
            for (int k = 1 + random.nextInt(3); k > 0; k--) { // hỏng 1–3 byte bất kỳ sau magic
                damaged[4 + random.nextInt(valid.length - 4)] = (byte) random.nextInt(256);
            }
            if (Arrays.equals(damaged, valid)) {
                continue; // byte ngẫu nhiên trùng giá trị cũ → không hỏng gì
            }
            assertThrows(DictionaryFormatException.class,
                    () -> DictionaryStore.deserialize(new ByteArrayInputStream(damaged)), "lần " + i);
        }
    }

    @Test
    void delayedSaveWritesOnlyAfterTheDelay() throws Exception {
        Path dat = dir.resolve("dictionary.dat");
        try (DictionaryStore store = new DictionaryStore(dat, dir.resolve("slang.txt"), 400)) {
            SlangDictionary d = small();
            store.saveInBackground(d);
            assertFalse(Files.exists(dat), "chưa tới giờ ghi");
            for (int i = 0; i < 100 && !Files.exists(dat); i++) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(dat), "hết thời gian hoãn thì phải ghi");
            assertSameContent(d, DictionaryStore.load(dat));
        }
    }

    @Test
    void flushWritesAPendingDelayedSaveRightAway() throws Exception {
        Path dat = dir.resolve("dictionary.dat");
        List<IOException> errors = new CopyOnWriteArrayList<>();
        DictionaryStore store = new DictionaryStore(dat, dir.resolve("slang.txt"), 60_000);
        store.setErrorListener(errors::add);
        SlangDictionary d = small();
        store.saveInBackground(d);
        d.add("late", List.of("added after the save request"));
        store.saveInBackground(d);

        long start = System.nanoTime();
        store.close(); // như lúc thoát app: không được chờ đủ 60 s
        assertTrue(System.nanoTime() - start < 10_000_000_000L);
        assertSameContent(d, DictionaryStore.load(dat));
        assertEquals(List.of(), errors);
    }

    @Test
    void saveAfterCloseIsReportedNotThrown() {
        List<IOException> errors = new CopyOnWriteArrayList<>();
        DictionaryStore store = store(dir);
        store.setErrorListener(errors::add);
        store.close();
        store.saveInBackground(small());
        assertEquals(1, errors.size());
        assertFalse(Files.exists(dir.resolve("dictionary.dat")));
    }

    @Test
    void closeIsIdempotent() {
        DictionaryStore store = store(dir);
        store.close();
        store.close();
        store.flush();
    }
}
