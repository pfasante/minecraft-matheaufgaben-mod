package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BudgetHardTimeoutScreen extends Screen {

    public BudgetHardTimeoutScreen() {
        super(Component.translatable("matheaufgabenmod.budget.hard.title"));
    }

    @Override
    public boolean isPauseScreen() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button quit = Button.builder(
                Component.translatable("matheaufgabenmod.budget.hard.quit"),
                btn -> Minecraft.getInstance().stop()
        ).bounds(cx - 60, cy + 40, 120, 20).build();
        this.addRenderableWidget(quit);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        Font tr = this.font;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 50;
        int subtitleY = this.height / 2 - 10;

        ctx.pose().pushMatrix();
        ctx.pose().translate((float) cx, (float) titleY);
        ctx.pose().scale(1.5f, 1.5f);
        ctx.centeredText(tr, this.title, 0, 0, 0xFFFF5555);
        ctx.pose().popMatrix();

        ctx.centeredText(tr, Component.translatable("matheaufgabenmod.budget.hard.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
