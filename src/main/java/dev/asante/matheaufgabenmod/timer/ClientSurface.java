package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.generator.Problem;

/**
 * Narrow façade over MinecraftClient — only the bits the scheduler needs. Lets
 * us unit-test scheduling logic without booting Minecraft.
 */
public interface ClientSurface {
    boolean hasWorld();
    boolean isPaused();
    boolean currentScreenIsPrompt();
    void openPromptScreen(Problem problem);
}
