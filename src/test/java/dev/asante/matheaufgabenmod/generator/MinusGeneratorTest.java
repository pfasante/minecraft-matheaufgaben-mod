package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MinusGeneratorTest {

    // U+2212 MINUS SIGN, not ASCII hyphen.
    private static final Pattern PROMPT = Pattern.compile("(\\d+) − (\\d+)");

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static boolean hasBorrow(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) < (b % 10)) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private final MinusGenerator gen = new MinusGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void nonNegativeByDefault() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(ab.a >= ab.b);
            assertEquals(Integer.toString(ab.a - ab.b), prob.answer());
        }
    }

    @Test
    void rangeRespected() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(0 <= ab.a && ab.a <= 100);
            assertTrue(0 <= ab.b && ab.b <= 100);
        }
    }

    @Test
    void borrowYes() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "borrow", "yes"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(hasBorrow(ab.a, ab.b));
        }
    }

    @Test
    void borrowNo() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "borrow", "no"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertFalse(hasBorrow(ab.a, ab.b));
        }
    }

    @Test
    void negativeResultsAllowed() {
        Object p = gen.parseParams(
                Map.of("range", "100", "count", "30", "negative_results", "true"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a - ab.b), prob.answer());
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "50"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaisesInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "9999", "borrow", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void borrowYesTightCapacityCaughtInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "100", "borrow", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidRange() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "50", "count", "5")));
        assertTrue(ex.getMessage().contains("range"));
    }

    @Test
    void rejectsInvalidBorrow() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "borrow", "maybe")));
        assertTrue(ex.getMessage().contains("borrow"));
    }
}
