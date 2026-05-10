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

class EinmaleinsGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) · (\\d+)");  // U+00B7 middle dot

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private final EinmaleinsGenerator gen = new EinmaleinsGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void correctness() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a * ab.b), prob.answer());
        }
    }

    @Test
    void factorConstraintRange() {
        Object p = gen.parseParams(Map.of("rows", "2-5", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(Set.of(2, 3, 4, 5).contains(ab.a));
            assertTrue(1 <= ab.b && ab.b <= 10);
        }
    }

    @Test
    void factorConstraintList() {
        Object p = gen.parseParams(Map.of("rows", "3,7", "count", "20"));
        Set<Integer> rowsSeen = new HashSet<>();
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            rowsSeen.add(ab.a);
        }
        assertTrue(Set.of(3, 7).containsAll(rowsSeen));
    }

    @Test
    void rowsAll() {
        Object p = gen.parseParams(Map.of("rows", "all", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(1 <= ab.a && ab.a <= 10);
            assertTrue(1 <= ab.b && ab.b <= 10);
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "40"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void distinctOrderings() {
        Object p = gen.parseParams(Map.of("rows", "7", "count", "10"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(7, ab.a);
        }
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("rows", "2-9", "count", "20"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaises() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("rows", "3", "count", "11")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidRows() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("rows", "x-y", "count", "5")));
        assertTrue(ex.getMessage().contains("rows"));
    }
}
