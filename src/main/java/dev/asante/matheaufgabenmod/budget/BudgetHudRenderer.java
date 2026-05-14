package dev.asante.matheaufgabenmod.budget;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
    public void onHudRender(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        BudgetState s = stateHolder.get();
        Text label;
        int colour;
        switch (s.phase()) {
            case ACTIVE -> { label = Text.literal("Restzeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFFFFFF; }
            case EXPIRED -> { label = Text.literal("Schlusszeit: " + formatMmSs(s.remainingTicks())); colour = 0xFFFF5555; }
            default -> { return; }  // no HUD in WAITING_* or HARD_TIMEOUT
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(label);
        int x = ctx.getScaledWindowWidth() - textWidth - RIGHT_MARGIN;
        ctx.drawTextWithShadow(client.textRenderer, label, x, TOP_MARGIN, colour);
    }

    static String formatMmSs(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int mm = totalSeconds / 60;
        int ss = totalSeconds % 60;
        return String.format("%d:%02d", mm, ss);
    }
}
