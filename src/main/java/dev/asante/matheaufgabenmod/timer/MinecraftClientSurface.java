package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.history.HistoryEntry;
import dev.asante.matheaufgabenmod.screen.PromptScreen;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Production ClientSurface backed by a real Minecraft client and the scheduler. */
public final class MinecraftClientSurface implements ClientSurface {

    private final Minecraft client;
    private final Supplier<Problem> problemSupplier;
    private final Consumer<HistoryEntry> historyConsumer;
    private final int tasksPerIteration;

    public MinecraftClientSurface(Minecraft client,
                                  Supplier<Problem> problemSupplier,
                                  Consumer<HistoryEntry> historyConsumer,
                                  int tasksPerIteration) {
        this.client = client;
        this.problemSupplier = problemSupplier;
        this.historyConsumer = historyConsumer;
        this.tasksPerIteration = tasksPerIteration;
    }

    @Override
    public boolean hasWorld() { return client.level != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public boolean currentScreenIsPrompt() { return client.screen instanceof PromptScreen; }

    @Override
    public void openPromptScreen(Problem problem) {
        client.setScreen(new PromptScreen(problemSupplier, problem, historyConsumer, tasksPerIteration));
    }
}
