package dev.asante.matheaufgabenmod.budget;

/**
 * Immutable state record for the play-budget timer. Every transition method
 * returns a new instance — no mutation. Tick semantics: ticks only accumulate
 * while in {@link BudgetPhase#ACTIVE} or {@link BudgetPhase#WARNING}; waiting
 * phases and hard timeout are static.
 *
 * <p>The caller (BudgetTracker) is responsible for not invoking
 * {@link #tick()} when the game is paused.
 */
public record BudgetState(BudgetPhase phase, int budgetTicks, int elapsedTicks) {

    public static final int TICKS_PER_MINUTE = 60 * 20;     // 1200
    public static final int WARNING_TICKS = 5 * TICKS_PER_MINUTE; // 6000 (5 minutes)
    public static final int MIN_BUDGET_MINUTES = 1;
    public static final int MAX_BUDGET_MINUTES = 1440;       // 24 hours

    public static BudgetState initial() {
        return new BudgetState(BudgetPhase.WAITING_FOR_WORLD, 0, 0);
    }

    public BudgetState worldLoaded() {
        if (phase == BudgetPhase.WAITING_FOR_WORLD) {
            return new BudgetState(BudgetPhase.WAITING_FOR_BUDGET, 0, 0);
        }
        return this;
    }

    public BudgetState worldUnloaded() {
        return BudgetState.initial();
    }

    public BudgetState budgetSubmitted(int minutes) {
        if (phase != BudgetPhase.WAITING_FOR_BUDGET) return this;
        int clamped = Math.max(MIN_BUDGET_MINUTES, Math.min(MAX_BUDGET_MINUTES, minutes));
        return new BudgetState(BudgetPhase.ACTIVE, clamped * TICKS_PER_MINUTE, 0);
    }

    public BudgetState tick() {
        return switch (phase) {
            case ACTIVE -> {
                int next = elapsedTicks + 1;
                if (next >= budgetTicks) {
                    yield new BudgetState(BudgetPhase.HARD_TIMEOUT, budgetTicks, next);
                }
                if (budgetTicks > WARNING_TICKS && next >= budgetTicks - WARNING_TICKS) {
                    yield new BudgetState(BudgetPhase.WARNING, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.ACTIVE, budgetTicks, next);
            }
            case WARNING -> {
                int next = elapsedTicks + 1;
                if (next >= budgetTicks) {
                    yield new BudgetState(BudgetPhase.HARD_TIMEOUT, budgetTicks, next);
                }
                yield new BudgetState(BudgetPhase.WARNING, budgetTicks, next);
            }
            default -> this;
        };
    }

    /**
     * Ticks remaining until the budget is fully used up (HARD_TIMEOUT).
     * - In {@link BudgetPhase#ACTIVE} or {@link BudgetPhase#WARNING}: budgetTicks - elapsedTicks.
     * - Otherwise: 0.
     */
    public int remainingTicks() {
        return switch (phase) {
            case ACTIVE, WARNING -> Math.max(0, budgetTicks - elapsedTicks);
            default -> 0;
        };
    }
}
