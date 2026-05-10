package dev.asante.matheaufgabenmod;

import dev.asante.matheaufgabenmod.config.ConfigLoader;
import dev.asante.matheaufgabenmod.config.ModConfig;
import dev.asante.matheaufgabenmod.history.HistoryLogger;
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
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(MOD_ID + ".json");
        Path historyPath = configDir.resolve(MOD_ID + "-history.log");
        ModConfig config = ConfigLoader.loadOrCreate(configPath);
        HistoryLogger historyLogger = new HistoryLogger(historyPath);

        Random rng = new Random();
        PromptScheduler scheduler = new PromptScheduler(config, rng);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSurface surface = new MinecraftClientSurface(
                    client, scheduler::pickProblem, historyLogger::logAttempt);
            scheduler.onTick(surface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} section spec(s), history={}",
                MOD_ID, config.intervalMinutes(), config.sectionSpecs().size(), historyPath);
    }
}
