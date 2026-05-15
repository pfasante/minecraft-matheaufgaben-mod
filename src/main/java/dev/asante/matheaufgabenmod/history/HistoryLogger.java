package dev.asante.matheaufgabenmod.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only history log writer. Lazily creates the file on first call,
 * writing a fixed-width header row before any data. On {@link IOException},
 * logs to SLF4J and swallows — a failed log must not crash the prompt flow.
 */
public final class HistoryLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("matheaufgabenmod");
    static final String HEADER = String.format(
            "%-19s  %-16s  %-10s  %-13s  %-9s  %-9s  %-7s  %s",
            "timestamp", "player", "type", "prompt", "expected", "given", "result", "duration_s");

    private final Path file;

    public HistoryLogger(Path file) {
        this.file = file;
    }

    public void logAttempt(HistoryEntry entry) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean writeHeader = !Files.exists(file) || Files.size(file) == 0;
            String line = (writeHeader ? HEADER + "\n" : "") + entry.toLine() + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("[matheaufgabenmod] could not append to history log {}: {}",
                    file, e.getMessage());
        }
    }
}
