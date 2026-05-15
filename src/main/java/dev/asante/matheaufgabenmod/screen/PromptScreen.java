package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.history.HistoryEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PromptScreen extends Screen {

    private final Supplier<Problem> problemSupplier;
    private final Consumer<HistoryEntry> historyConsumer;
    private final int totalTasks;
    private Problem currentProblem;
    private TextFieldWidget inputField;
    private Text feedback = Text.empty();
    private long attemptStartNanos;
    /** 0-based index of the task currently being attempted. Advances only on correct answer. */
    private int currentIndex = 0;

    public PromptScreen(Supplier<Problem> problemSupplier, Problem initialProblem,
                        Consumer<HistoryEntry> historyConsumer, int totalTasks) {
        super(Text.translatable("matheaufgabenmod.prompt.title"));
        this.problemSupplier = problemSupplier;
        this.currentProblem = initialProblem;
        this.historyConsumer = historyConsumer;
        this.totalTasks = Math.max(1, totalTasks);
        this.attemptStartNanos = System.nanoTime();
    }

    public static boolean checkAnswer(Problem problem, String guess) {
        return guess.trim().equals(problem.answer());
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
                Text.translatable("matheaufgabenmod.prompt.title")
        );
        this.inputField.setMaxLength(16);
        this.addDrawableChild(this.inputField);
        this.setInitialFocus(this.inputField);

        ButtonWidget submit = ButtonWidget.builder(
                Text.translatable("matheaufgabenmod.prompt.submit"),
                btn -> onSubmit()
        ).dimensions(cx - 50, cy + 40, 100, 20).build();
        this.addDrawableChild(submit);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (keyCode == 257 || keyCode == 335) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSubmit() {
        String given = inputField.getText();
        boolean correct = checkAnswer(currentProblem, given);
        Duration duration = Duration.ofNanos(System.nanoTime() - attemptStartNanos);
        historyConsumer.accept(HistoryEntry.fromAttempt(currentProblem, given, correct, duration));

        if (correct) {
            if (currentIndex + 1 < totalTasks) {
                // More tasks remaining in this iteration — advance to the next.
                currentIndex++;
                currentProblem = problemSupplier.get();
                inputField.setText("");
                feedback = Text.empty();
                attemptStartNanos = System.nanoTime();
                return;
            }
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        currentProblem = problemSupplier.get();
        inputField.setText("");
        feedback = Text.translatable("matheaufgabenmod.prompt.wrong");
        attemptStartNanos = System.nanoTime();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 70;
        int progressY = this.height / 2 - 50;
        int promptY = this.height / 2 - 30;
        int feedbackY = this.height / 2 + 70;

        // Title (1.5x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, titleY, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        // Progress indicator "Aufgabe X von Y" — only when more than 1 task.
        if (totalTasks > 1) {
            Text progress = Text.translatable("matheaufgabenmod.prompt.progress",
                    currentIndex + 1, totalTasks);
            ctx.drawCenteredTextWithShadow(tr, progress, cx, progressY, 0xFFAAAAAA);
        }

        // Prompt text (2x scale)
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, promptY, 0);
        ctx.getMatrices().scale(2.0f, 2.0f, 1.0f);
        ctx.drawCenteredTextWithShadow(tr, currentProblem.prompt() + " =", 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();

        // Feedback (red)
        if (!feedback.getString().isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
