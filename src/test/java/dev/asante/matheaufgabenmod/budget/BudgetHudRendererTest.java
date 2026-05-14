package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BudgetHudRendererTest {

    @Test
    void formatsMinutesAndSecondsWithLeadingZero() {
        assertEquals("0:00", BudgetHudRenderer.formatMmSs(0));
        assertEquals("0:01", BudgetHudRenderer.formatMmSs(20));      // 1 second
        assertEquals("1:00", BudgetHudRenderer.formatMmSs(60 * 20)); // 1 minute
        assertEquals("23:45", BudgetHudRenderer.formatMmSs((23 * 60 + 45) * 20));
        assertEquals("0:00", BudgetHudRenderer.formatMmSs(-100));    // negative clamps to 0:00
    }
}
