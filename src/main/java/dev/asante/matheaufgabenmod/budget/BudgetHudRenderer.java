package dev.asante.matheaufgabenmod.budget;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Draws the play-budget HUD overlay top-right. The current state is supplied
 * by {@link BudgetTracker} via {@link #stateHolder()} — a thread-safe atomic
 * reference (client thread writes, render thread reads on Fabric, but render
 * actually runs on the client thread too in 1.21.x; the atomic is defensive).
 */
public final class BudgetHudRenderer implements HudRenderCallback {

    private static final int RIGHT_MARGIN = 10;
    private static final int TOP_MARGIN = 10;

    private final AtomicReference<BudgetState> stateHolder = new AtomicReference<>(BudgetState.initial());

    /** Hand this to BudgetTracker via a setter callback. Tracker writes; renderer reads. */
    public AtomicReference<BudgetState> stateHolder() { return stateHolder; }

    public void register() {
        HudRenderCallback.EVENT.register(this);
    }

    @Override
    public void onHudRender(GuiGraphics ctx, DeltaTracker tickCounter) {
        BudgetState s = stateHolder.get();
        Component label;
        int colour;
        switch (s.phase()) {
            case ACTIVE -> { label = Component.literal("Restzeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFFFFFF; }
            case EXPIRED -> { label = Component.literal("Schlusszeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFF5555; }
            default -> { return; }  // no HUD in WAITING_* or HARD_TIMEOUT
        }
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(label);
        int x = ctx.guiWidth() - textWidth - RIGHT_MARGIN;
        ctx.drawString(client.font, label, x, TOP_MARGIN, colour);
    }

    static String formatMmSs(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format("%d:%02d", mm, ss);
    }
}
