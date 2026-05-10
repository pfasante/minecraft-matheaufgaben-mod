package dev.asante.matheaufgabenmod.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    @Test
    void allFourGeneratorsRegistered() {
        assertTrue(Registry.contains("plus"));
        assertTrue(Registry.contains("minus"));
        assertTrue(Registry.contains("einmaleins"));
        assertTrue(Registry.contains("division"));
    }

    @Test
    void getReturnsRegisteredGenerator() {
        Generator g = Registry.get("plus");
        assertEquals("plus", g.name());
    }

    @Test
    void getUnknownThrows() {
        ConfigException ex = assertThrows(ConfigException.class, () -> Registry.get("addition"));
        assertTrue(ex.getMessage().contains("unknown problem type 'addition'"));
    }

    @Test
    void allNamesReturnsSortedKeys() {
        assertEquals(java.util.List.of("division", "einmaleins", "minus", "plus"),
                Registry.allNames());
    }
}
