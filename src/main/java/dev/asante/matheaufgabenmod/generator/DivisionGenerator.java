package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DivisionGenerator implements Generator {

    private static final Set<String> KNOWN_PARAMS =
            Set.of("divisor", "count", "with_remainder", "result_max");
    private static final String DIV_SIGN = ":";

    public record DivisionParams(int[] divisors, int count, boolean withRemainder, int resultMax) {}

    @Override
    public String name() { return "division"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("division: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("divisor")) throw new ConfigException("division: missing required param 'divisor'");
        if (!raw.containsKey("count")) throw new ConfigException("division: missing required param 'count'");
        int[] divisors = parseDivisor(raw.get("divisor"));
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("division: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("division: count must be >= 1, got " + count);
        boolean withRemainder = parseBool(raw.getOrDefault("with_remainder", "false"), "with_remainder");
        int resultMax;
        try {
            resultMax = Integer.parseInt(raw.getOrDefault("result_max", "10"));
        } catch (NumberFormatException e) {
            throw new ConfigException("division: result_max must be int, got '" + raw.get("result_max") + "'");
        }
        if (resultMax < 1) throw new ConfigException("division: result_max must be >= 1, got " + resultMax);
        DivisionParams params = new DivisionParams(divisors, count, withRemainder, resultMax);
        int capacity = capacity(params);
        if (count > capacity) {
            throw new ConfigException(
                    "division: count=" + count + " exceeds capacity " + capacity
                            + " for divisor=" + raw.get("divisor")
                            + ", result_max=" + resultMax + ", with_remainder=" + withRemainder);
        }
        return params;
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof DivisionParams params)) {
            throw new IllegalArgumentException("division: expected DivisionParams, got " + paramsObj);
        }
        List<int[]> all = new ArrayList<>();  // (d, q, r)
        for (int d : params.divisors) {
            for (int q = 1; q <= params.resultMax; q++) {
                if (params.withRemainder) {
                    for (int r = 0; r < d; r++) {
                        all.add(new int[]{d, q, r});
                    }
                } else {
                    all.add(new int[]{d, q, 0});
                }
            }
        }
        Collections.shuffle(all, rng);
        List<Problem> problems = new ArrayList<>(params.count);
        for (int i = 0; i < params.count; i++) {
            int d = all.get(i)[0];
            int q = all.get(i)[1];
            int r = all.get(i)[2];
            int dividend = d * q + r;
            String answer = r == 0 ? Integer.toString(q) : q + " R " + r;
            problems.add(new Problem(dividend + " " + DIV_SIGN + " " + d, answer));
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                division — basic division
                  divisor: range '2-9' | list '2,3' | single '7'  (required, all values >=1)
                  count: int                                       (required, >=1)
                  with_remainder: bool                             (default false)
                  result_max: int                                  (default 10; cap on quotient)
                """;
    }

    private static boolean parseBool(String raw, String name) {
        String lower = raw.toLowerCase();
        if (lower.equals("true") || lower.equals("yes") || lower.equals("1")) return true;
        if (lower.equals("false") || lower.equals("no") || lower.equals("0")) return false;
        throw new ConfigException("division: " + name + " must be true|false, got '" + raw + "'");
    }

    private static int[] parseDivisor(String raw) {
        String s = raw.strip();
        if (s.contains("-")) {
            String[] parts = s.split("-");
            if (parts.length != 2) {
                throw new ConfigException("division: divisor malformed range '" + raw + "'");
            }
            int start, end;
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ConfigException("division: divisor range must be ints, got '" + raw + "'");
            }
            if (start < 1 || end < start) {
                throw new ConfigException(
                        "division: divisor must satisfy 1<=start<=end, got '" + raw + "'");
            }
            int[] out = new int[end - start + 1];
            for (int i = 0; i < out.length; i++) out[i] = start + i;
            return out;
        }
        String[] parts = s.split(",");
        Set<Integer> seen = new LinkedHashSet<>();
        for (String part : parts) {
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new ConfigException(
                        "division: divisor must be int, range, or comma list, got '" + raw + "'");
            }
            if (v < 1) {
                throw new ConfigException(
                        "division: divisor entries must be >=1, got '" + raw + "'");
            }
            seen.add(v);
        }
        if (seen.isEmpty()) {
            throw new ConfigException("division: divisor list is empty");
        }
        int[] out = new int[seen.size()];
        int i = 0;
        for (int v : seen) out[i++] = v;
        return out;
    }

    private static int capacity(DivisionParams params) {
        if (params.withRemainder) {
            int sum = 0;
            for (int d : params.divisors) sum += d * params.resultMax;
            return sum;
        }
        return params.divisors.length * params.resultMax;
    }
}
