package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.screen.PromptScreen;
import net.minecraft.client.MinecraftClient;

import java.util.function.Supplier;

/** Production ClientSurface backed by a real MinecraftClient and the scheduler. */
public final class MinecraftClientSurface implements ClientSurface {

    private final MinecraftClient client;
    private final Supplier<Problem> problemSupplier;

    public MinecraftClientSurface(MinecraftClient client, Supplier<Problem> problemSupplier) {
        this.client = client;
        this.problemSupplier = problemSupplier;
    }

    @Override
    public boolean hasWorld() { return client.world != null; }

    @Override
    public boolean isPaused() { return client.isPaused(); }

    @Override
    public boolean currentScreenIsPrompt() { return client.currentScreen instanceof PromptScreen; }

    @Override
    public void openPromptScreen(Problem problem) {
        client.setScreen(new PromptScreen(problemSupplier, problem));
    }
}
