package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BudgetSoftExpiredScreen extends Screen {

    public BudgetSoftExpiredScreen() {
        super(Text.translatable("matheaufgabenmod.budget.soft.title"));
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        ButtonWidget ok = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.soft.ok"),
                btn -> close()
        ).dimensions(cx - 50, cy + 30, 100, 20).build();
        this.addDrawableChild(ok);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 50;
        int subtitleY = this.height / 2 - 10;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFCC00);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.soft.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
