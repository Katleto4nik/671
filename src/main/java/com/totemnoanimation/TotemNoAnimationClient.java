package com.totemnoanimation;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TotemNoAnimationClient implements ClientModInitializer {
    public static final String MOD_ID = "totemnoanimation";
    public static final Logger LOGGER = LoggerFactory.getLogger("Totem no animation");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Totem no animation loaded");
    }
}
