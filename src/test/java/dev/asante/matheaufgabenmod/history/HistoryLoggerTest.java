package dev.asante.matheaufgabenmod.history;

import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryLoggerTest {

    @Test
    void writesHeaderAndOneRowWhenFileMissing(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(2300)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "header + 1 row");
        assertEquals("timestamp\ttype\tprompt\texpected\tgiven\tresult\tduration_s", lines.get(0));
        String[] cols = lines.get(1).split("\t");
        assertEquals(7, cols.length);
        assertEquals("plus", cols[1]);
        assertEquals("3 + 4", cols[2]);
        assertEquals("7", cols[3]);
        assertEquals("7", cols[4]);
        assertEquals("correct", cols[5]);
        assertEquals("2.30", cols[6]);
    }

    @Test
    void appendsWithoutRewritingHeader(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "8", false, Duration.ofMillis(1500)));
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(900)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size(), "header + 2 rows");
        assertTrue(lines.get(1).contains("\twrong\t"));
        assertTrue(lines.get(2).contains("\tcorrect\t"));
    }

    @Test
    void inferTypeFromOperator() {
        assertEquals("plus", HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ZERO).type());
        assertEquals("minus", HistoryEntry.fromAttempt(
                new Problem("10 − 3", "7"), "7", true, Duration.ZERO).type());
        assertEquals("einmaleins", HistoryEntry.fromAttempt(
                new Problem("7 · 8", "56"), "56", true, Duration.ZERO).type());
        assertEquals("division", HistoryEntry.fromAttempt(
                new Problem("12 : 4", "3"), "3", true, Duration.ZERO).type());
    }

    @Test
    void unknownTypeForUnrecognisedPrompt() {
        assertEquals("unknown", HistoryEntry.fromAttempt(
                new Problem("???", "x"), "x", true, Duration.ZERO).type());
    }

    @Test
    void trimsGivenAnswer() {
        HistoryEntry entry = HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "  7  ", true, Duration.ofMillis(500));
        assertEquals("7", entry.given());
    }

    @Test
    void durationFormattingTwoDecimals(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(12345)));
        List<String> lines = Files.readAllLines(file);
        String[] cols = lines.get(1).split("\t");
        assertEquals("12.35", cols[6]);  // 12345ms → 12.345s → "12.35"
    }

    @Test
    void ioErrorDoesNotPropagate(@TempDir Path tmp) {
        // Use a path under a regular file — directory creation will fail.
        Path notADir = tmp.resolve("blocker");
        try {
            Files.writeString(notADir, "blocker");
        } catch (IOException e) {
            fail("setup");
        }
        Path file = notADir.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        assertDoesNotThrow(() -> logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "7", true, Duration.ofMillis(100))));
    }
}
