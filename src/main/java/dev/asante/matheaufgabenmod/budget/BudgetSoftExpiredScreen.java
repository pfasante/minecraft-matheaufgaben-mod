package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BudgetSoftExpiredScreen extends Screen {

    public BudgetSoftExpiredScreen() {
        super(Component.translatable("matheaufgabenmod.budget.soft.title"));
    }

    @Override
    public boolean isPauseScreen() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button ok = Button.builder(
                Component.translatable("matheaufgabenmod.budget.soft.ok"),
                btn -> onClose()
        ).bounds(cx - 50, cy + 30, 100, 20).build();
        this.addRenderableWidget(ok);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
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
        ctx.centeredText(tr, this.title, 0, 0, 0xFFFFCC00);
        ctx.pose().popMatrix();

        ctx.centeredText(tr, Component.translatable("matheaufgabenmod.budget.soft.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
