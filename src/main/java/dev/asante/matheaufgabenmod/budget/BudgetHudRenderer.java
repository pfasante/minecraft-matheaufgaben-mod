package dev.asante.matheaufgabenmod.budget;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Draws the play-budget HUD overlay top-right. The current state is supplied
 * by {@link BudgetTracker} via {@link #stateHolder()} — a thread-safe atomic
 * reference (client thread writes, render thread reads).
 *
 * <p>MC 26.x replaced {@code HudRenderCallback} (a simple per-frame draw hook)
 * with {@link HudElementRegistry} (identity-based attachment + extract-then-render
 * state pattern). The HUD element is registered after the vanilla MISC_OVERLAYS
 * layer so it draws on top of the game world but underneath modal screens.
 */
public final class BudgetHudRenderer implements HudElement {

    private static final Identifier ELEMENT_ID =
            Identifier.fromNamespaceAndPath("matheaufgabenmod", "budget_timer");
    private static final int RIGHT_MARGIN = 10;
    private static final int TOP_MARGIN = 10;

    private final AtomicReference<BudgetState> stateHolder = new AtomicReference<>(BudgetState.initial());

    /** Hand this to BudgetTracker so it can push state on each tick. */
    public AtomicReference<BudgetState> stateHolder() { return stateHolder; }

    public void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ELEMENT_ID, this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
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
        ctx.text(client.font, label, x, TOP_MARGIN, colour);
    }

    static String formatMmSs(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format("%d:%02d", mm, ss);
    }
}
