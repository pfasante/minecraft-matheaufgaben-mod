package dev.asante.matheaufgabenmod.budget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Asks the kid how many minutes they want to play. Shown automatically when a
 * world loads.
 *
 * <p>Two-stage UI: a row of three preset buttons appears first (30 min, 60 min,
 * "Eigene Zeit…"). Clicking 30 or 60 submits immediately — no typing required.
 * Clicking "Eigene Zeit…" expands to a text field + submit button for arbitrary
 * values in {@link BudgetState#MIN_BUDGET_MINUTES}..{@link BudgetState#MAX_BUDGET_MINUTES}.
 * Once in custom mode there's no going back without re-entering the world.
 */
public final class BudgetQueryScreen extends Screen {

    private static final int DEFAULT_CUSTOM_MINUTES = 30;

    private final IntConsumer onSubmit;
    private EditBox inputField;
    private Component feedback = Component.empty();
    /** Toggled by clicking "Eigene Zeit…"; controls which widgets are visible. */
    private boolean customMode = false;

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
        int btnW = 120;
        int btnH = 20;
        int gap = 4;

        if (!customMode) {
            // Preset mode: three stacked buttons centred horizontally.
            int firstY = cy - 30;
            Button preset30 = Button.builder(
                    Component.translatable("matheaufgabenmod.budget.query.preset.30"),
                    btn -> submitPreset(30)
            ).bounds(cx - btnW / 2, firstY, btnW, btnH).build();
            this.addRenderableWidget(preset30);

            Button preset60 = Button.builder(
                    Component.translatable("matheaufgabenmod.budget.query.preset.60"),
                    btn -> submitPreset(60)
            ).bounds(cx - btnW / 2, firstY + btnH + gap, btnW, btnH).build();
            this.addRenderableWidget(preset60);

            Button presetCustom = Button.builder(
                    Component.translatable("matheaufgabenmod.budget.query.preset.custom"),
                    btn -> enterCustomMode()
            ).bounds(cx - btnW / 2, firstY + 2 * (btnH + gap), btnW, btnH).build();
            this.addRenderableWidget(presetCustom);
        } else {
            // Custom mode: text input + submit button.
            this.inputField = new EditBox(
                    this.font,
                    cx - 50, cy + 10, 100, 20,
                    Component.translatable("matheaufgabenmod.budget.query.title")
            );
            this.inputField.setMaxLength(4);
            this.inputField.setValue(Integer.toString(DEFAULT_CUSTOM_MINUTES));
            this.addRenderableWidget(this.inputField);
            this.setInitialFocus(this.inputField);

            Button submit = Button.builder(
                    Component.translatable("matheaufgabenmod.budget.query.submit"),
                    btn -> onCustomSubmitClicked()
            ).bounds(cx - 50, cy + 40, 100, 20).build();
            this.addRenderableWidget(submit);
        }
    }

    private void enterCustomMode() {
        customMode = true;
        this.rebuildWidgets();
    }

    private void submitPreset(int minutes) {
        onSubmit.accept(minutes);
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Enter submits only in custom mode (presets are click-only).
        if (customMode && (event.key() == 257 || event.key() == 335)) {
            onCustomSubmitClicked();
            return true;
        }
        return super.keyPressed(event);
    }

    private void onCustomSubmitClicked() {
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
        int titleY = this.height / 2 - 70;
        int unitY = this.height / 2 + 35;
        int feedbackY = this.height / 2 + 75;

        // Title (1.5x scale) — always shown.
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) cx, (float) titleY);
        ctx.pose().scale(1.5f, 1.5f);
        ctx.drawCenteredString(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.pose().popMatrix();

        // "Minuten" label and feedback only relevant in custom mode.
        if (customMode) {
            ctx.drawCenteredString(tr, Component.translatable("matheaufgabenmod.budget.query.unit"),
                    cx, unitY, 0xFFAAAAAA);
            if (!feedback.getString().isEmpty()) {
                ctx.drawCenteredString(tr, feedback, cx, feedbackY, 0xFFFF5555);
            }
        }
    }
}
