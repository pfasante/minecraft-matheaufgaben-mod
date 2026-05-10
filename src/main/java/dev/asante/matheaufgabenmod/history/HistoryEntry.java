package dev.asante.matheaufgabenmod.history;

import dev.asante.matheaufgabenmod.generator.Problem;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the math-task history log. Constructed via {@link #fromAttempt} which
 * stamps the current wall-clock time and derives the generator type by inspecting
 * the prompt for the operator character.
 */
public record HistoryEntry(
        OffsetDateTime timestamp,
        String type,
        String prompt,
        String expected,
        String given,
        boolean correct,
        Duration duration
) {

    public static HistoryEntry fromAttempt(Problem problem, String given, boolean correct, Duration duration) {
        return new HistoryEntry(
                OffsetDateTime.now(),
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

    /** Tab-separated row. The header row is hard-coded in {@link HistoryLogger}. */
    public String toTsv() {
        double seconds = duration.toMillis() / 1000.0;
        return String.format(java.util.Locale.ROOT,
                "%s\t%s\t%s\t%s\t%s\t%s\t%.2f",
                timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                type,
                prompt,
                expected,
                given,
                correct ? "correct" : "wrong",
                seconds);
    }
}
