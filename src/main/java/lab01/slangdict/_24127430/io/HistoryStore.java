package lab01.slangdict._24127430.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import lab01.slangdict._24127430.model.HistoryItem;

/**
 * Đọc/ghi lịch sử tìm kiếm ở {@code data/history.dat}.
 *
 * <pre>
 * Định dạng file:  [int MAGIC "HIST"] [int FORMAT_VERSION] [payload = ArrayList&lt;HistoryItem&gt; qua ObjectOutputStream]
 * </pre>
 * Cách làm giống {@link DictionaryStore} nhưng gọn hơn (lịch sử chỉ vài chục KB):
 * <ul>
 *   <li>ghi file .tmp rồi đổi tên ({@link DictionaryStore#writeAtomically}), tắt app giữa chừng không hỏng file cũ;</li>
 *   <li>{@link #saveInBackground} trả về ngay, một luồng nền ghi đĩa, nhiều lần lưu dồn dập chỉ ghi lần mới nhất;</li>
 *   <li>{@link ObjectInputFilter} chỉ cho đọc class của app, {@code java.util}, {@code java.lang}, {@code java.time}.</li>
 * </ul>
 * Không có CRC như .dat: lịch sử hỏng một phần cũng không làm sai chức năng nào, chỉ cần đọc được hoặc bỏ.
 */
public class HistoryStore implements AutoCloseable {

    /** "HIST" theo mã ASCII. */
    public static final int MAGIC = 0x48495354;
    public static final int FORMAT_VERSION = 1;

    /**
     * {@code java.time.*}: {@code LocalDateTime} được serialize qua class trung gian {@code java.time.Ser}.
     * maxarray: lịch sử tối đa vài nghìn dòng, file hỏng khai báo mảng khổng lồ sẽ bị chặn.
     */
    private static final ObjectInputFilter CLASS_FILTER = ObjectInputFilter.Config.createFilter(
            "maxarray=100000;maxdepth=20;lab01.slangdict._24127430.model.*;java.util.*;java.lang.*;java.time.*;!*");

    private static final long FLUSH_TIMEOUT_SECONDS = 10;

    private final Path file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "history-writer");
        thread.setDaemon(true); // không giữ JVM sống; lúc thoát app gọi close() để chờ ghi xong
        return thread;
    });
    private final AtomicLong latestSave = new AtomicLong();
    private volatile Consumer<IOException> errorListener = HistoryStore::logError;

    public HistoryStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** Store dùng file {@code data/history.dat} cạnh jar. */
    public static HistoryStore createDefault() {
        return new HistoryStore(AppPaths.historyFile());
    }

    public Path file() {
        return file;
    }

    // ===================== Đọc / ghi đồng bộ =====================

    /**
     * Đọc lịch sử, mới nhất ở đầu.
     *
     * @return danh sách rỗng nếu chưa có file (lần chạy đầu)
     * @throws IOException file hỏng, không phải file lịch sử, hoặc không đọc được
     */
    public List<HistoryItem> load() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return deserialize(in);
        }
    }

    /** Ghi đồng bộ (chặn luồng gọi cho tới khi ghi xong). */
    public void save(List<HistoryItem> items) throws IOException {
        DictionaryStore.writeAtomically(serialize(items), file);
    }

    static byte[] serialize(List<HistoryItem> items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(bytes)) {
            header.writeInt(MAGIC);
            header.writeInt(FORMAT_VERSION);
            header.flush();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(new ArrayList<>(items)); // ArrayList: kiểu cụ thể, đọc lại chắc chắn sửa được
            }
        }
        return bytes.toByteArray();
    }

    static List<HistoryItem> deserialize(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        try {
            int magic = data.readInt();
            if (magic != MAGIC) {
                throw new IOException("Not a history file (magic 0x" + Integer.toHexString(magic) + ")");
            }
            int version = data.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported history format version " + version);
            }
            ObjectInputStream objects = new ObjectInputStream(data);
            objects.setObjectInputFilter(CLASS_FILTER);
            Object object = objects.readObject();
            if (!(object instanceof List<?> list)) {
                throw new IOException("Unexpected object " + object.getClass().getName());
            }
            // Kiểm tra từng phần tử: generic bị xóa lúc chạy (type erasure), nên List<?> có thể chứa bất cứ gì
            List<HistoryItem> items = new ArrayList<>(list.size());
            for (Object element : list) {
                if (!(element instanceof HistoryItem item)) {
                    throw new IOException("Unexpected history element "
                            + (element == null ? "null" : element.getClass().getName()));
                }
                items.add(item);
            }
            return items;
        } catch (EOFException | ObjectStreamException | ClassNotFoundException e) {
            // cắt cụt, dữ liệu rác, class bị filter chặn, record thiếu field (compact constructor từ chối)...
            throw new IOException("Corrupted history file: " + e, e);
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("Corrupted history file: " + e, e);
        }
    }

    // ===================== Ghi nền =====================

    /**
     * Yêu cầu lưu; trả về ngay. {@code snapshot} phải là bản sao mà luồng gọi không sửa nữa
     * ({@code HistoryService} truyền {@code List.copyOf(...)}), vì luồng nền đọc nó sau.
     */
    public void saveInBackground(List<HistoryItem> snapshot) {
        List<HistoryItem> copy = List.copyOf(snapshot);
        long ticket = latestSave.incrementAndGet();
        try {
            writer.execute(() -> writeIfLatest(copy, ticket));
        } catch (RejectedExecutionException e) {
            errorListener.accept(new IOException("History store is closed, change was not saved", e));
        }
    }

    /** Chạy trên luồng ghi. */
    private void writeIfLatest(List<HistoryItem> snapshot, long ticket) {
        if (ticket != latestSave.get()) {
            return; // còn bản mới hơn đang chờ → để lần đó ghi
        }
        try {
            save(snapshot);
        } catch (IOException e) {
            errorListener.accept(e);
        } catch (RuntimeException e) {
            errorListener.accept(new IOException("Unexpected error while saving history", e));
        }
    }

    /** Chờ mọi lần ghi nền đang chờ hoàn tất. */
    public void flush() {
        try {
            writer.submit(() -> { }).get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // đã close
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            errorListener.accept(new IOException("Background history save did not finish", e));
        }
    }

    /** Lỗi khi ghi nền được báo về đây (mặc định in ra stderr). Listener chạy trên luồng ghi. */
    public void setErrorListener(Consumer<IOException> listener) {
        this.errorListener = listener == null ? HistoryStore::logError : listener;
    }

    /** flush rồi dừng luồng ghi. */
    @Override
    public void close() {
        flush();
        writer.shutdown();
    }

    private static void logError(IOException e) {
        System.err.println("[HistoryStore] save failed: " + e);
    }
}
