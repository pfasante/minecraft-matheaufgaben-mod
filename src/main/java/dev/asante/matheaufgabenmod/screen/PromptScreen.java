package dev.asante.matheaufgabenmod.screen;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.history.HistoryEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PromptScreen extends Screen {

    private final Supplier<Problem> problemSupplier;
    private final Consumer<HistoryEntry> historyConsumer;
    private final int totalTasks;
    private final String player;
    private Problem currentProblem;
    private EditBox inputField;
    private Component feedback = Component.empty();
    private long attemptStartNanos;
    /** 0-based index of the task currently being attempted. Advances only on correct answer. */
    private int currentIndex = 0;

    public PromptScreen(Supplier<Problem> problemSupplier, Problem initialProblem,
                        Consumer<HistoryEntry> historyConsumer, int totalTasks, String player) {
        super(Component.translatable("matheaufgabenmod.prompt.title"));
        this.problemSupplier = problemSupplier;
        this.currentProblem = initialProblem;
        this.historyConsumer = historyConsumer;
        this.totalTasks = Math.max(1, totalTasks);
        this.player = player;
        this.attemptStartNanos = System.nanoTime();
    }

    public static boolean checkAnswer(Problem problem, String guess) {
        return guess.trim().equals(problem.answer());
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
                Component.translatable("matheaufgabenmod.prompt.title")
        );
        this.inputField.setMaxLength(16);
        this.addRenderableWidget(this.inputField);
        this.setInitialFocus(this.inputField);

        Button submit = Button.builder(
                Component.translatable("matheaufgabenmod.prompt.submit"),
                btn -> onSubmit()
        ).bounds(cx - 50, cy + 40, 100, 20).build();
        this.addRenderableWidget(submit);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Main Enter (257) and numpad Enter (335) both submit.
        if (event.key() == 257 || event.key() == 335) {
            onSubmit();
            return true;
        }
        return super.keyPressed(event);
    }

    private void onSubmit() {
        String given = inputField.getValue();
        boolean correct = checkAnswer(currentProblem, given);
        Duration duration = Duration.ofNanos(System.nanoTime() - attemptStartNanos);
        historyConsumer.accept(HistoryEntry.fromAttempt(currentProblem, player, given, correct, duration));

        if (correct) {
            if (currentIndex + 1 < totalTasks) {
                // More tasks remaining in this iteration — advance to the next.
                currentIndex++;
                currentProblem = problemSupplier.get();
                inputField.setValue("");
                feedback = Component.empty();
                attemptStartNanos = System.nanoTime();
                return;
            }
            Minecraft.getInstance().setScreen(null);
            return;
        }
        currentProblem = problemSupplier.get();
        inputField.setValue("");
        feedback = Component.translatable("matheaufgabenmod.prompt.wrong");
        attemptStartNanos = System.nanoTime();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        Font tr = this.font;
        int cx = this.width / 2;
        int titleY = this.height / 2 - 70;
        int progressY = this.height / 2 - 50;
        int promptY = this.height / 2 - 30;
        int feedbackY = this.height / 2 + 70;

        // Title (1.5x scale)
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) cx, (float) titleY);
        ctx.pose().scale(1.5f, 1.5f);
        ctx.centeredText(tr, this.title, 0, 0, 0xFFFFFFFF);
        ctx.pose().popMatrix();

        // Progress indicator "Aufgabe X von Y" — only when more than 1 task.
        if (totalTasks > 1) {
            Component progress = Component.translatable("matheaufgabenmod.prompt.progress",
                    currentIndex + 1, totalTasks);
            ctx.centeredText(tr, progress, cx, progressY, 0xFFAAAAAA);
        }

        // Prompt text (2x scale)
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) cx, (float) promptY);
        ctx.pose().scale(2.0f, 2.0f);
        ctx.centeredText(tr, currentProblem.prompt() + " =", 0, 0, 0xFFFFFFFF);
        ctx.pose().popMatrix();

        // Feedback (red)
        if (!feedback.getString().isEmpty()) {
            ctx.centeredText(tr, feedback, cx, feedbackY, 0xFFFF5555);
        }
    }
}
