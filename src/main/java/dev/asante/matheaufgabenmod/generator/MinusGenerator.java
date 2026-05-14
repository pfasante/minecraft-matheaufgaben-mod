package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class MinusGenerator implements Generator {

    public static final int[] VALID_RANGES = {10, 20, 100, 1000};
    private static final Set<String> KNOWN_PARAMS =
            Set.of("range", "count", "borrow", "negative_results");

    private static final String MINUS_SIGN = "−";  // U+2212

    public record MinusParams(int range, int count, BorrowMode borrow, boolean negativeResults) {}

    private record BorrowKey(int range, BorrowMode mode, boolean negativeResults) {}

    private static final Map<BorrowKey, Integer> BORROW_CAPACITY = computeBorrowCapacities();

    @Override
    public String name() { return "minus"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("minus: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("range")) throw new ConfigException("minus: missing required param 'range'");
        if (!raw.containsKey("count")) throw new ConfigException("minus: missing required param 'count'");
        int range;
        try {
            range = Integer.parseInt(raw.get("range"));
        } catch (NumberFormatException e) {
            throw new ConfigException("minus: range must be int, got '" + raw.get("range") + "'");
        }
        boolean validRange = false;
        for (int r : VALID_RANGES) if (r == range) { validRange = true; break; }
        if (!validRange) {
            throw new ConfigException(
                    "minus: range must be one of " + Arrays.toString(VALID_RANGES) + ", got " + range);
        }
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("minus: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("minus: count must be >= 1, got " + count);
        BorrowMode borrow = raw.containsKey("borrow") ? BorrowMode.parse(raw.get("borrow")) : BorrowMode.MIXED;
        boolean neg = parseBool(raw.getOrDefault("negative_results", "false"), "negative_results");
        MinusParams params = new MinusParams(range, count, borrow, neg);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "minus: count=" + count + " exceeds capacity " + capacity
                            + " for range=" + range + ", borrow=" + borrow.name().toLowerCase());
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof MinusParams params)) {
            throw new IllegalArgumentException("minus: expected MinusParams, got " + paramsObj);
        }
        // Enumerate every valid (a, b) pair, shuffle, take first `count`.
        // Provably uniform via Fisher-Yates. With negative_results=false the pair
        // space is the lower triangle (a >= b); with =true it's the full square.
        // parseParams already guarantees count <= pairs.size().
        List<int[]> pairs = new ArrayList<>();
        for (int a = 0; a <= params.range; a++) {
            int bMax = params.negativeResults ? params.range : a;
            for (int b = 0; b <= bMax; b++) {
                if (borrowMatches(a, b, params.borrow)) {
                    pairs.add(new int[]{a, b});
                }
            }
        }
        Collections.shuffle(pairs, rng);
        List<Problem> problems = new ArrayList<>(params.count);
        for (int i = 0; i < params.count; i++) {
            int a = pairs.get(i)[0];
            int b = pairs.get(i)[1];
            problems.add(new Problem(a + " " + MINUS_SIGN + " " + b, Integer.toString(a - b)));
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                minus — subtraction
                  range: 10|20|100|1000     (required)
                  count: int                (required, >=1)
                  borrow: yes|no|mixed      (default mixed)
                  negative_results: bool    (default false; if false, a >= b)
                """;
    }

    private static boolean parseBool(String raw, String name) {
        String lower = raw.toLowerCase();
        if (lower.equals("true") || lower.equals("yes") || lower.equals("1")) return true;
        if (lower.equals("false") || lower.equals("no") || lower.equals("0")) return false;
        throw new ConfigException("minus: " + name + " must be true|false, got '" + raw + "'");
    }

    private static boolean hasBorrow(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) < (b % 10)) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private static boolean borrowMatches(int a, int b, BorrowMode mode) {
        if (mode == BorrowMode.MIXED) return true;
        boolean has = hasBorrow(a, b);
        return mode == BorrowMode.YES ? has : !has;
    }

    private static int capacity(MinusParams params) {
        int n = params.range;
        if (params.borrow == BorrowMode.MIXED) {
            return params.negativeResults ? (n + 1) * (n + 1) : (n + 1) * (n + 2) / 2;
        }
        return BORROW_CAPACITY.get(new BorrowKey(n, params.borrow, params.negativeResults));
    }

    private static Map<BorrowKey, Integer> computeBorrowCapacities() {
        Map<BorrowKey, Integer> out = new HashMap<>();
        for (int r : VALID_RANGES) {
            for (boolean neg : new boolean[]{false, true}) {
                int yes = 0, no = 0;
                int aMax = r;
                for (int a = 0; a <= aMax; a++) {
                    int bMax = neg ? r : a;
                    for (int b = 0; b <= bMax; b++) {
                        if (hasBorrow(a, b)) yes++; else no++;
                    }
                }
                out.put(new BorrowKey(r, BorrowMode.YES, neg), yes);
                out.put(new BorrowKey(r, BorrowMode.NO, neg), no);
            }
        }
        return out;
    }
}
