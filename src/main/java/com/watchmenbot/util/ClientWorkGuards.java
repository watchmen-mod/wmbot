package com.watchmenbot.util;

import net.minecraft.client.MinecraftClient;

public final class ClientWorkGuards {
    private ClientWorkGuards() {
    }

    public static boolean worldReady(MinecraftClient mc) {
        return mc != null && mc.player != null && mc.world != null;
    }

    public static boolean interactionReady(MinecraftClient mc) {
        return worldReady(mc) && mc.interactionManager != null;
    }
}
