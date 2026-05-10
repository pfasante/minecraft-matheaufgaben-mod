package dev.asante.matheaufgabenmod.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class EinmaleinsGenerator implements Generator {

    private static final Set<String> KNOWN_PARAMS = Set.of("rows", "count");
    private static final String MULT_SIGN = "·";  // U+00B7 MIDDLE DOT

    public record EinmaleinsParams(int[] rows, int count) {}

    @Override
    public String name() { return "einmaleins"; }

    @Override
    public Object parseParams(Map<String, String> raw) {
        for (String k : raw.keySet()) {
            if (!KNOWN_PARAMS.contains(k)) {
                throw new ConfigException("einmaleins: unknown param '" + k + "'");
            }
        }
        if (!raw.containsKey("rows")) throw new ConfigException("einmaleins: missing required param 'rows'");
        if (!raw.containsKey("count")) throw new ConfigException("einmaleins: missing required param 'count'");
        int[] rows = parseRows(raw.get("rows"));
        int count;
        try {
            count = Integer.parseInt(raw.get("count"));
        } catch (NumberFormatException e) {
            throw new ConfigException("einmaleins: count must be int, got '" + raw.get("count") + "'");
        }
        if (count < 1) throw new ConfigException("einmaleins: count must be >= 1, got " + count);
        int capacity = rows.length * 10;
        if (count > capacity) {
            throw new ConfigException(
                    "einmaleins: count=" + count + " exceeds capacity " + capacity
                            + " for rows=" + raw.get("rows"));
        }
        return new EinmaleinsParams(rows, count);
    }

    @Override
    public List<Problem> generate(Random rng, Object paramsObj) {
        if (!(paramsObj instanceof EinmaleinsParams params)) {
            throw new IllegalArgumentException("einmaleins: expected EinmaleinsParams, got " + paramsObj);
        }
        List<int[]> allPairs = new ArrayList<>();
        for (int r : params.rows) {
            for (int j = 1; j <= 10; j++) {
                allPairs.add(new int[]{r, j});
            }
        }
        Collections.shuffle(allPairs, rng);
        List<Problem> problems = new ArrayList<>(params.count);
        for (int i = 0; i < params.count; i++) {
            int a = allPairs.get(i)[0];
            int b = allPairs.get(i)[1];
            problems.add(new Problem(a + " " + MULT_SIGN + " " + b, Integer.toString(a * b)));
        }
        return List.copyOf(problems);
    }

    @Override
    public String describe() {
        return """
                einmaleins — multiplication tables (1..10 × rows)
                  rows: range like '2-9' | list like '2,3,7' | 'all'  (required)
                  count: int                                          (required, >=1)
                """;
    }

    private static int[] parseRows(String raw) {
        String s = raw.strip();
        if (s.equals("all")) return new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        if (s.contains("-")) {
            String[] parts = s.split("-");
            if (parts.length != 2) {
                throw new ConfigException("einmaleins: rows malformed range '" + raw + "'");
            }
            int start, end;
            try {
                start = Integer.parseInt(parts[0]);
                end = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ConfigException("einmaleins: rows range must be ints, got '" + raw + "'");
            }
            if (!(1 <= start && start <= end && end <= 10)) {
                throw new ConfigException(
                        "einmaleins: rows must satisfy 1<=start<=end<=10, got '" + raw + "'");
            }
            int[] out = new int[end - start + 1];
            for (int i = 0; i < out.length; i++) out[i] = start + i;
            return out;
        }
        // Comma-separated list
        String[] parts = s.split(",");
        Set<Integer> seen = new LinkedHashSet<>();
        for (String part : parts) {
            int v;
            try {
                v = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new ConfigException(
                        "einmaleins: rows list must be comma-separated ints, got '" + raw + "'");
            }
            if (v < 1 || v > 10) {
                throw new ConfigException(
                        "einmaleins: rows entries must be 1..10, got '" + raw + "'");
            }
            seen.add(v);
        }
        if (seen.isEmpty()) {
            throw new ConfigException("einmaleins: rows list is empty");
        }
        int[] out = new int[seen.size()];
        int i = 0;
        for (int v : seen) out[i++] = v;
        return out;
    }
}
