package dev.asante.matheaufgabenmod.budget;

/**
 * Drives the {@link BudgetState} state machine and calls into a {@link BudgetSurface}
 * at the right transitions. Designed to be invoked from a per-tick callback
 * (e.g. Fabric's ClientTickEvents.END_CLIENT_TICK).
 *
 * <p>The tracker is single-threaded — Minecraft's client thread is the only caller.
 */
public final class BudgetTracker {

    private BudgetState state = BudgetState.initial();

    public BudgetState state() { return state; }

    public void onTick(BudgetSurface surface) {
        BudgetState before = state;

        // 1. Reconcile world presence first.
        if (!surface.hasWorld()) {
            if (state.phase() != BudgetPhase.WAITING_FOR_WORLD) {
                state = state.worldUnloaded();
            }
            surface.updateHud(state);
            return;
        }
        if (state.phase() == BudgetPhase.WAITING_FOR_WORLD) {
            state = state.worldLoaded();
            surface.openBudgetQuery(this::onBudgetSubmitted);
            surface.updateHud(state);
            return;
        }

        // 2. Tick only while not paused.
        if (!surface.isPaused()) {
            state = state.tick();
        }

        // 3. Fire one-shot callbacks on phase transitions.
        if (before.phase() != BudgetPhase.EXPIRED && state.phase() == BudgetPhase.EXPIRED) {
            surface.openSoftExpired();
        }
        if (before.phase() != BudgetPhase.HARD_TIMEOUT && state.phase() == BudgetPhase.HARD_TIMEOUT) {
            surface.openHardTimeout();
        }

        // 4. Update HUD every tick.
        surface.updateHud(state);
    }

    /**
     * Invoked by the budget-query screen when the user submits a value.
     * Clamping and phase guarding are inside BudgetState.budgetSubmitted.
     */
    public void onBudgetSubmitted(int minutes) {
        state = state.budgetSubmitted(minutes);
    }
}
