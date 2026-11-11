package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @Test
    void overrideWins(@TempDir Path dir) throws Exception {
        Path jar = Files.createFile(dir.resolve("app.jar"));
        Path custom = dir.resolve("custom");
        assertEquals(custom, AppPaths.resolveDataDir(" " + custom + " ", jar, dir.resolve("work")));
    }

    @Test
    void runningFromJarUsesFolderNextToJar(@TempDir Path dir) throws Exception {
        Path jar = Files.createFile(Files.createDirectories(dir.resolve("dist")).resolve("slang-dictionary.jar"));
        assertEquals(dir.resolve("dist").resolve("data"), AppPaths.resolveDataDir(null, jar, dir.resolve("work")));
        assertEquals(dir.resolve("dist").resolve("data"), AppPaths.resolveDataDir("  ", jar, dir.resolve("work")));
    }

    @Test
    void runningFromClassesFolderUsesWorkingDir(@TempDir Path dir) throws Exception {
        Path classes = Files.createDirectories(dir.resolve("target").resolve("classes"));
        Path work = dir.resolve("work");
        assertEquals(work.resolve("data"), AppPaths.resolveDataDir(null, classes, work));
        assertEquals(work.resolve("data"), AppPaths.resolveDataDir(null, null, work));
    }

    @Test
    void writableFolderNextToJarIsUsed(@TempDir Path dir) throws Exception {
        Path jar = Files.createFile(Files.createDirectories(dir.resolve("dist")).resolve("app.jar"));
        AppPaths.Location location = AppPaths.chooseLocation(null, jar, dir.resolve("work"), dir.resolve("home"));
        assertEquals(dir.resolve("dist").resolve("data"), location.dir());
        assertFalse(location.isFallback());
        assertTrue(Files.isDirectory(location.dir()));
        assertFalse(Files.exists(dir.resolve("home")));
        try (var files = Files.list(location.dir())) {
            assertEquals(0, files.count(), "the write test leaves nothing behind");
        }
    }

    @Test
    void unwritableFolderNextToJarFallsBackToHome(@TempDir Path dir) throws Exception {
        Path dist = Files.createDirectories(dir.resolve("dist"));
        Path jar = Files.createFile(dist.resolve("app.jar"));
        Files.createFile(dist.resolve("data")); // một file chiếm tên data/ → không tạo được thư mục (như ổ chỉ đọc)
        Path home = dir.resolve("home");

        AppPaths.Location first = AppPaths.chooseLocation(null, jar, dir.resolve("work"), home);
        assertEquals(home.resolve(AppPaths.FALLBACK_DIR_NAME), first.dir());
        assertEquals(dist.resolve("data"), first.unwritable());
        assertTrue(first.isFallback());
        assertTrue(first.newlyCreated());
        assertTrue(Files.isDirectory(first.dir()));

        AppPaths.Location second = AppPaths.chooseLocation(null, jar, dir.resolve("work"), home);
        assertEquals(first.dir(), second.dir());
        assertFalse(second.newlyCreated(), "the notice is shown only on the run that created the folder");
    }

    @Test
    void unusableFolderChosenByTheUserIsReportedNotReplaced(@TempDir Path dir) throws Exception {
        Path blocked = Files.createFile(dir.resolve("blocked"));
        assertThrows(UncheckedIOException.class,
                () -> AppPaths.chooseLocation(blocked.toString(), null, dir.resolve("work"), dir.resolve("home")));
        assertFalse(Files.exists(dir.resolve("home")));
    }

    @Test
    void bothFoldersUnusableFails(@TempDir Path dir) throws Exception {
        Path work = Files.createDirectories(dir.resolve("work"));
        Files.createFile(work.resolve("data"));
        Path home = Files.createDirectories(dir.resolve("home"));
        Files.createFile(home.resolve(AppPaths.FALLBACK_DIR_NAME));
        assertThrows(UncheckedIOException.class, () -> AppPaths.chooseLocation(null, null, work, home));
    }

    @Test
    void dataDirHonoursSystemPropertyAndCreatesFolder(@TempDir Path dir) {
        Path data = dir.resolve("nested").resolve("data");
        String saved = System.getProperty(AppPaths.DATA_DIR_PROPERTY);
        System.setProperty(AppPaths.DATA_DIR_PROPERTY, data.toString());
        try {
            assertEquals(data, AppPaths.dataDir());
            assertTrue(Files.isDirectory(data));
            assertEquals(data.resolve("dictionary.dat"), AppPaths.dictionaryFile());
            assertEquals(data.resolve("history.dat"), AppPaths.historyFile());
            assertEquals(data.resolve("settings.properties"), AppPaths.settingsFile());
            assertEquals(data.resolve("slang.txt"), AppPaths.externalOriginalFile());
        } finally {
            if (saved == null) {
                System.clearProperty(AppPaths.DATA_DIR_PROPERTY);
            } else {
                System.setProperty(AppPaths.DATA_DIR_PROPERTY, saved);
            }
        }
    }
}
