package dev.asante.matheaufgabenmod.config;

import dev.asante.matheaufgabenmod.generator.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectionSpecTest {

    @Test
    void parsesSimpleSpec() {
        SectionSpec s = SectionSpec.parse("plus:range=100,count=10");
        assertEquals("plus", s.type());
        assertEquals(Map.of("range", "100", "count", "10"), s.params());
    }

    @Test
    void parsesEmptyParamBody() {
        SectionSpec s = SectionSpec.parse("plus:");
        assertEquals("plus", s.type());
        assertEquals(Map.of(), s.params());
    }

    @Test
    void parsesSingleParam() {
        SectionSpec s = SectionSpec.parse("einmaleins:rows=2-9");
        assertEquals("einmaleins", s.type());
        assertEquals(Map.of("rows", "2-9"), s.params());
    }

    @Test
    void rejectsMissingColon() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus"));
        assertTrue(ex.getMessage().contains("missing ':'"), ex.getMessage());
    }

    @Test
    void rejectsEmptyType() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse(":range=100"));
        assertTrue(ex.getMessage().contains("empty type"), ex.getMessage());
    }

    @Test
    void rejectsMalformedKv() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:range=100,bogus"));
        assertTrue(ex.getMessage().contains("expected k=v"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateKey() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:range=100,range=20"));
        assertTrue(ex.getMessage().contains("duplicate"), ex.getMessage());
    }

    @Test
    void rejectsEmptyKey() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> SectionSpec.parse("plus:=100"));
        assertTrue(ex.getMessage().contains("empty key"), ex.getMessage());
    }
}
