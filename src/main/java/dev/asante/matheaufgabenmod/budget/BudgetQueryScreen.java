package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

public final class BudgetQueryScreen extends Screen {

    private static final int DEFAULT_MINUTES = 60;

    private final IntConsumer onSubmit;
    private TextFieldWidget inputField;
    private Text feedback = Text.empty();

    public BudgetQueryScreen(IntConsumer onSubmit) {
        super(Text.translatable("matheaufgabenmod.budget.query.title"));
        this.onSubmit = onSubmit;
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.inputField = new TextFieldWidget(
                this.textRenderer,
                cx - 50, cy + 10, 100, 20,
                Text.translatable("matheaufgabenmod.budget.query.title")
        );
        this.inputField.setMaxLength(4);
        this.inputField.setText(Integer.toString(DEFAULT_MINUTES));
        this.addDrawableChild(this.inputField);
        this.setInitialFocus(this.inputField);

        ButtonWidget submit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.budget.query.submit"),
                btn -> onSubmitClicked()
        ).dimensions(cx - 50, cy + 40, 100, 20).build();
        this.addDrawableChild(submit);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (keyCode == 257 || keyCode == 335) {
            onSubmitClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmitClicked() {
        Integer parsed = parseMinutes(inputField.getText());
        if (parsed == null) {
            inputField.setText("");
            feedback = Text.translatable("matheaufgabenmod.budget.query.invalid");
            return;
        }
        onSubmit.accept(parsed);
        MinecraftClient.getInstance().setScreen(null);
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 60;
        int unitY = this.height / 2 + 35;
        int feedbackY = this.height / 2 + 75;

        // Title (1.5x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        ctx.drawCenteredTextWithShadow(tr, Text.translatable("matheaufgabenmod.budget.query.unit"),
                cx, unitY, 0xFFAAAAAA);

        if (!feedback.getString().isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
