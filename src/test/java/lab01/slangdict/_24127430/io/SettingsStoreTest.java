package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lab01.slangdict._24127430.model.Theme;
import lab01.slangdict._24127430.testutil.ScratchDir;

class SettingsStoreTest {

    private static final Locale FALLBACK = Locale.forLanguageTag("vi");

    /** Không dùng @TempDir: xem {@link ScratchDir} (đã gặp lỗi dọn dẹp chập chờn ở chính test này). */
    private Path dir;

    @BeforeEach
    void createDir() throws IOException {
        dir = ScratchDir.create("slangdict-settings");
    }

    @AfterEach
    void deleteDir() {
        ScratchDir.deleteQuietly(dir);
    }

    @Test
    void missingFileGivesDefaults() {
        SettingsStore store = new SettingsStore(dir.resolve("settings.properties"));
        assertEquals(FALLBACK, store.language(FALLBACK));
        assertEquals(Theme.DARK, SettingsStore.DEFAULT_THEME, "first run uses the dark theme");
        assertEquals(SettingsStore.DEFAULT_THEME, store.theme());
    }

    @Test
    void savedValuesAreReadBackByANewStore() throws IOException {
        Path file = dir.resolve("sub/settings.properties"); // thư mục cha chưa có → tự tạo
        SettingsStore store = new SettingsStore(file);
        store.setLanguage(Locale.ENGLISH);
        store.setTheme(Theme.DARK);
        store.save();

        SettingsStore reloaded = new SettingsStore(file);
        assertEquals(Locale.ENGLISH, reloaded.language(FALLBACK));
        assertEquals(Theme.DARK, reloaded.theme());
        assertFalse(Files.exists(file.resolveSibling("settings.properties.tmp")), "temp file must be renamed away");
    }

    @Test
    void unknownOrBlankValuesFallBackToDefaults() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "language=   \ntheme=PURPLE\n", StandardCharsets.UTF_8);
        SettingsStore store = new SettingsStore(file);
        assertEquals(FALLBACK, store.language(FALLBACK));
        assertEquals(SettingsStore.DEFAULT_THEME, store.theme());
    }

    @Test
    void themeIsCaseInsensitive() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "theme=light\n", StandardCharsets.UTF_8);
        assertEquals(Theme.LIGHT, new SettingsStore(file).theme());
    }

    @Test
    void corruptedFileDoesNotPreventStartup() throws IOException {
        Path file = dir.resolve("settings.properties");
        // "\\u12" là escape unicode sai → Properties.load ném IllegalArgumentException
        Files.writeString(file, "theme=LIGHT\nlanguage=\\u12\n", StandardCharsets.UTF_8);
        SettingsStore store = new SettingsStore(file);
        assertEquals(SettingsStore.DEFAULT_THEME, store.theme()); // bỏ cả file, kể cả dòng theme hợp lệ
        assertEquals(FALLBACK, store.language(FALLBACK));

        store.setTheme(Theme.LIGHT); // và vẫn ghi đè được bằng file hợp lệ
        store.save();
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("theme=LIGHT"));
        assertEquals(Theme.LIGHT, new SettingsStore(file).theme());
    }
}
