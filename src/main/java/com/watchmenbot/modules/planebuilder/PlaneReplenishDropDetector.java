package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;

final class PlaneReplenishDropDetector {
    private final PlaneDroppedItemScanner scanner;

    PlaneReplenishDropDetector() {
        scanner = new PlaneDroppedItemScanner(PlanePickupSettings.REPLENISH_CLEANUP_SCAN_RADIUS, this::matchesCleanupDrop);
    }

    ItemEntity nearestCleanupDrop() {
        return scanner.nearestMatchingDrop();
    }

    ItemEntity nearestShulkerDrop() {
        return scanner.nearestMatchingDrop(this::matchesShulkerDrop);
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
