package dev.asante.matheaufgabenmod.generator;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pure-function generator interface. Implementations have no Minecraft imports
 * and no I/O — they consume an {@link Random} and a generator-specific Params
 * record, and return a list of {@link Problem}s.
 */
public interface Generator {

    /** Registry key, e.g. "plus". */
    String name();

    /**
     * Validate and parse a section spec's raw key=value map into a Params
     * record specific to this generator. Throws {@link ConfigException} on
     * invalid input — the message must be prefixed with {@code "<name>: "}.
     */
    Object parseParams(Map<String, String> raw);

    /**
     * Generate {@code params.count}-many distinct problems using the supplied
     * RNG. Returns a list whose order depends on the RNG state.
     *
     * <p>Implementations narrow {@code params} from {@code Object} via an
     * {@code instanceof} check at the top of the method. The interface uses
     * {@code Object} (rather than a generic type parameter on {@link Generator})
     * because the {@link Registry} stores heterogeneous generator instances
     * under string keys; making the registry generic over the params type
     * would either erase to {@code Object} anyway or force callers to know
     * each concrete params type at the lookup site.
     */
    List<Problem> generate(Random rng, Object params);

    /** Human-readable schema, used for log diagnostics on bad config. */
    String describe();
}
