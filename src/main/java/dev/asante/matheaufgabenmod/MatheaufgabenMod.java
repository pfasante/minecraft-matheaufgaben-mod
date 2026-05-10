package dev.asante.matheaufgabenmod;

import dev.asante.matheaufgabenmod.config.ConfigLoader;
import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.timer.ClientSurface;
import dev.asante.matheaufgabenmod.timer.MinecraftClientSurface;
import dev.asante.matheaufgabenmod.timer.PromptScheduler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Random;

public final class MatheaufgabenMod implements ClientModInitializer {

    public static final String MOD_ID = "matheaufgabenmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        ModConfig config = ConfigLoader.loadOrCreate(configPath);

        Random rng = new Random();
        PromptScheduler scheduler = new PromptScheduler(config, rng);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSurface surface = new MinecraftClientSurface(client, scheduler::pickProblem);
            scheduler.onTick(surface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} section spec(s)",
                MOD_ID, config.intervalMinutes(), config.sectionSpecs().size());
    }
}
