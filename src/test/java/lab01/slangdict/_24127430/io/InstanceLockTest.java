package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceLockTest {

    @Test
    void secondHolderIsRefusedUntilTheFirstReleases(@TempDir Path dir) throws Exception {
        Optional<InstanceLock> first = InstanceLock.tryAcquire(dir);
        assertTrue(first.isPresent());
        assertTrue(Files.exists(dir.resolve(InstanceLock.LOCK_FILE)));

        assertFalse(InstanceLock.tryAcquire(dir).isPresent());

        first.get().close();
        Optional<InstanceLock> again = InstanceLock.tryAcquire(dir);
        assertTrue(again.isPresent(), "released lock can be taken again");
        again.get().close();
    }

    @Test
    void differentDataFoldersDoNotBlockEachOther(@TempDir Path dir) throws Exception {
        try (InstanceLock a = InstanceLock.tryAcquire(Files.createDirectories(dir.resolve("a"))).orElseThrow();
             InstanceLock b = InstanceLock.tryAcquire(Files.createDirectories(dir.resolve("b"))).orElseThrow()) {
            assertTrue(a != b);
        }
    }

    /** Giống thật: process thứ hai (một JVM khác) không lấy được khóa process này đang giữ. */
    @Test
    void anotherProcessIsRefused(@TempDir Path dir) throws Exception {
        try (InstanceLock held = InstanceLock.tryAcquire(dir).orElseThrow()) {
            assertEquals("refused", runProbeProcess(dir));
        }
        assertEquals("acquired", runProbeProcess(dir), "lock released → another process can start");
    }

    private static String runProbeProcess(Path dir) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(), dir.toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "probe process timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, process.exitValue(), output);
        String[] lines = output.split("\\R");
        return lines[lines.length - 1];
    }

    /** Chạy trong process con: thử khóa thư mục được truyền vào rồi in kết quả. */
    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(new File(args[0]).toPath());
            System.out.println(lock.isPresent() ? "acquired" : "refused");
            if (lock.isPresent()) {
                lock.get().close();
            }
        }
    }
}
