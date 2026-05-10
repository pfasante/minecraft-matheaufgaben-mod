package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class PlusGenerator implements Generator {

    public static final int[] VALID_RANGES = {10, 20, 100, 1000};
    private static final Set<String> KNOWN_PARAMS = Set.of("range", "count", "carry");

    public record PlusParams(int range, int count, CarryMode carry) {}

    private record CarryKey(int range, CarryMode mode) {}

    /**
     * Exact (a, b) pair-count table per (range, carry mode), populated at class load.
     * The mixed-mode capacity is closed-form ((n+1)(n+2)/2) and computed inline; the
     * yes/no modes need enumeration because closed-form heuristics overestimate for
     * small ranges and break the fail-fast contract.
     */
    private static final Map<CarryKey, Integer> CARRY_CAPACITY = computeCarryCapacities();

    @Override
    public String name() { return "plus"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("plus: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("range")) throw new ConfigException("plus: missing required param 'range'");
        if (!raw.containsKey("count")) throw new ConfigException("plus: missing required param 'count'");
        int range;
        try {
            range = Integer.parseInt(raw.get("range"));
        } catch (NumberFormatException e) {
            throw new ConfigException("plus: range must be int, got '" + raw.get("range") + "'");
        }
        boolean validRange = false;
        for (int r : VALID_RANGES) if (r == range) { validRange = true; break; }
        if (!validRange) {
            throw new ConfigException(
                    "plus: range must be one of " + Arrays.toString(VALID_RANGES) + ", got " + range);
        }
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("plus: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("plus: count must be >= 1, got " + count);
        CarryMode carry = raw.containsKey("carry") ? CarryMode.parse(raw.get("carry")) : CarryMode.MIXED;
        PlusParams params = new PlusParams(range, count, carry);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "plus: count=" + count + " exceeds capacity " + capacity
                            + " for range=" + range + ", carry=" + carry.name().toLowerCase());
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof PlusParams params)) {
            throw new IllegalArgumentException("plus: expected PlusParams, got " + paramsObj);
        }
        Set<Long> seen = new HashSet<>();
        List<Problem> problems = new ArrayList<>(params.count);
        int maxAttempts = Math.max(1000, 200 * params.count);
        for (int i = 0; i < maxAttempts && problems.size() < params.count; i++) {
            int a = rng.nextInt(params.range + 1);
            int b = rng.nextInt(params.range + 1 - a);
            long key = ((long) a << 32) | b;
            if (!seen.add(key)) continue;
            if (!carryMatches(a, b, params.carry)) continue;
            problems.add(new Problem(a + " + " + b, Integer.toString(a + b)));
        }
        if (problems.size() < params.count) {
            throw new ConfigException(
                    "plus: could not generate " + params.count + " unique problems "
                            + "under constraints (got " + problems.size() + ")");
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                plus — addition
                  range: 10|20|100|1000  (required)
                  count: int             (required, >=1)
                  carry: yes|no|mixed    (default mixed)
                """;
    }

    private static boolean hasCarry(int a, int b) {
        while (a > 0 || b > 0) {
            if ((a % 10) + (b % 10) >= 10) return true;
            a /= 10; b /= 10;
        }
        return false;
    }

    private static boolean carryMatches(int a, int b, CarryMode mode) {
        if (mode == CarryMode.MIXED) return true;
        boolean has = hasCarry(a, b);
        return mode == CarryMode.YES ? has : !has;
    }

    private static int capacity(PlusParams params) {
        int n = params.range;
        if (params.carry == CarryMode.MIXED) {
            return (n + 1) * (n + 2) / 2;
        }
        return CARRY_CAPACITY.get(new CarryKey(n, params.carry));
    }

    private static Map<CarryKey, Integer> computeCarryCapacities() {
        Map<CarryKey, Integer> out = new HashMap<>();
        for (int r : VALID_RANGES) {
            int yes = 0, no = 0;
            for (int a = 0; a <= r; a++) {
                for (int b = 0; b <= r - a; b++) {
                    if (hasCarry(a, b)) yes++; else no++;
                }
            }
            out.put(new CarryKey(r, CarryMode.YES), yes);
            out.put(new CarryKey(r, CarryMode.NO), no);
        }
        return out;
    }
}
