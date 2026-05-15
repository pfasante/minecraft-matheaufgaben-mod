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

    private static final String PLAYER = "TestPlayer";

    @Test
    void writesHeaderAndOneRowWhenFileMissing(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "7", true, Duration.ofMillis(2300)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "header + 1 row");
        // Header is fixed-width with 2-space separators; check by splitting on
        // 2-or-more whitespace so we don't pin exact padding.
        String[] hdr = lines.get(0).split("\\s{2,}");
        assertArrayEquals(
                new String[]{"timestamp", "player", "type", "prompt", "expected", "given", "result", "duration_s"},
                hdr);
        String[] cols = lines.get(1).split("\\s{2,}");
        assertEquals(8, cols.length);
        assertEquals(PLAYER, cols[1]);
        assertEquals("plus", cols[2]);
        assertEquals("3 + 4", cols[3]);
        assertEquals("7", cols[4]);
        assertEquals("7", cols[5]);
        assertEquals("correct", cols[6]);
        assertEquals("2.30", cols[7]);
    }

    @Test
    void appendsWithoutRewritingHeader(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "8", false, Duration.ofMillis(1500)));
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "7", true, Duration.ofMillis(900)));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size(), "header + 2 rows");
        assertTrue(lines.get(1).contains(" wrong "));
        assertTrue(lines.get(2).contains(" correct "));
    }

    @Test
    void inferTypeFromOperator() {
        assertEquals("plus", HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "7", true, Duration.ZERO).type());
        assertEquals("minus", HistoryEntry.fromAttempt(
                new Problem("10 − 3", "7"), PLAYER, "7", true, Duration.ZERO).type());
        assertEquals("einmaleins", HistoryEntry.fromAttempt(
                new Problem("7 · 8", "56"), PLAYER, "56", true, Duration.ZERO).type());
        assertEquals("division", HistoryEntry.fromAttempt(
                new Problem("12 : 4", "3"), PLAYER, "3", true, Duration.ZERO).type());
    }

    @Test
    void unknownTypeForUnrecognisedPrompt() {
        assertEquals("unknown", HistoryEntry.fromAttempt(
                new Problem("???", "x"), PLAYER, "x", true, Duration.ZERO).type());
    }

    @Test
    void trimsGivenAnswer() {
        HistoryEntry entry = HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "  7  ", true, Duration.ofMillis(500));
        assertEquals("7", entry.given());
    }

    @Test
    void durationFormattingTwoDecimals(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), PLAYER, "7", true, Duration.ofMillis(12345)));
        List<String> lines = Files.readAllLines(file);
        String[] cols = lines.get(1).split("\\s{2,}");
        assertEquals("12.35", cols[7]);  // 12345ms → 12.345s → "12.35"
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
                new Problem("3 + 4", "7"), PLAYER, "7", true, Duration.ofMillis(100))));
    }

    @Test
    void playerNameAppearsInRow(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("matheaufgabenmod-history.log");
        HistoryLogger logger = new HistoryLogger(file);
        logger.logAttempt(HistoryEntry.fromAttempt(
                new Problem("3 + 4", "7"), "KidAccount", "7", true, Duration.ofMillis(2000)));
        List<String> lines = Files.readAllLines(file);
        String[] cols = lines.get(1).split("\\s{2,}");
        assertEquals("KidAccount", cols[1], "player name should appear in column 1 (after timestamp)");
    }
}
