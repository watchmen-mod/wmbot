package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Blocks;

final class PlaneInventoryPreparation {
    private final PlaneInventory inventory;
    private final PlaneInventoryMover mover;

    PlaneInventoryPreparation(PlaneInventory inventory, PlaneInventoryMover mover) {
        this.inventory = inventory;
        this.mover = mover;
    }

    BuildBlockPreparation prepareBuildBlock() {
        FindItemResult result = inventory.findHotbarBuildBlock();
        if (inventory.findResultMatchesBuildBlock(result)) {
            return buildBlockPreparation(result, true, false, result);
        }

        boolean mainInventorySourceFound = inventory.findMainInventoryBuildBlockSlot() >= 0;
        if (!mainInventorySourceFound) {
            return buildBlockPreparation(result, false, false, result);
        }

        mover.ensureBuildBlockInHotbar();
        return buildBlockPreparation(result, false, true, inventory.findHotbarBuildBlock());
    }

    FindItemResult prepareUsableEnderChest() {
        FindItemResult result = inventory.findEnderChest();
        if (inventory.findResultMatchesBlock(result, Blocks.ENDER_CHEST)) return result;

        if (inventory.findMainInventoryEnderChestSlot() < 0) return result;

        mover.ensureEnderChestInHotbar();
        return inventory.findEnderChest();
    }

    FindItemResult prepareUsableEnderChestShulker() {
        FindItemResult result = inventory.findEnderChestShulkerInHotbar();
        if (inventory.findResultMatchesEnderChestShulker(result)) return result;

        mover.ensureEnderChestShulkerInHotbar();
        return inventory.findEnderChestShulkerInHotbar();
    }

    FindItemResult prepareUsablePickaxe() {
        FindItemResult result = inventory.findHotbarPickaxe();
        if (result != null && result.isHotbar()) {
            return pickaxePreparation(result, true, false, result);
        }

        if (inventory.findMainInventoryPickaxeSlot() < 0) {
            return pickaxePreparation(result, false, false, result);
        }

        mover.ensurePickaxeInHotbar();
        return pickaxePreparation(result, false, true, inventory.findHotbarPickaxe());
    }

    FindItemResult prepareUsableBow() {
        FindItemResult result = inventory.findHotbarBow();
        if (result != null && result.isHotbar()) {
            return bowPreparation(result, true, false, result);
        }

        if (inventory.findMainInventoryBowSlot() < 0) {
            return bowPreparation(result, false, false, result);
        }

        mover.ensureBowInHotbar();
        return bowPreparation(result, false, true, inventory.findHotbarBow());
    }

    static BuildBlockPreparation buildBlockPreparation(
        FindItemResult hotbarResult,
        boolean alreadyUsable,
        boolean mainInventorySourceFound,
        FindItemResult promotedResult
    ) {
        if (alreadyUsable) return BuildBlockPreparation.alreadyUsable(hotbarResult);
        if (!mainInventorySourceFound) return BuildBlockPreparation.missing(hotbarResult);

        return BuildBlockPreparation.afterHotbarPromotion(promotedResult);
    }

    static FindItemResult pickaxePreparation(
        FindItemResult hotbarResult,
        boolean alreadyUsable,
        boolean mainInventorySourceFound,
        FindItemResult promotedResult
    ) {
        if (alreadyUsable) return hotbarResult;
        if (!mainInventorySourceFound) return hotbarResult;

        return promotedResult;
    }

    static FindItemResult bowPreparation(
        FindItemResult hotbarResult,
        boolean alreadyUsable,
        boolean mainInventorySourceFound,
        FindItemResult promotedResult
    ) {
        if (alreadyUsable) return hotbarResult;
        if (!mainInventorySourceFound) return hotbarResult;

        return promotedResult;
    }
}
