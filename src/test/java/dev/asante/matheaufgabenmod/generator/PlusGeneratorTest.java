package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PlusGeneratorTest {

    private static final Pattern PROMPT = Pattern.compile("(\\d+) \\+ (\\d+)");

    private record AB(int a, int b) {}

    private static AB parsePrompt(String prompt) {
        Matcher m = PROMPT.matcher(prompt);
        assertTrue(m.matches(), "unexpected prompt: " + prompt);
        return new AB(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private static boolean hasCarry(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) + (b % 10) >= 10) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private final PlusGenerator gen = new PlusGenerator();

    @Test
    void countExact() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "10"));
        assertEquals(10, gen.generate(new Random(42), p).size());
    }

    @Test
    void rangeRespected() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(0 <= ab.a && ab.a <= 100);
            assertTrue(0 <= ab.b && ab.b <= 100);
            assertTrue(ab.a + ab.b <= 100);
        }
    }

    @Test
    void correctness() {
        Object p = gen.parseParams(Map.of("range", "1000", "count", "30"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertEquals(Integer.toString(ab.a + ab.b), prob.answer());
        }
    }

    @Test
    void carryYes() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "carry", "yes"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertTrue(hasCarry(ab.a, ab.b), ab.a + " + " + ab.b + " should carry");
        }
    }

    @Test
    void carryNo() {
        Object p = gen.parseParams(Map.of("range", "100", "count", "30", "carry", "no"));
        for (Problem prob : gen.generate(new Random(42), p)) {
            AB ab = parsePrompt(prob.prompt());
            assertFalse(hasCarry(ab.a, ab.b), ab.a + " + " + ab.b + " should not carry");
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
    void countExceedsCapacityRaisesInParseParams() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "9999", "carry", "no")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void carryYesTightCapacityCaughtInParseParams() {
        // range=10 carry=yes admits only 9 distinct (a, b) pairs (enumerated:
        // 1+9, 2+8, 2+9, 3+7, 3+8, 3+9, 4+6, 4+7, 4+8 — and stops there because
        // a+b must also be <= 10). parseParams must reject count=10 *before*
        // generate runs; a closed-form heuristic of total/2 = 33 would let
        // generate fail at runtime.
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "10", "count", "10", "carry", "yes")));
        assertTrue(ex.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsUnknownParam() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "bogus", "x")));
        assertTrue(ex.getMessage().contains("unknown"));
    }

    @Test
    void rejectsInvalidRange() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "50", "count", "5")));
        assertTrue(ex.getMessage().contains("range"));
    }

    @Test
    void rejectsInvalidCarry() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> gen.parseParams(Map.of("range", "100", "count", "5", "carry", "maybe")));
        assertTrue(ex.getMessage().contains("carry"));
    }

    @Test
    void fullCapacityYieldsAllValidPairsExactlyOnce() {
        // At capacity, the shuffle+take-first-N becomes a permutation of the full
        // candidate set — every valid pair appears exactly once. This is a
        // definitive proof of uniform coverage (no pair over- or under-represented).
        Object p = gen.parseParams(Map.of("range", "10", "count", "66"));  // 66 = capacity for mixed
        List<Problem> problems = gen.generate(new Random(42), p);
        Set<String> prompts = new HashSet<>();
        for (Problem prob : problems) prompts.add(prob.prompt());
        assertEquals(66, prompts.size(), "every valid pair should appear exactly once at capacity");

        // Spot-check that both orderings of the same numbers appear (commutative balance).
        assertTrue(prompts.contains("3 + 7"));
        assertTrue(prompts.contains("7 + 3"));
        assertTrue(prompts.contains("0 + 10"));
        assertTrue(prompts.contains("10 + 0"));
    }

    @Test
    void empiricalDistributionIsApproximatelyUniform() {
        // Sample 1000 problems at range=10 (66 valid pairs). With true uniform
        // sampling each pair is expected ~15 times. Bound the deviation generously
        // to allow for legitimate sampling variance but catch any 5x-style bias
        // like the one the old nested-rejection algorithm produced.
        Object p = gen.parseParams(Map.of("range", "10", "count", "1"));
        Map<String, Integer> counts = new HashMap<>();
        Random rng = new Random(42);
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            Problem prob = gen.generate(rng, p).get(0);
            counts.merge(prob.prompt(), 1, Integer::sum);
        }
        double expected = trials / 66.0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            // Allow each pair to deviate up to 3x the expected count.
            // Old biased algorithm produced 6x for "10 + 0" — this catches that.
            assertTrue(e.getValue() < expected * 3,
                    "pair " + e.getKey() + " appeared " + e.getValue()
                            + " times (expected ~" + expected + "); old algorithm would be ~6x here");
        }
    }
}
