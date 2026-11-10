package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.model.HistoryItem;
import lab01.slangdict._24127430.model.SearchMode;
import lab01.slangdict._24127430.model.SearchTarget;
import lab01.slangdict._24127430.testutil.ScratchDir;

class HistoryStoreTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 11, 14, 30, 5);

    /** Không dùng @TempDir: xem {@link ScratchDir}. */
    private Path dir;

    @BeforeEach
    void createDir() throws IOException {
        dir = ScratchDir.create("slangdict-history-store");
    }

    @AfterEach
    void deleteDir() {
        ScratchDir.deleteQuietly(dir);
    }

    private static HistoryItem item(SearchTarget target, SearchMode mode, String query, int minute) {
        return new HistoryItem(target, mode, query, T0.plusMinutes(minute));
    }

    private static List<HistoryItem> sample() {
        return List.of(
                item(SearchTarget.DEFINITION, SearchMode.EXACT, "laugh out loud", 3),
                item(SearchTarget.SLANG, SearchMode.FUZZY, "lool", 2),
                item(SearchTarget.SLANG, SearchMode.PREFIX, ":|", 1),
                item(SearchTarget.DEFINITION, SearchMode.SUBSTRING, "sånt", 0)); // UTF-8
    }

    @Test
    void missingFileMeansEmptyHistory() throws IOException {
        assertTrue(new HistoryStore(dir.resolve("history.dat")).load().isEmpty());
    }

    @Test
    void saveThenLoadKeepsEveryFieldAndTheOrder() throws IOException {
        Path file = dir.resolve("sub/history.dat"); // thư mục cha chưa có → tự tạo
        HistoryStore store = new HistoryStore(file);
        store.save(sample());

        assertEquals(sample(), new HistoryStore(file).load());
        assertFalse(Files.exists(file.resolveSibling("history.dat.tmp")), "temp file must be renamed away");
    }

    @Test
    void foreignOrDamagedFilesAreRejected() throws IOException {
        byte[] good = HistoryStore.serialize(sample());

        byte[] wrongMagic = good.clone();
        wrongMagic[0] ^= 0x01;
        byte[] wrongVersion = good.clone();
        wrongVersion[7] ^= 0x01;
        byte[] truncated = Arrays.copyOf(good, good.length / 2);
        byte[] garbage = Arrays.copyOf(good, good.length);
        Arrays.fill(garbage, 8, garbage.length, (byte) 0x5A); // header đúng, payload rác

        for (byte[] bytes : List.of(wrongMagic, wrongVersion, truncated, garbage, new byte[0])) {
            Path file = dir.resolve("history.dat");
            Files.write(file, bytes);
            assertThrows(IOException.class, () -> new HistoryStore(file).load());
        }
    }

    @Test
    void classesOutsideTheAllowListAreNeverInstantiated() throws IOException {
        Path file = dir.resolve("history.dat");
        Files.write(file, framed(new ArrayList<>(List.of(new Evil()))));
        assertThrows(IOException.class, () -> new HistoryStore(file).load());
        assertFalse(Evil.created, "the filter must reject the class before creating it");
    }

    @Test
    void aListOfSomethingElseIsRejected() throws IOException {
        Path file = dir.resolve("history.dat");
        Files.write(file, framed(new ArrayList<>(List.of("not a history item"))));
        assertThrows(IOException.class, () -> new HistoryStore(file).load());
    }

    @Test
    void backgroundSavesEndWithTheLatestSnapshot() throws IOException {
        Path file = dir.resolve("history.dat");
        try (HistoryStore store = new HistoryStore(file)) {
            List<HistoryItem> items = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                items.add(0, item(SearchTarget.SLANG, SearchMode.EXACT, "q" + i, i));
                store.saveInBackground(items); // store tự chụp bản sao, sửa items sau đó không ảnh hưởng
            }
            store.flush();
            assertEquals(items, store.load());
        }
    }

    @Test
    void backgroundErrorsGoToTheListener() throws IOException {
        Path blocked = dir.resolve("history.dat");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("keep"), "x"); // thư mục không rỗng → không thể bị file đè lên
        List<IOException> errors = new CopyOnWriteArrayList<>();
        HistoryStore store = new HistoryStore(blocked);
        store.setErrorListener(errors::add);

        store.saveInBackground(sample());
        store.close();
        assertEquals(1, errors.size());

        store.saveInBackground(sample()); // đã close → báo lỗi, không ném exception
        assertEquals(2, errors.size());
    }

    /** Ghép header hợp lệ trước một object tùy ý, giả lập file do người khác tạo. */
    private static byte[] framed(Object payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(bytes);
        header.writeInt(HistoryStore.MAGIC);
        header.writeInt(HistoryStore.FORMAT_VERSION);
        header.flush();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    /** Class "lạ" (nằm ở package test, ngoài model): filter phải chặn trước khi readObject của nó chạy. */
    static final class Evil implements Serializable {
        private static final long serialVersionUID = 1L;
        static volatile boolean created;

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            created = true;
            in.defaultReadObject();
        }
    }
}
