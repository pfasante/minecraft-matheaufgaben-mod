package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/** Production BudgetSurface that opens real Screens and writes to the HUD state holder. */
public final class MinecraftBudgetSurface implements BudgetSurface {

    private final Minecraft client;
    private final AtomicReference<BudgetState> hudState;

    public MinecraftBudgetSurface(Minecraft client, AtomicReference<BudgetState> hudState) {
        this.client = client;
        this.hudState = hudState;
    }

    @Override
    public boolean hasWorld() { return client.level != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public void openBudgetQuery(IntConsumer onSubmit) {
        client.setScreen(new BudgetQueryScreen(onSubmit));
    }

    @Override
    public void openWarning() {
        client.setScreen(new BudgetWarningScreen());
    }

    @Override
    public void openHardTimeout() {
        client.setScreen(new BudgetHardTimeoutScreen());
    }

    @Override
    public void updateHud(BudgetState state) {
        hudState.set(state);
    }
}
