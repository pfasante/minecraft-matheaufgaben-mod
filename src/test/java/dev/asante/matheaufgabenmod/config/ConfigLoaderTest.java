package dev.asante.matheaufgabenmod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadsDefaultsWhenFileMissing(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT, cfg);
        assertTrue(Files.exists(configFile), "should create the config file with defaults");
    }

    @Test
    void loadsValidConfig(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {
                  "intervalMinutes": 7,
                  "sectionSpecs": ["plus:range=20,count=1"]
                }
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(7, cfg.intervalMinutes());
        assertEquals(List.of("plus:range=20,count=1"), cfg.sectionSpecs());
    }

    @Test
    void fallsBackToDefaultsOnMalformedJson(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, "not json {{");
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT, cfg);
    }

    @Test
    void dropsInvalidSpecsAndKeepsValidOnes(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {
                  "intervalMinutes": 5,
                  "sectionSpecs": [
                    "plus:range=100,count=1",
                    "garbage_no_colon",
                    "minus:range=100,count=1"
                  ]
                }
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(2, cfg.sectionSpecs().size());
        assertTrue(cfg.sectionSpecs().contains("plus:range=100,count=1"));
        assertTrue(cfg.sectionSpecs().contains("minus:range=100,count=1"));
    }

    @Test
    void fallsBackToDefaultsWhenAllSpecsInvalid(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {"intervalMinutes": 5, "sectionSpecs": ["garbage", "more_garbage"]}
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT.sectionSpecs(), cfg.sectionSpecs());
    }

    @Test
    void rejectsIntervalLessThanOne(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {"intervalMinutes": 0, "sectionSpecs": ["plus:range=10,count=1"]}
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(ModConfig.DEFAULT.intervalMinutes(), cfg.intervalMinutes());
    }

    @Test
    void dropsSpecsWithUnknownGeneratorType(@TempDir Path tmp) throws IOException {
        Path configFile = tmp.resolve("matheaufgabenmod.json");
        Files.writeString(configFile, """
                {
                  "intervalMinutes": 5,
                  "sectionSpecs": [
                    "plus:range=100,count=1",
                    "unknowntype:count=1",
                    "minus:range=100,count=1"
                  ]
                }
                """);
        ModConfig cfg = ConfigLoader.loadOrCreate(configFile);
        assertEquals(2, cfg.sectionSpecs().size());
        assertTrue(cfg.sectionSpecs().contains("plus:range=100,count=1"));
        assertTrue(cfg.sectionSpecs().contains("minus:range=100,count=1"));
    }

    @Test
    void modConfigDefaultIsValid() {
        // Each default spec should parse successfully.
        for (String spec : ModConfig.DEFAULT.sectionSpecs()) {
            assertDoesNotThrow(() -> SectionSpec.parse(spec),
                    "default spec must parse: " + spec);
        }
        assertTrue(ModConfig.DEFAULT.intervalMinutes() >= 1);
    }
}
