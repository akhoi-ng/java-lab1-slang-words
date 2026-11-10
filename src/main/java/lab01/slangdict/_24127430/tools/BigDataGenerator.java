package lab01.slangdict._24127430.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lab01.slangdict._24127430.core.Tokenizer;
import lab01.slangdict._24127430.io.AppPaths;
import lab01.slangdict._24127430.io.DictionaryStore;
import lab01.slangdict._24127430.io.SlangFileParser;
import lab01.slangdict._24127430.io.SlangFormatException;
import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Sinh file slang lớn (mặc định 100.000 entry) để kiểm tra yêu cầu "mỗi chức năng &lt; 1 giây".
 * <p>
 * File sinh ra = toàn bộ 7.638 entry gốc + entry tổng hợp cho đủ số lượng:
 * <ul>
 *   <li>tên: 2–8 ký tự in hoa (đôi khi có chữ số, như "GR8"), không trùng tên nào đã có;</li>
 *   <li>nghĩa: 1–3 nghĩa, mỗi nghĩa ghép 2–7 từ lấy từ definition thật. Từ được chọn theo
 *       đúng tần suất xuất hiện trong dữ liệu gốc, nên phân bố từ vựng giống dữ liệu thật
 *       (từ phổ biến như "you" xuất hiện nhiều) và benchmark phản ánh đúng thực tế.</li>
 * </ul>
 * Dùng seed cố định nên chạy lại luôn ra đúng file cũ.
 *
 * <pre>
 * Cách chạy (sau khi .\mvnw.cmd package):
 *   java -cp target\slang-dictionary.jar lab01.slangdict._24127430.tools.BigDataGenerator [số lượng] [file ra]
 * Mặc định: 100000 entry → data\slang-100k.txt (thư mục data cạnh file jar)
 * </pre>
 */
public final class BigDataGenerator {

    public static final int DEFAULT_COUNT = 100_000;
    public static final String DEFAULT_FILE_NAME = "slang-100k.txt";
    /** Seed cố định (MSSV) để mỗi lần chạy sinh ra cùng một dữ liệu. */
    public static final long DEFAULT_SEED = 24127430L;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 8;
    private static final int MIN_WORDS_PER_MEANING = 2;
    private static final int MAX_WORDS_PER_MEANING = 7;

    private BigDataGenerator() {
    }

    public static void main(String[] args) throws IOException, SlangFormatException {
        int count;
        try {
            count = args.length > 0 ? Integer.parseInt(args[0].strip()) : DEFAULT_COUNT;
        } catch (NumberFormatException e) {
            System.err.println("Usage: BigDataGenerator [count] [output file]");
            System.exit(2);
            return;
        }
        Path out = args.length > 1 ? Path.of(args[1]) : AppPaths.dataDir().resolve(DEFAULT_FILE_NAME);

        long start = System.nanoTime();
        List<SlangEntry> base = loadBundledOriginal();
        List<SlangEntry> all = generate(base, count, DEFAULT_SEED);
        Path target = out.toAbsolutePath();
        Files.createDirectories(target.getParent());
        SlangFileParser.write(target, all);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        System.out.printf("Wrote %,d slang words (%,d original + %,d generated) to %s in %d ms%n",
                all.size(), base.size(), all.size() - base.size(), target, ms);
        System.out.println("To try it in the app: copy this file to data/slang.txt, delete data/dictionary.dat,"
                + " then start the app. Delete both files afterwards to go back to the original data.");
    }

    /**
     * Danh sách gồm toàn bộ {@code base} (giữ nguyên id) rồi thêm entry tổng hợp cho đủ {@code total}.
     *
     * @param total tổng số entry mong muốn, phải ≥ {@code base.size()}
     * @param seed  cùng seed thì cùng kết quả
     */
    public static List<SlangEntry> generate(List<SlangEntry> base, int total, long seed) {
        Objects.requireNonNull(base, "base");
        if (total < base.size()) {
            throw new IllegalArgumentException("total " + total + " is smaller than the base size " + base.size());
        }
        // Giữ cả từ lặp lại: chọn ngẫu nhiên từ danh sách này = chọn theo tần suất thật
        List<String> words = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        int nextId = 0;
        for (SlangEntry entry : base) {
            usedNames.add(Tokenizer.normalizeName(entry.getSlang()));
            for (String meaning : entry.getMeanings()) {
                words.addAll(Tokenizer.tokens(meaning));
            }
            nextId = Math.max(nextId, entry.getId() + 1);
        }
        if (words.isEmpty() && total > base.size()) {
            throw new IllegalArgumentException("base has no words to build meanings from");
        }

        Random random = new Random(seed);
        List<SlangEntry> result = new ArrayList<>(total);
        result.addAll(base);
        while (result.size() < total) {
            String name = uniqueName(random, usedNames);
            int meaningCount = random.nextInt(10) < 8 ? 1 : 2 + random.nextInt(2); // 80% có 1 nghĩa
            List<String> meanings = new ArrayList<>(meaningCount);
            for (int i = 0; i < meaningCount; i++) {
                meanings.add(meaning(random, words));
            }
            result.add(new SlangEntry(nextId++, name, meanings));
        }
        return result;
    }

    /** Tên in hoa ngẫu nhiên chưa ai dùng (so không phân biệt hoa thường). */
    private static String uniqueName(Random random, Set<String> usedNames) {
        while (true) {
            int length = MIN_NAME_LENGTH + random.nextInt(MAX_NAME_LENGTH - MIN_NAME_LENGTH + 1);
            StringBuilder name = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                // ký tự đầu luôn là chữ; các ký tự sau 10% là chữ số
                boolean digit = i > 0 && random.nextInt(10) == 0;
                String alphabet = digit ? DIGITS : LETTERS;
                name.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = name.toString();
            if (usedNames.add(Tokenizer.normalizeName(candidate))) {
                return candidate;
            }
        }
    }

    /** Một câu nghĩa: ghép vài từ thật, viết hoa chữ cái đầu. */
    private static String meaning(Random random, List<String> words) {
        int count = MIN_WORDS_PER_MEANING + random.nextInt(MAX_WORDS_PER_MEANING - MIN_WORDS_PER_MEANING + 1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(words.get(random.nextInt(words.size())));
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /** slang.txt gốc nhúng trong jar (luôn là bản gốc, kể cả khi data/slang.txt đang là file 100k). */
    public static List<SlangEntry> loadBundledOriginal() throws IOException, SlangFormatException {
        try (InputStream in = BigDataGenerator.class.getResourceAsStream(DictionaryStore.BUNDLED_ORIGINAL)) {
            if (in == null) {
                throw new IOException("Bundled resource " + DictionaryStore.BUNDLED_ORIGINAL + " is missing");
            }
            return SlangFileParser.parse(in);
        }
    }
}
