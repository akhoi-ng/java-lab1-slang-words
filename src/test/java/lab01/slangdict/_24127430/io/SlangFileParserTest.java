package lab01.slangdict._24127430.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab01.slangdict._24127430.model.SlangEntry;

class SlangFileParserTest {

    private static List<SlangEntry> parse(String text) throws Exception {
        return SlangFileParser.parse(new BufferedReader(new StringReader(text)));
    }

    private static List<SlangEntry> parseOriginal() throws Exception {
        try (InputStream in = SlangFileParserTest.class.getResourceAsStream("/data/slang.txt")) {
            return SlangFileParser.parse(in);
        }
    }

    @Test
    void originalFileKeepsEverySlangMeaningPair() throws Exception {
        List<SlangEntry> entries = parseOriginal();
        assertEquals(7638, entries.size());

        Set<String> parsedPairs = new HashSet<>();
        int meaningCount = 0;
        for (SlangEntry e : entries) {
            meaningCount += e.getMeanings().size();
            for (String m : e.getMeanings()) {
                parsedPairs.add(e.getSlang() + "\u0000" + m);
            }
        }
        assertEquals(8941, meaningCount);

        // Tính độc lập từ file thô (tách bằng "| ") để chắc chắn parser không làm mất cặp nào
        Set<String> rawPairs = new HashSet<>();
        try (InputStream in = getClass().getResourceAsStream("/data/slang.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int sep = line.indexOf('`');
                for (String m : line.substring(sep + 1).split("\\| ")) {
                    rawPairs.add(line.substring(0, sep) + "\u0000" + m);
                }
            }
        }
        assertEquals(rawPairs, parsedPairs);
    }

    @Test
    void idsFollowFileOrder() throws Exception {
        List<SlangEntry> entries = parseOriginal();
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(i, entries.get(i).getId());
        }
        assertEquals("#1", entries.get(0).getSlang());
    }

    @Test
    void slangMayContainPipe() throws Exception {
        SlangEntry e = parse(":|`Disgust| disapproval| not funny").get(0);
        assertEquals(":|", e.getSlang());
        assertEquals(List.of("Disgust", "disapproval", "not funny"), e.getMeanings());
    }

    @Test
    void readsUtf8AndSkipsHeaderBlankLinesAndBom() throws Exception {
        List<SlangEntry> entries = parse("﻿Slag`Meaning\r\n\r\nELNS`Eller noe sånt\r\n   \r\n$`Dollar| money\r\n");
        assertEquals(2, entries.size());
        assertEquals("Eller noe sånt", entries.get(0).getMeanings().get(0));
        assertEquals(List.of("Dollar", "money"), entries.get(1).getMeanings());
    }

    @Test
    void headerIsOptional() throws Exception {
        assertEquals(1, parse("LOL`Laugh out loud").size());
    }

    @Test
    void malformedLinesReportLineNumber() {
        SlangFormatException noSeparator =
                assertThrows(SlangFormatException.class, () -> parse("Slag`Meaning\nLOL`Laugh\nbroken line"));
        assertEquals(3, noSeparator.getLineNumber());

        assertThrows(SlangFormatException.class, () -> parse("`no slang"));
        assertThrows(SlangFormatException.class, () -> parse("LOL`  |  "));
    }

    @Test
    void writeThenParseRoundTrips(@TempDir Path dir) throws IOException, SlangFormatException {
        List<SlangEntry> original = List.of(
                new SlangEntry(0, ":|", List.of("Disgust", "not funny")),
                new SlangEntry(1, "®", List.of("Registered Trademark Symbol")));
        Path file = dir.resolve("out.txt");
        SlangFileParser.write(file, original);

        assertEquals(SlangFileParser.HEADER, Files.readAllLines(file, StandardCharsets.UTF_8).get(0));
        List<SlangEntry> back = SlangFileParser.parse(file);
        assertEquals(original.size(), back.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).getSlang(), back.get(i).getSlang());
            assertEquals(original.get(i).getMeanings(), back.get(i).getMeanings());
        }
    }
}
