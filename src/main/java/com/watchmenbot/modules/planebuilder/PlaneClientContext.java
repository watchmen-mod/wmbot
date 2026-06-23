package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

final class PlaneClientContext {
    private final MinecraftClient mc;

    PlaneClientContext() {
        this(MinecraftClient.getInstance());
    }

    PlaneClientContext(MinecraftClient mc) {
        this.mc = mc;
    }

    MinecraftClient client() {
        return mc;
    }

    ClientPlayerEntity player() {
        return mc.player;
    }

    ClientWorld world() {
        return mc.world;
    }

    boolean worldReady() {
        return mc.player != null && mc.world != null;
    }

    boolean interactionReady() {
        return worldReady() && mc.interactionManager != null;
    }
}
