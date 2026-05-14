package dev.asante.matheaufgabenmod.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

class BudgetTrackerTest {

    private static final int TPM = BudgetState.TICKS_PER_MINUTE;
    private static final int GRACE = BudgetState.GRACE_TICKS;

    /** Test double recording surface interactions and exposing controllable inputs. */
    private static final class FakeSurface implements BudgetSurface {
        boolean hasWorld = false;
        boolean isPaused = false;
        IntConsumer pendingBudgetCallback;
        int softExpiredCount = 0;
        int hardTimeoutCount = 0;
        final List<BudgetState> hudHistory = new ArrayList<>();

        @Override public boolean hasWorld() { return hasWorld; }
        @Override public boolean isPaused() { return isPaused; }
        @Override public void openBudgetQuery(IntConsumer onSubmit) { this.pendingBudgetCallback = onSubmit; }
        @Override public void openSoftExpired() { softExpiredCount++; }
        @Override public void openHardTimeout() { hardTimeoutCount++; }
        @Override public void updateHud(BudgetState s) { hudHistory.add(s); }
    }

    private static void tickN(BudgetTracker tracker, FakeSurface surface, int n) {
        for (int i = 0; i < n; i++) tracker.onTick(surface);
    }

    @Test
    void noWorldNoBudgetQuery() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        tickN(t, s, 100);
        assertNull(s.pendingBudgetCallback, "no world → no query opened");
    }

    @Test
    void worldLoadOpensBudgetQuery() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        assertNotNull(s.pendingBudgetCallback, "query opens on first tick after world available");
    }

    @Test
    void doesNotReopenBudgetQueryOnSubsequentTicks() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        IntConsumer first = s.pendingBudgetCallback;
        s.pendingBudgetCallback = null;
        for (int i = 0; i < 10; i++) t.onTick(s);
        assertNull(s.pendingBudgetCallback, "tracker must not reopen the query on every tick");
        assertNotNull(first);
    }

    @Test
    void budgetSubmissionTransitionsToActive() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(30);
        assertEquals(BudgetPhase.ACTIVE, t.state().phase());
        assertEquals(30 * TPM, t.state().budgetTicks());
    }

    @Test
    void pausedTicksDoNotAdvanceElapsed() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(10);
        s.isPaused = true;
        tickN(t, s, 100);
        assertEquals(0, t.state().elapsedTicks(), "paused = no advance");
    }

    @Test
    void crossingBudgetFiresSoftExpiredExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(1);  // 1200 tick budget
        tickN(t, s, TPM);
        assertEquals(BudgetPhase.EXPIRED, t.state().phase());
        assertEquals(1, s.softExpiredCount, "soft-expired called once on transition");
        tickN(t, s, 100);
        assertEquals(1, s.softExpiredCount, "and not again while in EXPIRED");
    }

    @Test
    void crossingGraceFiresHardTimeoutExactlyOnce() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(1);
        tickN(t, s, TPM + GRACE);
        assertEquals(BudgetPhase.HARD_TIMEOUT, t.state().phase());
        assertEquals(1, s.hardTimeoutCount);
        tickN(t, s, 100);
        assertEquals(1, s.hardTimeoutCount, "no repeat firing in HARD_TIMEOUT");
    }

    @Test
    void leavingWorldResetsToInitial() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(60);
        tickN(t, s, 600);
        assertEquals(600, t.state().elapsedTicks());
        s.hasWorld = false;
        t.onTick(s);
        assertEquals(BudgetPhase.WAITING_FOR_WORLD, t.state().phase());
        // Re-entering opens a fresh query.
        s.pendingBudgetCallback = null;
        s.hasWorld = true;
        t.onTick(s);
        assertNotNull(s.pendingBudgetCallback);
    }

    @Test
    void hudUpdatesEveryTickWithCurrentState() {
        BudgetTracker t = new BudgetTracker();
        FakeSurface s = new FakeSurface();
        s.hasWorld = true;
        t.onTick(s);
        s.pendingBudgetCallback.accept(2);
        tickN(t, s, 5);
        assertEquals(6, s.hudHistory.size(),
                "1 hud update at world-load + 5 ticks under ACTIVE");
        assertEquals(BudgetPhase.ACTIVE, s.hudHistory.get(s.hudHistory.size() - 1).phase());
    }
}
