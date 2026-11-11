package lab01.slangdict._24127430.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.CRC32;

import lab01.slangdict._24127430.core.SlangDictionary;

/**
 * Đọc/ghi {@link SlangDictionary} đã index xuống data/dictionary.dat để lần chạy sau không phải index lại.
 *
 * <pre>
 * Định dạng file:  [int MAGIC "SLNG"] [int FORMAT_VERSION] [long CRC32 của payload] [payload]
 *                  payload = SlangDictionary qua ObjectOutputStream
 * </pre>
 * Header giúp phân biệt nhanh "không phải file của mình / phiên bản cũ" với lỗi đọc thông thường.
 * CRC32 phát hiện file bị hỏng âm thầm (đổi vài byte nhưng vẫn deserialize được, ví dụ một id trong index
 * bị sai) — nếu không có nó, từ điển hỏng sẽ được nạp vào và gây lỗi khó hiểu về sau.
 * <p>
 * <b>Ghi an toàn:</b> ghi ra file .tmp rồi đổi tên đè lên file thật. Nếu app bị tắt giữa chừng thì
 * chỉ hỏng file .tmp, file .dat cũ vẫn nguyên.
 * <p>
 * <b>Ghi nền:</b> {@link #saveInBackground} trả về ngay; một luồng nền duy nhất vừa serialize vừa ghi đĩa.
 * Serialize 100.000 slang tốn khoảng 2 giây nên không được làm trên EDT (UI sẽ đứng). Bản ghi vẫn nhất quán
 * vì SlangDictionary serialize trong khoá {@code synchronized}, cùng khoá với add/replace/remove.
 * Nhiều lần lưu dồn dập thì chỉ ghi một lần, với trạng thái mới nhất. App dùng bản có hoãn
 * ({@link #DEFAULT_SAVE_DELAY_MS}) để việc giữ khoá khi serialize không rơi đúng lúc tìm lại sau khi sửa.
 */
public class DictionaryStore implements AutoCloseable {

    /** "SLNG" theo mã ASCII. */
    public static final int MAGIC = 0x534C4E47;
    /** Tăng số này khi đổi cấu trúc dữ liệu để file cũ bị coi là không hợp lệ và build lại. */
    public static final int FORMAT_VERSION = 1;
    /** magic (4) + phiên bản (4) + CRC32 (8). */
    static final int HEADER_BYTES = 16;
    /** slang.txt gốc nhúng trong jar (Maven copy từ gốc repo khi build). */
    public static final String BUNDLED_ORIGINAL = "/data/slang.txt";

    /**
     * Chỉ cho phép đọc lại đúng các class mà từ điển dùng. File .dat lạ chứa class khác sẽ bị từ chối
     * thay vì được khởi tạo (phòng lỗi bảo mật kinh điển của Java deserialization).
     * maxarray chặn file hỏng khai báo collection khổng lồ làm tràn bộ nhớ (100k slang chỉ cần cỡ 10^5–10^6).
     */
    private static final ObjectInputFilter CLASS_FILTER = ObjectInputFilter.Config.createFilter(
            "maxarray=20000000;lab01.slangdict._24127430.**;java.util.*;java.lang.*;!*");

    private static final long FLUSH_TIMEOUT_SECONDS = 30;
    /**
     * Serialize cây (BK-tree; Trie trong file do bản cũ ghi) là đệ quy theo độ sâu: luồng ghi và luồng đọc
     * đều dùng stack rộng hơn mặc định, để file nào ghi được thì cũng đọc lại được.
     */
    private static final long LARGE_STACK_BYTES = 16L * 1024 * 1024;
    private static final int MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_DELAY_MS = 50;

    /** Nguồn dữ liệu đã dùng khi khởi động. */
    public enum Source {
        /** Đọc từ dictionary.dat đã index sẵn. */
        DAT,
        /** Build từ slang.txt (lần đầu chạy, hoặc .dat hỏng: xem {@link LoadResult#recovery()}). */
        TEXT
    }

    /**
     * Kết quả load khi khởi động.
     *
     * @param recovery khác null khi dictionary.dat có nhưng hỏng: file cũ đã được đổi tên để giữ lại và từ điển
     *                 được build lại từ slang.txt. UI phải báo cho người dùng (các thay đổi trước đó không còn).
     */
    public record LoadResult(SlangDictionary dictionary, Source source, long elapsedNanos, Recovery recovery) {
        public long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        }
    }

    /**
     * dictionary.dat không đọc được nên đã bị build lại.
     *
     * @param backupFile file .dat cũ, đã đổi tên (ví dụ dictionary.dat.bad) chứ không bị ghi đè
     * @param cause      lý do không đọc được
     */
    public record Recovery(Path backupFile, DictionaryFormatException cause) {
    }

    /** Hậu tố của file .dat hỏng được giữ lại: dictionary.dat.bad, dictionary.dat.bad2, ... */
    static final String BACKUP_SUFFIX = ".bad";
    private static final int MAX_BACKUPS = 1000;

    /**
     * App chờ chừng này sau lần sửa cuối rồi mới ghi nền. Lúc serialize, luồng ghi giữ khóa của từ điển (100k slang:
     * khoảng 2 s); nếu ghi ngay thì lần tìm lại ngay sau khi thêm/sửa/xóa phải đứng chờ khóa. Hoãn lại thì lần tìm đó
     * xong trước, việc ghi rơi vào lúc người dùng đang đọc kết quả. Thoát app vẫn ghi ngay ({@link #flush}).
     */
    public static final long DEFAULT_SAVE_DELAY_MS = 1500;

    private final Path dictionaryFile;
    private final Path externalOriginalFile;
    private final long saveDelayMillis;

    private final ScheduledThreadPoolExecutor writer = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(null, runnable, "dictionary-writer", LARGE_STACK_BYTES);
        thread.setDaemon(true); // không giữ JVM sống; lúc thoát app gọi flush() để chờ ghi xong
        return thread;
    });
    private final AtomicLong latestSave = new AtomicLong();
    /** Từ điển đang chờ được ghi (null nếu không có gì chờ). */
    private final AtomicReference<SlangDictionary> pending = new AtomicReference<>();
    private volatile Consumer<IOException> errorListener = DictionaryStore::logError;

    /**
     * Store ghi nền ngay khi được yêu cầu (không hoãn).
     *
     * @param dictionaryFile       file .dat để đọc/ghi
     * @param externalOriginalFile slang.txt đặt cạnh jar; nếu không tồn tại thì dùng bản nhúng trong jar
     */
    public DictionaryStore(Path dictionaryFile, Path externalOriginalFile) {
        this(dictionaryFile, externalOriginalFile, 0);
    }

    /** @param saveDelayMillis {@link #saveInBackground} chờ chừng này (tính từ lần yêu cầu cuối) rồi mới ghi */
    public DictionaryStore(Path dictionaryFile, Path externalOriginalFile, long saveDelayMillis) {
        this.dictionaryFile = Objects.requireNonNull(dictionaryFile, "dictionaryFile");
        this.externalOriginalFile = Objects.requireNonNull(externalOriginalFile, "externalOriginalFile");
        if (saveDelayMillis < 0) {
            throw new IllegalArgumentException("saveDelayMillis must be >= 0");
        }
        this.saveDelayMillis = saveDelayMillis;
        // close() đã flush rồi: các lần ghi hẹn giờ còn lại không cần chạy nữa
        writer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /** Store dùng các đường dẫn chuẩn trong thư mục data/ của ứng dụng, ghi nền hoãn {@value #DEFAULT_SAVE_DELAY_MS} ms. */
    public static DictionaryStore createDefault() {
        return new DictionaryStore(AppPaths.dictionaryFile(), AppPaths.externalOriginalFile(), DEFAULT_SAVE_DELAY_MS);
    }

    public Path dictionaryFile() {
        return dictionaryFile;
    }

    // ===================== Đọc/ghi một file =====================

    /** Ghi đồng bộ (chặn luồng gọi cho tới khi ghi xong). */
    public static void save(SlangDictionary dictionary, Path file) throws IOException {
        writeAtomically(serialize(dictionary), file);
    }

    /**
     * @throws DictionaryFormatException file sai magic/phiên bản, bị cắt cụt hoặc không còn khớp class
     * @throws IOException               lỗi đọc đĩa (không tồn tại, không có quyền...)
     */
    public static SlangDictionary load(Path file) throws IOException {
        // Đọc trên luồng có stack rộng như luồng ghi: file do bản cũ ghi (Trie serialize đệ quy theo độ dài tên)
        // vẫn đọc lại được, thay vì ghi được mà không đọc được.
        return onLargeStack("dictionary-reader", () -> {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                return deserialize(in);
            }
        });
    }

    /** Việc đọc/ghi có thể ném IOException, chạy trên luồng riêng. */
    private interface IoTask<T> {
        T run() throws IOException;
    }

    /** Chạy {@code task} trên một luồng mới có stack {@link #LARGE_STACK_BYTES}, chờ xong rồi trả kết quả. */
    private static <T> T onLargeStack(String name, IoTask<T> task) throws IOException {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(null, () -> {
            try {
                result.set(task.run());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, name, LARGE_STACK_BYTES);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while reading the dictionary file");
        }
        Throwable t = failure.get();
        if (t instanceof IOException io) {
            throw io;
        }
        if (t instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (t instanceof Error error) {
            throw error;
        }
        return result.get();
    }

    static byte[] serialize(SlangDictionary dictionary) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(1 << 20);
        try (ObjectOutputStream out = new ObjectOutputStream(payload)) {
            out.writeObject(dictionary);
        }
        return frame(payload.toByteArray());
    }

    /** Gắn header (magic, phiên bản, CRC32) trước payload. */
    static byte[] frame(byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + payload.length);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeLong(crc32(payload));
            out.write(payload);
        }
        return bytes.toByteArray();
    }

    private static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    static SlangDictionary deserialize(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        try {
            int magic = data.readInt();
            if (magic != MAGIC) {
                throw new DictionaryFormatException("Not a dictionary file (magic 0x" + Integer.toHexString(magic) + ")");
            }
            int version = data.readInt();
            if (version != FORMAT_VERSION) {
                throw new DictionaryFormatException(
                        "Unsupported format version " + version + " (expected " + FORMAT_VERSION + ")");
            }
            long expectedCrc = data.readLong();
            byte[] payload = data.readAllBytes();
            if (crc32(payload) != expectedCrc) {
                throw new DictionaryFormatException("Checksum mismatch: dictionary file is damaged");
            }
            ObjectInputStream objects = new ObjectInputStream(new ByteArrayInputStream(payload));
            objects.setObjectInputFilter(CLASS_FILTER);
            Object object = objects.readObject();
            if (!(object instanceof SlangDictionary dictionary)) {
                throw new DictionaryFormatException("Unexpected object " + object.getClass().getName());
            }
            return dictionary;
        } catch (EOFException | ObjectStreamException | UTFDataFormatException e) {
            // cắt cụt, dữ liệu rác, serialVersionUID không khớp, class bị filter chặn...
            throw new DictionaryFormatException("Corrupted dictionary file: " + e, e);
        } catch (ClassNotFoundException | ClassCastException | LinkageError e) {
            // LinkageError: tên class trong file bị hỏng chỉ khác hoa/thường (ví dụ "SlangDictionarY");
            // trên Windows hệ thống file không phân biệt hoa thường nên JVM ném NoClassDefFoundError
            throw new DictionaryFormatException("Dictionary file does not match this version of the app: " + e, e);
        } catch (RuntimeException e) {
            // Dữ liệu rác sau một header hợp lệ có thể làm readObject của collection ném lỗi runtime bất kỳ;
            // coi như file hỏng để loadOrBuild build lại thay vì làm sập app lúc khởi động.
            throw new DictionaryFormatException("Corrupted dictionary file: " + e, e);
        } catch (StackOverflowError e) {
            // Cấu trúc lồng quá sâu kể cả với stack rộng: không đọc được thì coi như file hỏng,
            // không để Error lọt ra làm app thoát ngay lúc khởi động.
            throw new DictionaryFormatException("Dictionary file is nested too deeply to read", e);
        }
    }

    /** Ghi ra file .tmp cạnh file đích, ép xuống đĩa, rồi đổi tên đè lên file đích. */
    static void writeAtomically(byte[] bytes, Path file) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true); // đảm bảo dữ liệu đã thật sự xuống đĩa trước khi đổi tên
        }
        for (int attempt = 1; ; attempt++) {
            try {
                moveReplacing(tmp, target);
                return;
            } catch (AccessDeniedException e) {
                // Windows: trình diệt virus/indexer có thể đang mở file .dat trong giây lát → chờ rồi thử lại
                if (attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(MOVE_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ===================== Khởi động / Reset =====================

    /**
     * Thử đọc dictionary.dat; chưa có thì build từ slang.txt gốc rồi lưu lại .dat.
     * <p>
     * .dat có nhưng <b>hỏng</b> ({@link DictionaryFormatException}): file cũ được đổi tên thành
     * dictionary.dat.bad (không ghi đè bản .bad có sẵn) rồi mới build lại, và kết quả mang {@link Recovery} để UI
     * báo cho người dùng. Lỗi đọc <b>khác</b> (file đang bị process khác giữ, không có quyền...) thì ném lên luôn:
     * file có thể vẫn tốt, build lại rồi ghi đè sẽ làm mất mọi thay đổi của người dùng.
     * <p>
     * Lưu .dat thất bại (ví dụ thư mục chỉ đọc) không làm hỏng việc khởi động, chỉ báo qua error listener.
     *
     * @throws SlangFormatException slang.txt gốc sai định dạng (kèm số dòng)
     * @throws IOException          không đọc được slang.txt gốc, không đọc được .dat vì lỗi I/O,
     *                              hoặc không đổi tên được .dat hỏng để giữ lại
     */
    public LoadResult loadOrBuild() throws IOException, SlangFormatException {
        long start = System.nanoTime();
        Recovery recovery = null;
        if (Files.exists(dictionaryFile)) {
            try {
                SlangDictionary dictionary = load(dictionaryFile);
                return new LoadResult(dictionary, Source.DAT, System.nanoTime() - start, null);
            } catch (DictionaryFormatException e) {
                Path backup = backUpDamagedFile();
                System.err.println("[DictionaryStore] " + dictionaryFile + " unreadable (" + e.getMessage()
                        + "), kept as " + backup + ", rebuilding from text");
                recovery = new Recovery(backup, e);
            } catch (NoSuchFileException e) {
                // bị xóa ngay sau khi kiểm tra tồn tại: như lần chạy đầu
            }
        }
        SlangDictionary dictionary = loadOriginal();
        long elapsed = System.nanoTime() - start;
        try {
            save(dictionary, dictionaryFile);
        } catch (IOException e) {
            errorListener.accept(e);
        }
        return new LoadResult(dictionary, Source.TEXT, elapsed, recovery);
    }

    /**
     * Đổi tên dictionary.dat hỏng sang tên .bad chưa dùng. Không đổi tên được thì ném lỗi để app dừng lại
     * thay vì ghi đè file (biết đâu người dùng còn cứu được dữ liệu trong đó).
     */
    private Path backUpDamagedFile() throws IOException {
        String name = dictionaryFile.getFileName() + BACKUP_SUFFIX;
        for (int i = 1; i <= MAX_BACKUPS; i++) {
            Path backup = dictionaryFile.resolveSibling(i == 1 ? name : name + i);
            if (Files.exists(backup)) {
                continue; // bản .bad của lần hỏng trước: giữ nguyên
            }
            try {
                Files.move(dictionaryFile, backup);
                return backup;
            } catch (FileAlreadyExistsException e) {
                // vừa có ai tạo đúng tên này → thử tên kế tiếp
            } catch (IOException e) {
                throw new IOException("Dictionary file " + dictionaryFile + " is damaged and could not be backed up"
                        + " (it was left untouched): " + e, e);
            }
        }
        throw new IOException("Dictionary file " + dictionaryFile + " is damaged and too many backups ("
                + MAX_BACKUPS + ") already exist; it was left untouched");
    }

    /** Build từ điển từ slang.txt gốc: data/slang.txt nếu có, không thì bản nhúng trong jar. Dùng cho Reset. */
    public SlangDictionary loadOriginal() throws IOException, SlangFormatException {
        if (Files.isRegularFile(externalOriginalFile)) {
            return SlangDictionary.build(SlangFileParser.parse(externalOriginalFile));
        }
        try (InputStream in = DictionaryStore.class.getResourceAsStream(BUNDLED_ORIGINAL)) {
            if (in == null) {
                throw new FileNotFoundException("Bundled resource " + BUNDLED_ORIGINAL + " is missing");
            }
            return SlangDictionary.build(SlangFileParser.parse(in));
        }
    }

    /** File gốc đang được dùng (để hiển thị/log). */
    public String originalSourceDescription() {
        return Files.isRegularFile(externalOriginalFile) ? externalOriginalFile.toString()
                : "jar:" + BUNDLED_ORIGINAL;
    }

    // ===================== Ghi nền =====================

    /**
     * Yêu cầu lưu từ điển; trả về ngay. File sẽ chứa trạng thái của từ điển tại lúc luồng nền chụp lại,
     * tức là mới nhất có thể (bao gồm mọi thay đổi trước lời gọi này). Store có hoãn thì mỗi lần yêu cầu mới
     * lại dời giờ ghi (debounce): sửa liên tục chỉ ghi một lần, sau lần sửa cuối.
     */
    public void saveInBackground(SlangDictionary dictionary) {
        Objects.requireNonNull(dictionary, "dictionary");
        pending.set(dictionary);
        long ticket = latestSave.incrementAndGet();
        try {
            writer.schedule(() -> writeIfLatest(ticket), saveDelayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // store đã close (app đang thoát) → báo lỗi thay vì ném exception lên EDT
            errorListener.accept(new IOException("Store is closed, change was not saved", e));
        }
    }

    /** Chạy trên luồng ghi. */
    private void writeIfLatest(long ticket) {
        if (ticket != latestSave.get()) {
            return; // còn yêu cầu mới hơn trong hàng đợi → để lần đó ghi, đỡ serialize thừa
        }
        SlangDictionary dictionary = pending.getAndSet(null);
        if (dictionary == null) {
            return; // flush() đã ghi rồi
        }
        try {
            writeAtomically(serialize(dictionary), dictionaryFile);
        } catch (IOException e) {
            errorListener.accept(e);
        } catch (RuntimeException | Error e) {
            // Không để lỗi bất ngờ bị executor nuốt mất một cách im lặng. Error cũng vậy (ví dụ StackOverflowError):
            // FutureTask giữ nó lại mà không ai đọc, mọi lần lưu sau đó thất bại mà người dùng không hề biết.
            errorListener.accept(new IOException("Unexpected error while saving", e));
        }
    }

    /** Ghi ngay lần lưu đang chờ (kể cả lần đang hẹn giờ) và chờ ghi xong. Gọi khi thoát app. */
    public void flush() {
        try {
            writer.submit(() -> writeIfLatest(latestSave.get())).get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // đã close: close() đã chờ ghi xong rồi, không còn gì để chờ
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            errorListener.accept(new IOException("Background save did not finish", e));
        }
    }

    /** Lỗi khi ghi nền được báo về đây (mặc định in ra stderr; UI sẽ gắn listener hiện thông báo). */
    public void setErrorListener(Consumer<IOException> listener) {
        this.errorListener = listener == null ? DictionaryStore::logError : listener;
    }

    /** flush rồi dừng luồng ghi. */
    @Override
    public void close() {
        flush();
        writer.shutdown();
    }

    private static void logError(IOException e) {
        System.err.println("[DictionaryStore] save failed: " + e);
    }
}
