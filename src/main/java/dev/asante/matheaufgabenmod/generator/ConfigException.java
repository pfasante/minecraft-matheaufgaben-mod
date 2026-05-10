package dev.asante.matheaufgabenmod.generator;

/**
 * Thrown when a section spec or generator parameter is invalid.
 *
 * <p>Runtime exception so generators don't pollute their signatures with
 * a checked throw. {@code ConfigLoader} catches it and falls back to defaults.
 */
public final class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }
}
