package dev.asante.matheaufgabenmod.config;

import dev.asante.matheaufgabenmod.generator.ConfigException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed section spec of the form {@code type:k=v[,k=v...]}.
 *
 * <p>An empty body after the colon yields an empty params map. Missing colon,
 * empty type, malformed {@code k=v} pairs, empty keys, and duplicate keys all
 * raise {@link ConfigException} with a descriptive message.
 */
public record SectionSpec(String type, Map<String, String> params) {

    public static SectionSpec parse(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            throw new ConfigException("section spec missing ':' (got '" + spec + "')");
        }
        String type = spec.substring(0, colon);
        if (type.isEmpty()) {
            throw new ConfigException("section spec has empty type (got '" + spec + "')");
        }
        String body = spec.substring(colon + 1);
        Map<String, String> params = new LinkedHashMap<>();
        if (!body.isEmpty()) {
            for (String kv : body.split(",")) {
                int eq = kv.indexOf('=');
                if (eq < 0) {
                    throw new ConfigException(
                            "section '" + type + "': expected k=v, got '" + kv + "'");
                }
                String k = kv.substring(0, eq);
                String v = kv.substring(eq + 1);
                if (k.isEmpty()) {
                    throw new ConfigException(
                            "section '" + type + "': empty key in '" + kv + "'");
                }
                if (params.containsKey(k)) {
                    throw new ConfigException(
                            "section '" + type + "': duplicate key '" + k + "'");
                }
                params.put(k, v);
            }
        }
        return new SectionSpec(type, Map.copyOf(params));
    }
}
