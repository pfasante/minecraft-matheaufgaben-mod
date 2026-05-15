package dev.asante.matheaufgabenmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.asante.matheaufgabenmod.generator.ConfigException;
import dev.asante.matheaufgabenmod.generator.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("matheaufgabenmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {}

    /**
     * Load the config file at {@code path}, creating it with defaults if absent.
     * Falls back to defaults on any parse error rather than disabling the mod.
     */
    public static ModConfig loadOrCreate(Path path) {
        if (!Files.exists(path)) {
            writeDefaults(path);
            return ModConfig.DEFAULT;
        }
        try {
            String text = Files.readString(path);
            RawConfig raw = GSON.fromJson(text, RawConfig.class);
            if (raw == null) {
                LOGGER.error("[matheaufgabenmod] config file empty; using defaults");
                return ModConfig.DEFAULT;
            }
            return validate(raw);
        } catch (JsonSyntaxException e) {
            LOGGER.error("[matheaufgabenmod] config file is not valid JSON: {}; using defaults", e.getMessage());
            return ModConfig.DEFAULT;
        } catch (IOException e) {
            LOGGER.error("[matheaufgabenmod] failed to read config: {}; using defaults", e.getMessage());
            return ModConfig.DEFAULT;
        }
    }

    private static void writeDefaults(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(ModConfig.DEFAULT));
            LOGGER.info("[matheaufgabenmod] wrote default config to {}", path);
        } catch (IOException e) {
            LOGGER.error("[matheaufgabenmod] could not write default config: {}", e.getMessage());
        }
    }

    private static ModConfig validate(RawConfig raw) {
        int interval = raw.intervalMinutes;
        if (interval < 1) {
            LOGGER.warn("[matheaufgabenmod] intervalMinutes={} invalid; using default {}",
                    interval, ModConfig.DEFAULT.intervalMinutes());
            interval = ModConfig.DEFAULT.intervalMinutes();
        }
        int tasks = raw.tasksPerIteration;
        if (tasks < 1) {
            // Includes the JSON-absent case (Gson leaves the int at 0): silently fall back.
            tasks = ModConfig.DEFAULT.tasksPerIteration();
        }
        List<String> validSpecs = new ArrayList<>();
        if (raw.sectionSpecs != null) {
            for (String spec : raw.sectionSpecs) {
                try {
                    SectionSpec parsed = SectionSpec.parse(spec);
                    if (!Registry.contains(parsed.type())) {
                        throw new ConfigException("unknown problem type '" + parsed.type() + "'");
                    }
                    validSpecs.add(spec);
                } catch (ConfigException e) {
                    LOGGER.warn("[matheaufgabenmod] dropping invalid section spec '{}': {}",
                            spec, e.getMessage());
                }
            }
        }
        if (validSpecs.isEmpty()) {
            LOGGER.error("[matheaufgabenmod] no valid section specs in config; using defaults");
            return new ModConfig(interval, tasks, ModConfig.DEFAULT.sectionSpecs());
        }
        return new ModConfig(interval, tasks, List.copyOf(validSpecs));
    }

    /** Direct deserialisation target — populated by Gson. */
    private static final class RawConfig {
        int intervalMinutes;
        int tasksPerIteration;
        List<String> sectionSpecs;
    }
}
