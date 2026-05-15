package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public final class BudgetQueryScreen extends Screen {

    private static final int DEFAULT_MINUTES = 60;

    private final IntConsumer onSubmit;
    private EditBox inputField;
    private Component feedback = Component.empty();

    public BudgetQueryScreen(IntConsumer onSubmit) {
        super(Component.translatable("matheaufgabenmod.budget.query.title"));
        this.onSubmit = onSubmit;
    }

    @Override
    public boolean isPauseScreen() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.inputField = new EditBox(
                this.font,
                cx - 50, cy + 10, 100, 20,
                Component.translatable("matheaufgabenmod.budget.query.title")
        );
        this.inputField.setMaxLength(4);
        this.inputField.setValue(Integer.toString(DEFAULT_MINUTES));
        this.addRenderableWidget(this.inputField);
        this.setInitialFocus(this.inputField);

        Button submit = Button.builder(
                Component.translatable("matheaufgabenmod.budget.query.submit"),
                btn -> onSubmitClicked()
        ).bounds(cx - 50, cy + 40, 100, 20).build();
        this.addRenderableWidget(submit);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (event.key() == 257 || event.key() == 335) {
            onSubmitClicked();
            return true;
        }
        return super.keyPressed(event);
    }

    private void onSubmitClicked() {
        Integer parsed = parseMinutes(inputField.getValue());
        if (parsed == null) {
            inputField.setValue("");
            feedback = Component.translatable("matheaufgabenmod.budget.query.invalid");
            return;
        }
        onSubmit.accept(parsed);
        Minecraft.getInstance().setScreen(null);
    }

    static Integer parseMinutes(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < BudgetState.MIN_BUDGET_MINUTES || v > BudgetState.MAX_BUDGET_MINUTES) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        Font tr = this.font;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 60;
        int unitY = this.height / 2 + 35;
        int feedbackY = this.height / 2 + 75;

        // Title (1.5x scale)
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) cx, (float) titleY);
        ctx.pose().scale(1.5f, 1.5f);
        ctx.drawCenteredString(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.pose().popMatrix();

        ctx.drawCenteredString(tr, Component.translatable("matheaufgabenmod.budget.query.unit"),
                cx, unitY, 0xFFAAAAAA);

        if (!feedback.getString().isEmpty()) {
            ctx.drawCenteredString(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
