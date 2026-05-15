package dev.asante.matheaufgabenmod.history;

import dev.asante.matheaufgabenmod.generator.Problem;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the math-task history log. Constructed via {@link #fromAttempt} which
 * stamps the current wall-clock time and derives the generator type by inspecting
 * the prompt for the operator character. The {@code player} field carries the
 * Minecraft username at attempt time so a shared machine can distinguish which
 * kid was playing — the caller (PromptScreen) reads it from the production-side
 * Minecraft client and passes it in.
 */
public record HistoryEntry(
        OffsetDateTime timestamp,
        String player,
        String type,
        String prompt,
        String expected,
        String given,
        boolean correct,
        Duration duration
) {

    public static HistoryEntry fromAttempt(Problem problem, String player, String given,
                                           boolean correct, Duration duration) {
        return new HistoryEntry(
                OffsetDateTime.now(),
                player,
                inferType(problem.prompt()),
                problem.prompt(),
                problem.answer(),
                given.trim(),
                correct,
                duration
        );
    }

    /**
     * Infer the generator name from the prompt's operator character. Hard-coded
     * rather than carried on {@link Problem} to keep this feature non-invasive.
     */
    private static String inferType(String prompt) {
        if (prompt.contains(" + ")) return "plus";
        if (prompt.contains(" − ")) return "minus";        // U+2212
        if (prompt.contains(" · ")) return "einmaleins";   // U+00B7
        if (prompt.contains(" : ")) return "division";
        return "unknown";
    }

    /**
     * Fixed-width space-aligned row for human-readable logs. Column widths match
     * {@link HistoryLogger#HEADER} so the header and rows visually align. Fields
     * are separated by two spaces; the prompt's internal single spaces survive
     * so {@code awk -F'\s{2,}'} reads it cleanly.
     */
    public String toLine() {
        String ts = timestamp.format(LOCAL_TIMESTAMP);
        double seconds = duration.toMillis() / 1000.0;
        return String.format(java.util.Locale.ROOT,
                "%-19s  %-16s  %-10s  %-13s  %-9s  %-9s  %-7s  %.2f",
                ts,
                player,
                type,
                prompt,
                expected,
                given,
                correct ? "correct" : "wrong",
                seconds);
    }

    private static final DateTimeFormatter LOCAL_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
}
