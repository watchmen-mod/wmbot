package com.watchmenbot.modules.planebuilder;

final class PlaneInventoryQueries {
    private PlaneInventoryQueries() {
    }

    static int requiredEnderChestsForTarget(int currentBuildBlocks, int targetBuildBlocks) {
        int shortfall = Math.max(0, targetBuildBlocks - currentBuildBlocks);
        return Math.max(1, (shortfall + 7) / 8);
    }

    static int additionalEnderChestsForTarget(int currentBuildBlocks, int targetBuildBlocks) {
        int shortfall = Math.max(0, targetBuildBlocks - currentBuildBlocks);
        return (shortfall + 7) / 8;
    }

    static int effectiveReplenishTarget(int configuredTarget, int replenishMinBuildBlocks, int buildBlockCapacity) {
        int target = Math.max(configuredTarget, replenishMinBuildBlocks);
        return Math.min(target, Math.max(replenishMinBuildBlocks, buildBlockCapacity));
    }

    static boolean shulkerExtractionComplete(int baselineLooseEnderChests, int currentLooseEnderChests, int neededEnderChests) {
        return currentLooseEnderChests >= neededEnderChests
            && currentLooseEnderChests > baselineLooseEnderChests;
    }

    static boolean cleanupDropPickupable(
        boolean obsidian,
        boolean shulkerBox,
        boolean emptySlot,
        boolean partialObsidianStack
    ) {
        if (shulkerBox) return emptySlot;
        if (obsidian) return emptySlot || partialObsidianStack;
        return false;
    }

    static boolean enderChestPickupPreservesShulkerSlot(boolean partialEnderChestStack, int emptySlots) {
        if (emptySlots <= 0) return false;
        return partialEnderChestStack || emptySlots >= 2;
    }

    static int bestEnderChestShulkerHotbarSlot(int[] enderChestCountsBySlot) {
        int bestSlot = -1;
        int bestEnderChestCount = Integer.MAX_VALUE;
        for (int slot = 0; slot < enderChestCountsBySlot.length; slot++) {
            int enderChestCount = enderChestCountsBySlot[slot];
            if (enderChestCount > 0 && enderChestCount < bestEnderChestCount) {
                bestSlot = slot;
                bestEnderChestCount = enderChestCount;
            }
        }

        return bestSlot;
    }
}
