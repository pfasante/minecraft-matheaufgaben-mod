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

class DivisionGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) : (\\d+)");
    private static final Pattern ANSWER = Pattern.compile("(\\d+)(?: R (\\d+))?");

    private record AB(int a, int b) {}
    private record QR(int q, int r) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static QR parseAnswer(String answer) {
        Matcher m = ANSWER.matcher(answer);
        assertTrue(m.matches(), "unexpected answer: " + answer);
        int q = Integer.parseInt(m.group(1));
        int r = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        return new QR(q, r);
    }

    private final DivisionGenerator gen = new DivisionGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void cleanWhenNoRemainder() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            QR qr = parseAnswer(prob.answer());
            assertEquals(0, qr.r);
            assertEquals(ab.a, ab.b * qr.q);
        }
    }

    @Test
    void withRemainderConsistent() {
        Object p = gen.parseParams(
                Map.of("divisor", "2-9", "count", "30", "with_remainder", "true"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            QR qr = parseAnswer(prob.answer());
            assertTrue(0 <= qr.r && qr.r < ab.b);
            assertEquals(ab.a, ab.b * qr.q + qr.r);
        }
    }

    @Test
    void divisorInRange() {
        Object p = gen.parseParams(Map.of("divisor", "3-5", "count", "20"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(Set.of(3, 4, 5).contains(ab.b));
        }
    }

    @Test
    void quotientBoundedByResultMax() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "20", "result_max", "5"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            QR qr = parseAnswer(prob.answer());
            assertTrue(1 <= qr.q && qr.q <= 5);
        }
    }

    @Test
    void uniqueness() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "40"));
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(problems.size(), prompts.size());
    }

    @Test
    void determinism() {
        Object p = gen.parseParams(Map.of("divisor", "2-9", "count", "20"));
        assertEquals(gen.generate(new Random(42), p), gen.generate(new Random(42), p));
    }

    @Test
    void capacityOverrunRaises() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("divisor", "2", "count", "10", "result_max", "3")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsInvalidDivisor() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("divisor", "0-9", "count", "5")));
        assertTrue(ex.getMessage().contains("divisor"));
    }
}
