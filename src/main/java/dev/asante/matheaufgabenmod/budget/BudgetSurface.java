package dev.asante.matheaufgabenmod.budget;

import java.util.function.IntConsumer;

/**
 * Narrow façade over MinecraftClient for the play-budget timer. Mirrors the
 * pattern used by ClientSurface in the timer package: lets BudgetTracker be
 * fully unit-testable in plain JUnit, without booting Minecraft.
 */
public interface BudgetSurface {
    boolean hasWorld();
    boolean isPaused();

    /** Open the modal "how many minutes?" screen; invoke the consumer with the user's choice. */
    void openBudgetQuery(IntConsumer onSubmit);

    /** Open the dismissable pre-expiry warning popup. */
    void openWarning();

    /** Open the undismissable "Spiel beenden" popup. */
    void openHardTimeout();

    /** Update the HUD's idea of the remaining state. Called every tick. */
    void updateHud(BudgetState state);
}
