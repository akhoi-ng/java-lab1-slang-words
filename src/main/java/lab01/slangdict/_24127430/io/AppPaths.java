package lab01.slangdict._24127430.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Objects;

import lab01.slangdict._24127430.App;

/**
 * Xác định thư mục data/ của ứng dụng, không phụ thuộc thư mục đang chạy lệnh.
 * <ol>
 *   <li>System property {@value #DATA_DIR_PROPERTY} nếu có (dùng cho test hoặc chạy tay).</li>
 *   <li>Chạy từ file jar: thư mục chứa jar + /data. Nhờ vậy double-click jar ở đâu thì data nằm cạnh đó.</li>
 *   <li>Chạy trong IDE (class nằm trong thư mục target/classes): thư mục làm việc + /data.</li>
 * </ol>
 * Thư mục ở bước 2–3 không tạo hoặc không ghi được (jar nằm trong {@code C:\Program Files}, USB khóa ghi...) thì
 * dùng {@code <home>/}{@value #FALLBACK_DIR_NAME} thay vì để app không khởi động được. Thư mục do người dùng tự chỉ
 * định ở bước 1 thì không tự đổi: lỗi được báo ra như cũ.
 */
public final class AppPaths {

    public static final String DATA_DIR_PROPERTY = "slangdict.data.dir";

    static final String DATA_DIR_NAME = "data";
    static final String DICTIONARY_FILE = "dictionary.dat";
    static final String HISTORY_FILE = "history.dat";
    static final String SETTINGS_FILE = "settings.properties";
    static final String ORIGINAL_FILE = "slang.txt";
    /** Thư mục dự phòng, nằm trong thư mục home của người dùng. */
    public static final String FALLBACK_DIR_NAME = ".slang-dictionary";

    /**
     * Thư mục data/ đã chọn.
     *
     * @param dir          thư mục dùng thật (đã tạo, ghi được)
     * @param unwritable   thư mục lẽ ra được dùng nhưng không ghi được, nên phải chuyển sang {@code dir};
     *                     null nếu dùng đúng thư mục mặc định
     * @param newlyCreated {@code dir} vừa được tạo ở lần chạy này (để chỉ báo cho người dùng một lần)
     */
    public record Location(Path dir, Path unwritable, boolean newlyCreated) {
        public boolean isFallback() {
            return unwritable != null;
        }
    }

    /** Chọn một lần cho mỗi giá trị của system property, để mọi file nằm cùng một chỗ trong suốt phiên chạy. */
    private static Location cached;
    private static String cachedOverride;

    private AppPaths() {
    }

    /** Thư mục data/ (tự tạo nếu chưa có). */
    public static Path dataDir() {
        return location().dir();
    }

    /** Thư mục data/ đang dùng và có phải thư mục dự phòng không. */
    public static synchronized Location location() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (cached == null || !Objects.equals(override, cachedOverride)) {
            cached = chooseLocation(override, codeLocation(), Path.of("").toAbsolutePath(),
                    Path.of(System.getProperty("user.home")));
            cachedOverride = override;
        }
        return cached;
    }

    /** Từ điển đã index (serialize). */
    public static Path dictionaryFile() {
        return dataDir().resolve(DICTIONARY_FILE);
    }

    public static Path historyFile() {
        return dataDir().resolve(HISTORY_FILE);
    }

    public static Path settingsFile() {
        return dataDir().resolve(SETTINGS_FILE);
    }

    /** data/slang.txt: nếu có thì được ưu tiên hơn bản slang.txt nhúng trong jar. */
    public static Path externalOriginalFile() {
        return dataDir().resolve(ORIGINAL_FILE);
    }

    /**
     * Phần quyết định thuần (không đụng system property, không tạo thư mục) để test được.
     *
     * @param override     giá trị system property, có thể null
     * @param codeLocation file jar hoặc thư mục class đang chạy, có thể null
     * @param workingDir   thư mục làm việc hiện tại
     */
    static Path resolveDataDir(String override, Path codeLocation, Path workingDir) {
        if (override != null && !override.isBlank()) {
            return Path.of(override.strip()).toAbsolutePath();
        }
        if (codeLocation != null && Files.isRegularFile(codeLocation)) {
            // Chạy từ jar: codeLocation là chính file .jar
            return codeLocation.toAbsolutePath().getParent().resolve(DATA_DIR_NAME);
        }
        return workingDir.toAbsolutePath().resolve(DATA_DIR_NAME);
    }

    /**
     * Chọn thư mục data/: thư mục của {@link #resolveDataDir}, không ghi được thì {@code home}/{@value
     * #FALLBACK_DIR_NAME}. Tạo thư mục nếu chưa có.
     *
     * @throws UncheckedIOException thư mục người dùng tự chỉ định không dùng được, hoặc cả hai thư mục đều không dùng
     *                              được
     */
    static Location chooseLocation(String override, Path codeLocation, Path workingDir, Path home) {
        Path preferred = resolveDataDir(override, codeLocation, workingDir);
        boolean preferredExisted = Files.isDirectory(preferred);
        IOException problem = prepare(preferred);
        if (problem == null) {
            return new Location(preferred, null, !preferredExisted);
        }
        if (override != null && !override.isBlank()) {
            throw new UncheckedIOException("Cannot use data directory " + preferred, problem);
        }
        Path fallback = home.toAbsolutePath().resolve(FALLBACK_DIR_NAME);
        boolean fallbackExisted = Files.isDirectory(fallback);
        IOException fallbackProblem = prepare(fallback);
        if (fallbackProblem != null) {
            UncheckedIOException error = new UncheckedIOException(
                    "Cannot use data directory " + preferred + " nor " + fallback, fallbackProblem);
            error.addSuppressed(problem);
            throw error;
        }
        System.err.println("[AppPaths] " + preferred + " is not writable (" + problem + "), using " + fallback);
        return new Location(fallback, preferred, !fallbackExisted);
    }

    /**
     * Tạo thư mục rồi thử ghi và xóa một file tạm. Không dùng {@code Files.isWritable}: trên Windows nó không phản
     * ánh đúng quyền ghi thật (ACL, thư mục Program Files), còn ghi thử thì chắc chắn.
     *
     * @return null nếu dùng được, ngược lại là lỗi gặp phải
     */
    private static IOException prepare(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".write-test", ".tmp");
            Files.delete(probe);
            return null;
        } catch (IOException e) {
            return e;
        } catch (SecurityException e) {
            return new IOException(e);
        }
    }

    /** Vị trí nạp class App: file .jar hoặc thư mục target/classes; null nếu không xác định được. */
    private static Path codeLocation() {
        try {
            CodeSource source = App.class.getProtectionDomain().getCodeSource();
            // Đi qua URI để xử lý đúng đường dẫn có dấu cách / tiếng Việt (bị mã hoá %20 trong URL)
            return source == null ? null : Path.of(source.getLocation().toURI());
        } catch (URISyntaxException | SecurityException | IllegalArgumentException e) {
            return null;
        }
    }
}
