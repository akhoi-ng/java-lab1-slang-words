package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
