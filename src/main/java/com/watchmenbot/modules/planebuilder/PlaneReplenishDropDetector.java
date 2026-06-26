package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

final class PlaneReplenishDropDetector {
    private final PlaneDroppedItemScanner cleanupScanner;
    private final PlaneDroppedItemScanner shulkerScanner;

    PlaneReplenishDropDetector() {
        cleanupScanner = new PlaneDroppedItemScanner(PlanePickupSettings.REPLENISH_CLEANUP_SCAN_RADIUS, this::matchesCleanupDrop);
        shulkerScanner = new PlaneDroppedItemScanner(PlanePickupSettings.SHULKER_RECOVERY_SCAN_RADIUS, this::matchesShulkerDrop);
    }

    ItemEntity nearestCleanupDrop() {
        return cleanupScanner.nearestMatchingDrop();
    }

    int nearbyObsidianDropCount() {
        return cleanupScanner.matchingDrops()
            .stream()
            .map(ItemEntity::getStack)
            .filter(stack -> stack.isOf(Items.OBSIDIAN))
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    ItemEntity nearestShulkerDrop() {
        return shulkerScanner.nearestMatchingDrop();
    }

    boolean matchesCleanupDrop(ItemEntity item) {
        if (item == null || !item.isAlive() || item.isRemoved()) return false;
        return matchesCleanupStack(item.getStack());
    }

    boolean matchesShulkerDrop(ItemEntity item) {
        if (item == null || !item.isAlive() || item.isRemoved()) return false;
        return PlaneItemClassifier.isShulkerBoxStack(item.getStack());
    }

    static boolean matchesCleanupStack(ItemStack stack) {
        return PlaneItemClassifier.isReplenishCleanupDrop(stack);
    }

    static boolean matchesCleanupKind(boolean obsidian, boolean shulkerBox) {
        return PlaneItemClassifier.cleanupDropKind(obsidian, shulkerBox);
    }
}
