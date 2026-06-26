package com.watchmenbot.modules.planebuilder;

final class PlaneReplenishTargetPolicy {
    private PlaneReplenishTargetPolicy() {
    }

    static int effectiveTarget(
        int configuredTarget,
        boolean useAvailableSafeInventorySpace,
        int replenishMinBuildBlocks,
        int buildBlockCapacity,
        int safeBuildBlockCapacity
    ) {
        int target = useAvailableSafeInventorySpace ? safeBuildBlockCapacity : configuredTarget;
        int capacity = useAvailableSafeInventorySpace ? safeBuildBlockCapacity : buildBlockCapacity;
        return PlaneInventoryQueries.effectiveReplenishTarget(target, replenishMinBuildBlocks, capacity);
    }

    static int safeBuildBlockCapacity(
        int currentBuildBlocks,
        int partialBuildBlockRoom,
        int emptyInventorySlots,
        int buildBlockMaxCount,
        boolean hasPartialLooseEnderChestStack,
        boolean shulkerSourceMayBeNeeded,
        boolean cleanupMayNeedEmptySlot
    ) {
        int reservedEmptySlots = partialBuildBlockRoom > 0 ? 1 : 0;
        if (!hasPartialLooseEnderChestStack) reservedEmptySlots++;
        if (shulkerSourceMayBeNeeded) reservedEmptySlots++;
        if (cleanupMayNeedEmptySlot) reservedEmptySlots++;

        int fillableEmptySlots = Math.max(0, emptyInventorySlots - reservedEmptySlots);
        return currentBuildBlocks + partialBuildBlockRoom + fillableEmptySlots * buildBlockMaxCount;
    }
}
