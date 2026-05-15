package dev.asante.matheaufgabenmod;

import dev.asante.matheaufgabenmod.budget.BudgetHudRenderer;
import dev.asante.matheaufgabenmod.budget.BudgetTracker;
import dev.asante.matheaufgabenmod.budget.MinecraftBudgetSurface;
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

        // Budget timer wiring.
        BudgetTracker budgetTracker = new BudgetTracker();
        BudgetHudRenderer budgetHud = new BudgetHudRenderer();
        budgetHud.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Math-prompt scheduler tick.
            ClientSurface promptSurface = new MinecraftClientSurface(
                    client, scheduler::pickProblem, historyLogger::logAttempt,
                    config.tasksPerIteration());
            scheduler.onTick(promptSurface);

            // Budget tick.
            MinecraftBudgetSurface budgetSurface = new MinecraftBudgetSurface(
                    client, budgetHud.stateHolder());
            budgetTracker.onTick(budgetSurface);
        });

        LOGGER.info("[{}] initialised — interval={} min, {} tasks/iteration, {} section spec(s), history={}, budget-timer enabled",
                MOD_ID, config.intervalMinutes(), config.tasksPerIteration(),
                config.sectionSpecs().size(), historyPath);
    }
}
