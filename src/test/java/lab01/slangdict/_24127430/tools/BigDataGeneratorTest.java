package lab01.slangdict._24127430.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.model.SlangEntry;

class BigDataGeneratorTest {

    @Test
    void keepsOriginalAndAddsUniqueSyntheticEntries() throws Exception {
        List<SlangEntry> base = BigDataGenerator.loadBundledOriginal();
        assertEquals(7638, base.size());

        List<SlangEntry> all = BigDataGenerator.generate(base, 20_000, 1L);
        assertEquals(20_000, all.size());
        for (int i = 0; i < base.size(); i++) {
            assertSame(base.get(i), all.get(i), "original entries come first, untouched");
        }

        Set<Integer> ids = new HashSet<>();
        Set<String> generatedNames = new HashSet<>();
        Set<String> originalNames = new HashSet<>();
        base.forEach(e -> originalNames.add(Tokenizer.normalizeName(e.getSlang())));
        for (SlangEntry entry : all) {
            assertTrue(ids.add(entry.getId()), "duplicate id " + entry.getId());
        }
        for (SlangEntry entry : all.subList(base.size(), all.size())) {
            String name = Tokenizer.normalizeName(entry.getSlang());
            assertTrue(generatedNames.add(name), "generated name repeated: " + name);
            assertTrue(!originalNames.contains(name), "generated name clashes with original: " + name);
            assertTrue(name.matches("[a-z][a-z0-9]{1,7}"), name);
            assertTrue(entry.getMeanings().size() >= 1 && entry.getMeanings().size() <= 3);
        }
    }

    @Test
    void sameSeedSameData() throws Exception {
        List<SlangEntry> base = BigDataGenerator.loadBundledOriginal();
        List<SlangEntry> a = BigDataGenerator.generate(base, 9_000, 42L);
        List<SlangEntry> b = BigDataGenerator.generate(base, 9_000, 42L);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(SlangFileParser.formatLine(a.get(i)), SlangFileParser.formatLine(b.get(i)));
        }
    }

    @Test
    void generatedFileCanBeParsedBack(@TempDir Path dir) throws Exception {
        List<SlangEntry> all = BigDataGenerator.generate(BigDataGenerator.loadBundledOriginal(), 10_000, 3L);
        Path file = dir.resolve("big.txt");
        SlangFileParser.write(file, all);

        List<SlangEntry> parsed = SlangFileParser.parse(file);
        assertEquals(all.size(), parsed.size());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(SlangFileParser.formatLine(all.get(i)), SlangFileParser.formatLine(parsed.get(i)));
        }
    }

    @Test
    void mainWritesRequestedCount(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub").resolve("out.txt"); // thư mục cha chưa tồn tại
        BigDataGenerator.main(new String[] {"8000", file.toString()});
        assertEquals(8000, SlangFileParser.parse(file).size());
    }

    @Test
    void totalSmallerThanBaseIsRejected() throws Exception {
        List<SlangEntry> base = BigDataGenerator.loadBundledOriginal();
        assertThrows(IllegalArgumentException.class, () -> BigDataGenerator.generate(base, 100, 1L));
    }
}
