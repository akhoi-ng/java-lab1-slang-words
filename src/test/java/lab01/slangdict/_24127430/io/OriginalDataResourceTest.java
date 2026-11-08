package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Đảm bảo slang.txt gốc được đóng gói vào classpath tại /data/slang.txt và không bị cắt/hỏng.
 */
class OriginalDataResourceTest {

    @Test
    void originalSlangFileIsBundledIntact() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/slang.txt")) {
            assertNotNull(in, "/data/slang.txt phải có trên classpath");
            List<String> lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().filter(l -> !l.isBlank()).collect(Collectors.toList());

            assertEquals("Slag`Meaning", lines.get(0), "dòng header");
            assertEquals(7638, lines.size() - 1, "số entry trong file gốc");
        }
    }
}
