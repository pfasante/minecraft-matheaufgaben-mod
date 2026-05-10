package dev.asante.matheaufgabenmod.timer;

import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.config.SectionSpec;
import dev.asante.matheaufgabenmod.generator.Generator;
import dev.asante.matheaufgabenmod.generator.Problem;
import dev.asante.matheaufgabenmod.generator.Registry;

import java.util.Random;

public final class PromptScheduler {

    private static final int TICKS_PER_MINUTE = 60 * 20;

    private final ModConfig config;
    private final Random rng;
    private int elapsedTicks = 0;

    public PromptScheduler(ModConfig config, Random rng) {
        this.config = config;
        this.rng = rng;
    }

    public void onTick(ClientSurface client) {
        if (!client.hasWorld()) {
            elapsedTicks = 0;
            return;
        }
        if (client.isPaused()) return;
        if (client.currentScreenIsPrompt()) return;
        elapsedTicks++;
        int threshold = config.intervalMinutes() * TICKS_PER_MINUTE;
        if (elapsedTicks >= threshold) {
            elapsedTicks = 0;
            client.openPromptScreen(pickProblem());
        }
    }

    /** Public so {@link dev.asante.matheaufgabenmod.screen.PromptScreen} can request a fresh problem. */
    public Problem pickProblem() {
        String specStr = config.sectionSpecs().get(rng.nextInt(config.sectionSpecs().size()));
        SectionSpec spec = SectionSpec.parse(specStr);
        Generator gen = Registry.get(spec.type());
        Object params = gen.parseParams(spec.params());
        return gen.generate(rng, params).get(0);
    }
}
