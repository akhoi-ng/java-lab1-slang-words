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
     * Bỏ qua dòng trống và dòng header.
     */
    public static List<SlangEntry> parse(BufferedReader reader) throws IOException, SlangFormatException {
        List<SlangEntry> entries = new ArrayList<>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == BOM) {
                line = line.substring(1);
            }
            if (line.isBlank() || (lineNumber == 1 && line.strip().equalsIgnoreCase(HEADER))) {
                continue;
            }
            entries.add(parseLine(line, entries.size(), lineNumber));
        }
        return entries;
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

        List<String> meanings = new ArrayList<>();
        for (String part : line.substring(separator + 1).split(MEANING_SPLIT_REGEX)) {
            String meaning = part.strip();
            if (!meaning.isEmpty()) {
                meanings.add(meaning);
            }
        }
        if (meanings.isEmpty()) {
            throw new SlangFormatException(lineNumber, "slang '" + slang + "' has no meaning");
        }
        return new SlangEntry(id, slang, meanings);
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
