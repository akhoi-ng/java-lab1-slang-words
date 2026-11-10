package lab01.slangdict._24127430.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

import lab01.slangdict._24127430.App;

/**
 * Xác định thư mục data/ của ứng dụng, không phụ thuộc thư mục đang chạy lệnh.
 * <ol>
 *   <li>System property {@value #DATA_DIR_PROPERTY} nếu có (dùng cho test hoặc chạy tay).</li>
 *   <li>Chạy từ file jar: thư mục chứa jar + /data. Nhờ vậy double-click jar ở đâu thì data nằm cạnh đó.</li>
 *   <li>Chạy trong IDE (class nằm trong thư mục target/classes): thư mục làm việc + /data.</li>
 * </ol>
 */
public final class AppPaths {

    public static final String DATA_DIR_PROPERTY = "slangdict.data.dir";

    static final String DATA_DIR_NAME = "data";
    static final String DICTIONARY_FILE = "dictionary.dat";
    static final String HISTORY_FILE = "history.dat";
    static final String SETTINGS_FILE = "settings.properties";
    static final String ORIGINAL_FILE = "slang.txt";

    private AppPaths() {
    }

    /** Thư mục data/ (tự tạo nếu chưa có). */
    public static Path dataDir() {
        Path dir = resolveDataDir(System.getProperty(DATA_DIR_PROPERTY), codeLocation(),
                Path.of("").toAbsolutePath());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + dir, e);
        }
        return dir;
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
