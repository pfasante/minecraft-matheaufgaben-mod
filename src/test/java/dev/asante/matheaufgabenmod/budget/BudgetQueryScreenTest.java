package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetQueryScreenTest {

    @Test
    void parsesValidIntegers() {
        assertEquals(1, BudgetQueryScreen.parseMinutes("1"));
        assertEquals(60, BudgetQueryScreen.parseMinutes("60"));
        assertEquals(1440, BudgetQueryScreen.parseMinutes("1440"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(30, BudgetQueryScreen.parseMinutes(" 30 "));
    }

    @Test
    void rejectsOutOfRange() {
        assertNull(BudgetQueryScreen.parseMinutes("0"));
        assertNull(BudgetQueryScreen.parseMinutes("-5"));
        assertNull(BudgetQueryScreen.parseMinutes("1441"));
    }

    @Test
    void rejectsNonInteger() {
        assertNull(BudgetQueryScreen.parseMinutes(""));
        assertNull(BudgetQueryScreen.parseMinutes("abc"));
        assertNull(BudgetQueryScreen.parseMinutes("30.5"));
    }
}
