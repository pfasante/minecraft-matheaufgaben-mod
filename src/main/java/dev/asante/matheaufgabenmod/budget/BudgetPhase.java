package dev.asante.matheaufgabenmod.budget;

/** The five lifecycle phases of the play-budget timer. See the spec for transitions. */
public enum BudgetPhase {
    WAITING_FOR_WORLD,
    WAITING_FOR_BUDGET,
    ACTIVE,
    EXPIRED,
    HARD_TIMEOUT
}
