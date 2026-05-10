package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptScreenTest {

    @Test
    void exactAnswerAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "7"));
    }

    @Test
    void answerWithLeadingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "  7"));
    }

    @Test
    void answerWithTrailingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "7  "));
    }

    @Test
    void remainderAnswerAcceptedWithSpaces() {
        // Division problems answer "3 R 1" — kid typing "3 R 1" works as-is.
        assertTrue(PromptScreen.checkAnswer(new Problem("13 : 4", "3 R 1"), "3 R 1"));
    }

    @Test
    void remainderAnswerWithSurroundingWhitespaceAccepted() {
        assertTrue(PromptScreen.checkAnswer(new Problem("13 : 4", "3 R 1"), " 3 R 1 "));
    }

    @Test
    void wrongAnswerRejected() {
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "8"));
    }

    @Test
    void emptyInputRejected() {
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), ""));
        assertFalse(PromptScreen.checkAnswer(new Problem("3 + 4", "7"), "   "));
    }
}
