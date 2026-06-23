package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

final class ShulkerScreenInteractor {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneInventory inventory;
    private final PlaneActionGuards guards;

    ShulkerScreenInteractor(PlaneInventory inventory, PlaneActionGuards guards) {
        this.inventory = inventory;
        this.guards = guards;
    }

    boolean playerHasShulkerOpen() {
        return mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler;
    }

    boolean waitingForManagedScreen() {
        return !guards.readyForManagedShulkerScreen()
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    ShulkerBoxScreenHandler openHandler() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        return handler instanceof ShulkerBoxScreenHandler shulker ? shulker : null;
    }

    int findEnderChestSlot(ShulkerBoxScreenHandler handler) {
        return inventory.findOpenShulkerEnderChestSlot(handler);
    }

    void quickMove(ShulkerBoxScreenHandler handler, int slot) {
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    void close() {
        mc.player.closeHandledScreen();
    }

    boolean onPlayerInventoryScreen() {
        return mc.player.currentScreenHandler == mc.player.playerScreenHandler;
    }
}
