package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BudgetHardTimeoutScreen extends Screen {

    public BudgetHardTimeoutScreen() {
        super(Text.translatable("matheaufgabenmod.budget.hard.title"));
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        ButtonWidget quit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.hard.quit"),
                btn -> MinecraftClient.getInstance().scheduleStop()
        ).dimensions(cx - 60, cy + 40, 120, 20).build();
        this.addDrawableChild(quit);
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
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFF5555);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.hard.subtitle"),
                cx, subtitleY, 0xFFFFFFFF);
    }
}
