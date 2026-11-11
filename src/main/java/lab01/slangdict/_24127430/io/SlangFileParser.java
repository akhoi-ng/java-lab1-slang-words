package lab01.slangdict._24127430.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lab01.slangdict._24127430.model.SlangEntry;

/**
 * Đọc/ghi file định dạng gốc của đề bài (UTF-8):
 * <pre>
 * Slag`Meaning                      &lt;- header (tuỳ chọn)
 * $`Dollar| money                   &lt;- slang`nghĩa1| nghĩa2
 * :|`Disgust| disapproval| not funny
 * </pre>
 * Lưu ý: tên slang có thể chứa '|' (":|", "8-|") nên chỉ tách tên ở dấu '`' ĐẦU TIÊN,
 * còn '|' chỉ dùng để tách các nghĩa ở phần sau.
 * <p>
 * Dòng không có dấu '`' là nghĩa bổ sung của slang ở dòng trước. File gốc của đề có 5 dòng
 * như vậy, ví dụ:
 * <pre>
 * AMA`Ask Me Anything
 * Against Medical Advice            &lt;- thành nghĩa thứ hai của AMA
 * </pre>
 */
public final class SlangFileParser {

    public static final String HEADER = "Slag`Meaning";
    public static final char SLANG_SEPARATOR = '`';
    public static final String MEANING_SEPARATOR = "| ";

    private static final String MEANING_SPLIT_REGEX = "\\|";
    private static final char BOM = '﻿';

    private SlangFileParser() {
    }

    public static List<SlangEntry> parse(Path file) throws IOException, SlangFormatException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static List<SlangEntry> parse(InputStream in) throws IOException, SlangFormatException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parse(reader);
        }
    }

    /**
     * Đọc toàn bộ entry; id được đánh theo thứ tự xuất hiện, bắt đầu từ 0.
     * Bỏ qua dòng trống và dòng header. Dòng không có '`' được thêm vào nghĩa của entry trước
     * (bỏ nghĩa đã có).
     * <p>
     * Dòng sai định dạng (không có tên slang, không có nghĩa nào, hoặc không có '`' mà chưa có entry nào trước nó)
     * bị <b>bỏ qua</b> và ghi ra stderr: một dòng hỏng trong file 100k dòng không được làm cả app không mở được.
     * Dòng như vậy không chứa cặp (slang, nghĩa) nào nên không mất dữ liệu. Chỉ khi file có dòng lỗi mà
     * <b>không có dòng hợp lệ nào</b> (không phải file slang) mới ném {@link SlangFormatException} của dòng lỗi đầu tiên.
     */
    public static List<SlangEntry> parse(BufferedReader reader) throws IOException, SlangFormatException {
        List<SlangEntry> entries = new ArrayList<>();
        SlangFormatException firstProblem = null;
        int skipped = 0;
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == BOM) {
                line = line.substring(1);
            }
            // Header chỉ được nhận khi chưa có entry nào (có thể đứng sau vài dòng trống), không chỉ ở dòng 1
            if (line.isBlank() || (entries.isEmpty() && line.strip().equalsIgnoreCase(HEADER))) {
                continue;
            }
            if (line.indexOf(SLANG_SEPARATOR) < 0 && !entries.isEmpty()) {
                int last = entries.size() - 1;
                entries.set(last, withExtraMeanings(entries.get(last), line));
                continue;
            }
            try {
                entries.add(parseLine(line, entries.size(), lineNumber));
            } catch (SlangFormatException e) {
                skipped++;
                if (firstProblem == null) {
                    firstProblem = e;
                }
            }
        }
        if (firstProblem != null) {
            if (entries.isEmpty()) {
                throw firstProblem;
            }
            System.err.println("[SlangFileParser] skipped " + skipped + " malformed line(s), first: "
                    + firstProblem.getMessage());
        }
        return entries;
    }

    /** Dòng nối tiếp: thêm các nghĩa của dòng vào cuối entry, bỏ nghĩa đã có. */
    private static SlangEntry withExtraMeanings(SlangEntry entry, String line) {
        List<String> meanings = new ArrayList<>(entry.getMeanings());
        for (String meaning : splitMeanings(line)) {
            if (!meanings.contains(meaning)) {
                meanings.add(meaning);
            }
        }
        return meanings.size() == entry.getMeanings().size() ? entry : entry.withMeanings(meanings);
    }

    /** Tách một dòng "slang`nghĩa1| nghĩa2" thành SlangEntry. */
    static SlangEntry parseLine(String line, int id, int lineNumber) throws SlangFormatException {
        int separator = line.indexOf(SLANG_SEPARATOR);
        if (separator < 0) {
            throw new SlangFormatException(lineNumber, "missing '" + SLANG_SEPARATOR + "' separator");
        }
        String slang = line.substring(0, separator).strip();
        if (slang.isEmpty()) {
            throw new SlangFormatException(lineNumber, "empty slang word");
        }

        List<String> meanings = splitMeanings(line.substring(separator + 1));
        if (meanings.isEmpty()) {
            throw new SlangFormatException(lineNumber, "slang '" + slang + "' has no meaning");
        }
        return new SlangEntry(id, slang, meanings);
    }

    /** "nghĩa1| nghĩa2" -> [nghĩa1, nghĩa2], cắt khoảng trắng, bỏ phần rỗng. */
    private static List<String> splitMeanings(String text) {
        List<String> meanings = new ArrayList<>();
        for (String part : text.split(MEANING_SPLIT_REGEX)) {
            String meaning = part.strip();
            if (!meaning.isEmpty()) {
                meanings.add(meaning);
            }
        }
        return meanings;
    }

    /** Ngược lại với parseLine: SlangEntry -> "slang`nghĩa1| nghĩa2". */
    public static String formatLine(SlangEntry entry) {
        return entry.getSlang() + SLANG_SEPARATOR + String.join(MEANING_SEPARATOR, entry.getMeanings());
    }

    /** Ghi danh sách entry ra file (có header), dùng cho tool sinh dữ liệu lớn / export. */
    public static void write(Path file, Iterable<SlangEntry> entries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (SlangEntry entry : entries) {
                writer.write(formatLine(entry));
                writer.newLine();
            }
        }
    }
}
