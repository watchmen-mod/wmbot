package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;

final class PlaneInventoryMover {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneActionGuards guards;
    private final PlaneInventory inventory;

    PlaneInventoryMover(PlaneActionGuards guards, PlaneInventory inventory) {
        this.guards = guards;
        this.inventory = inventory;
    }

    void ensureBuildBlockInHotbar() {
        ensureInventorySlotInHotbar(findInventoryBuildBlockSlot());
    }

    void ensureEnderChestShulkerInHotbar() {
        ensureInventorySlotInHotbar(findInventoryEnderChestShulkerSlot());
    }

    void ensureEnderChestInHotbar() {
        ensureInventorySlotInHotbar(findInventoryEnderChestSlot());
    }

    void ensurePickaxeInHotbar() {
        ensureInventorySlotInHotbar(findInventoryPickaxeSlot());
    }

    void ensureBowInHotbar() {
        ensureInventorySlotInHotbar(findInventoryBowSlot());
    }

    private void ensureInventorySlotInHotbar(int sourceSlot) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!guards.readyForHotbarMutation()) return;

        int hotbarSlot = findHotbarSwapTarget();
        if (sourceSlot < 0 || hotbarSlot < 0) return;

        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            playerInventoryScreenSlot(sourceSlot),
            hotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
    }

    private int findInventoryBuildBlockSlot() {
        return inventory.findMainInventoryBuildBlockSlot();
    }

    private int findInventoryEnderChestSlot() {
        return inventory.findMainInventoryEnderChestSlot();
    }

    private int findInventoryEnderChestShulkerSlot() {
        return inventory.findMainInventoryEnderChestShulkerSlot();
    }

    private int findInventoryPickaxeSlot() {
        return inventory.findMainInventoryPickaxeSlot();
    }

    private int findInventoryBowSlot() {
        return inventory.findMainInventoryBowSlot();
    }

    private int findHotbarSwapTarget() {
        int selected = mc.player.getInventory().getSelectedSlot();
        boolean[] emptyHotbarSlots = new boolean[9];
        for (int slot = 0; slot <= 8; slot++) {
            emptyHotbarSlots[slot] = mc.player.getInventory().getStack(slot).isEmpty();
        }

        return hotbarSwapTarget(selected, emptyHotbarSlots);
    }

    static int hotbarSwapTarget(int selectedSlot, boolean[] emptyHotbarSlots) {
        if (selectedSlot >= 0 && selectedSlot <= 8 && emptyHotbarSlots[selectedSlot]) return selectedSlot;

        int hotbarSlots = Math.min(9, emptyHotbarSlots.length);
        for (int slot = 0; slot < hotbarSlots; slot++) {
            if (emptyHotbarSlots[slot]) return slot;
        }

        return selectedSlot >= 0 && selectedSlot <= 8 ? selectedSlot : -1;
    }

    private int playerInventoryScreenSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }
}
