package lab01.slangdict._24127430.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import lab01.slangdict._24127430.model.Theme;

/**
 * Cài đặt người dùng (ngôn ngữ, theme) trong {@code data/settings.properties}, ví dụ:
 * <pre>
 * language=vi
 * theme=DARK
 * </pre>
 * File thiếu, hỏng hoặc chứa giá trị lạ thì dùng giá trị mặc định: cài đặt không bao giờ làm app
 * không khởi động được. Ghi bằng cách ghi file tạm rồi đổi tên, giống {@link DictionaryStore}.
 */
public class SettingsStore {

    static final String KEY_LANGUAGE = "language";
    static final String KEY_THEME = "theme";

    /** Theme lần chạy đầu tiên (chưa có settings). */
    public static final Theme DEFAULT_THEME = Theme.DARK;

    private final Path file;
    private final Properties properties = new Properties();

    public SettingsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    /** Store dùng file {@code data/settings.properties} cạnh jar. */
    public static SettingsStore createDefault() {
        return new SettingsStore(AppPaths.settingsFile());
    }

    public Path file() {
        return file;
    }

    /**
     * Ngôn ngữ đã lưu, hoặc {@code fallback} nếu chưa lưu. Việc kiểm tra ngôn ngữ có bản dịch hay không
     * là của {@code Messages.resolve}, không phải của tầng io.
     */
    public Locale language(Locale fallback) {
        String tag = properties.getProperty(KEY_LANGUAGE, "").strip();
        return tag.isEmpty() ? fallback : Locale.forLanguageTag(tag);
    }

    public void setLanguage(Locale language) {
        properties.setProperty(KEY_LANGUAGE, language.toLanguageTag());
    }

    /** Theme đã lưu; chưa lưu hoặc giá trị lạ thì {@link #DEFAULT_THEME}. */
    public Theme theme() {
        String value = properties.getProperty(KEY_THEME, "").strip();
        for (Theme theme : Theme.values()) {
            if (theme.name().equalsIgnoreCase(value)) {
                return theme;
            }
        }
        return DEFAULT_THEME;
    }

    public void setTheme(Theme theme) {
        properties.setProperty(KEY_THEME, theme.name());
    }

    // [THAM KHẢO] Lưu/đọc cấu hình bằng java.util.Properties (store/load), theo Oracle Java Tutorials, "Properties"
    //   https://docs.oracle.com/javase/tutorial/essential/environment/properties.html
    /** Ghi xuống đĩa (file nhỏ, vài ms, gọi trên EDT được). */
    public void save() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            properties.store(writer, "Slang Dictionary settings");
        }
        DictionaryStore.writeAtomically(bytes.toByteArray(), file);
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            // IllegalArgumentException: Properties.load gặp chuỗi \\u sai định dạng
            System.err.println("[SettingsStore] " + file + " unreadable, using defaults: " + e);
            properties.clear();
        }
    }
    // [HẾT THAM KHẢO]
}
