// src/main/java/dev/asante/matheaufgabenmod/MatheaufgabenMod.java
package dev.asante.matheaufgabenmod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MatheaufgabenMod implements ClientModInitializer {

    public static final String MOD_ID = "matheaufgabenmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[matheaufgabenmod] initialising — placeholder, full wiring lands in Task 13");
    }
}
