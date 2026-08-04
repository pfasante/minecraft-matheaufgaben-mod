package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetStateTest {

    private static final int TPM = BudgetState.TICKS_PER_MINUTE;     // 1200
    private static final int WARNING_TICKS = BudgetState.WARNING_TICKS;    // 6000 (5 min)

    @Test
    void initialIsWaitingForWorldWithNoBudget() {
        BudgetState s = BudgetState.initial();
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, s.phase());
        assertEquals(0, s.budgetTicks());
        assertEquals(0, s.elapsedTicks());
    }

    @Test
    void worldLoadedAdvancesToWaitingForBudget() {
        BudgetState s = BudgetState.initial().worldLoaded();
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s.phase());
    }

    @Test
    void worldLoadedIsIdempotentInWaitingForBudget() {
        BudgetState s = BudgetState.initial().worldLoaded().worldLoaded();
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s.phase());
    }

    @Test
    void worldUnloadedResetsToWaitingForWorldFromAnyPhase() {
        BudgetState s = BudgetState.initial()
                .worldLoaded()
                .budgetSubmitted(30)
                .tick().tick().tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        BudgetState after = s.worldUnloaded();
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, after.phase());
        assertEquals(0, after.elapsedTicks(), "elapsed resets on world unload");
        assertEquals(0, after.budgetTicks(), "budget resets on world unload");
    }

    @Test
    void budgetSubmittedTransitionsToActive() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(30);
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        assertEquals(30 * TPM, s.budgetTicks());
        assertEquals(0, s.elapsedTicks());
    }

    @Test
    void budgetSubmittedIsIgnoredIfNotWaitingForBudget() {
        BudgetState s = BudgetState.initial();  // WAITING_FOR_WORLD
        BudgetState after = s.budgetSubmitted(30);
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, after.phase());
        assertEquals(0, after.budgetTicks());
    }

    @Test
    void tickIncrementsElapsedInActive() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(30).tick().tick();
        assertEquals(2, s.elapsedTicks());
        assertEquals(BudgetPhase.ACTIVE, s.phase());
    }

    @Test
    void tickInWaitingPhasesIsNoOp() {
        BudgetState s = BudgetState.initial().tick();
        assertEquals(0, s.elapsedTicks());
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, s.phase());

        BudgetState s2 = BudgetState.initial().worldLoaded().tick();
        assertEquals(0, s2.elapsedTicks());
        assertEquals(BudgetPhase.WAITING_FOR_BUDGET, s2.phase());
    }

    @Test
    void tickAtWarningBoundaryTransitionsToWarning() {
        // 10-minute budget: the 5-min warning window opens 5 minutes before the end,
        // i.e. once 5 of the 10 minutes have elapsed.
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = s.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase());
        s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
    }

    @Test
    void tickInWarningAccumulatesUntilHardTimeout() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = s.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary; i++) s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
        // Need WARNING_TICKS more ticks to reach the actual budget end.
        for (int i = 0; i < WARNING_TICKS - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.WARNING, s.phase());
        s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
    }

    @Test
    void shortBudgetSkipsWarningPhaseEntirely() {
        // A 5-minute budget equals WARNING_TICKS exactly — still too short to carve
        // out a separate warning window, so it must go straight to HARD_TIMEOUT.
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(5);
        for (int i = 0; i < WARNING_TICKS - 1; i++) s = s.tick();
        assertEquals(BudgetPhase.ACTIVE, s.phase(), "still active one tick before the end");
        s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase(), "5-min budget skips WARNING entirely");
    }

    @Test
    void remainingTicksReflectsPhase() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(2);  // 2400 ticks
        for (int i = 0; i < TPM; i++) s = s.tick();  // halfway
        assertEquals(TPM, s.remainingTicks(), "halfway through 2-min budget = 1 min remaining");

        BudgetState w = BudgetState.initial().worldLoaded().budgetSubmitted(10);
        int warningBoundary = w.budgetTicks() - WARNING_TICKS;
        for (int i = 0; i < warningBoundary; i++) w = w.tick();
        assertEquals(BudgetPhase.WARNING, w.phase());
        assertEquals(WARNING_TICKS, w.remainingTicks(), "just-entered WARNING: full warning window remaining");
    }

    @Test
    void hardTimeoutTicksAreNoOps() {
        BudgetState s = BudgetState.initial().worldLoaded().budgetSubmitted(1);
        for (int i = 0; i < TPM; i++) s = s.tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, s.phase());
        int elapsedAtTimeout = s.elapsedTicks();
        BudgetState after = s.tick().tick().tick();
        assertEquals(BudgetPhase.HARD_TIMEOUT, after.phase());
        assertEquals(elapsedAtTimeout, after.elapsedTicks(), "no further ticking after hard timeout");
    }
}
