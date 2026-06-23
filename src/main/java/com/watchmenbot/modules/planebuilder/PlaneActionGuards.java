package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ShulkerBoxScreenHandler;

final class PlaneActionGuards {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    boolean readyForWorldAction() {
        return clientReady()
            && !playerUsingItem()
            && mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    boolean readyForHotbarMutation() {
        return readyForWorldAction();
    }

    boolean readyForUseAction() {
        return readyForWorldAction();
    }

    boolean readyForBowStart() {
        return readyForWorldAction();
    }

    boolean readyForBowContinue() {
        return clientReady()
            && mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }

    boolean readyForManagedShulkerScreen() {
        return clientReady()
            && !playerUsingItem()
            && mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler;
    }

    boolean managedScreenOpen() {
        return clientReady()
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    boolean safeToCloseManagedScreen() {
        return clientReady()
            && safeToCloseManagedScreen(
                mc.player.currentScreenHandler == mc.player.playerScreenHandler,
                mc.player.currentScreenHandler.getCursorStack().isEmpty()
            );
    }

    void closeManagedScreenForSafety() {
        if (safeToCloseManagedScreen()) mc.player.closeHandledScreen();
    }

    boolean clientReady() {
        return mc.player != null && mc.world != null && mc.interactionManager != null;
    }

    boolean playerUsingItem() {
        return mc.player.isUsingItem() || mc.options.useKey.isPressed();
    }

    static boolean safeToCloseManagedScreen(boolean onPlayerInventoryScreen, boolean cursorEmpty) {
        return !onPlayerInventoryScreen && cursorEmpty;
    }
}
