package lab01.slangdict._24127430.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Thư mục tạm cho test có ghi file, thay cho {@code @TempDir}.
 * <p>
 * Trên Windows, trình diệt virus đôi khi còn giữ file vừa ghi trong giây lát, khiến {@code @TempDir} dọn dẹp thất
 * bại và đánh fail cả test dù test đúng (lỗi chập chờn, đã gặp ở DictionaryStoreTest và HistoryServiceTest).
 * Ở đây dọn kiểu "cố gắng hết mức": file sót lại trong %TEMP% không ảnh hưởng gì.
 */
public final class ScratchDir {

    private ScratchDir() {
    }

    public static Path create(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    public static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | UncheckedIOException ignored) {
            // bỏ qua: xem chú thích của class
        }
    }
}
