package dev.asante.matheaufgabenmod.generator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Registry {

    private static final Map<String, Generator> GENERATORS;

    static {
        Map<String, Generator> map = new TreeMap<>();
        registerInto(map, new PlusGenerator());
        registerInto(map, new MinusGenerator());
        registerInto(map, new EinmaleinsGenerator());
        registerInto(map, new DivisionGenerator());
        GENERATORS = Collections.unmodifiableMap(map);
    }

    private static void registerInto(Map<String, Generator> map, Generator gen) {
        if (map.containsKey(gen.name())) {
            throw new IllegalStateException("generator '" + gen.name() + "' already registered");
        }
        map.put(gen.name(), gen);
    }

    private Registry() {}

    public static boolean contains(String name) {
        return GENERATORS.containsKey(name);
    }

    public static Generator get(String name) {
        Generator g = GENERATORS.get(name);
        if (g == null) {
            throw new ConfigException("unknown problem type '" + name + "'");
        }
        return g;
    }

    public static List<String> allNames() {
        return List.copyOf(GENERATORS.keySet());
    }
}
