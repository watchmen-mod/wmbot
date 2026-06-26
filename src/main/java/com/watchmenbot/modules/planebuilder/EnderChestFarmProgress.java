package com.watchmenbot.modules.planebuilder;

final class EnderChestFarmProgress {
    static final int OBSIDIAN_PER_ENDER_CHEST = 8;

    private int pendingFarmedObsidian;
    private int lastBuildBlocks = -1;

    void reset() {
        pendingFarmedObsidian = 0;
        lastBuildBlocks = -1;
    }

    void recordFarmedEnderChest(int currentBuildBlocks) {
        reconcile(currentBuildBlocks);
        pendingFarmedObsidian += OBSIDIAN_PER_ENDER_CHEST;
    }

    int effectiveBuildBlocks(int currentBuildBlocks) {
        return effectiveBuildBlocks(currentBuildBlocks, 0);
    }

    int effectiveBuildBlocks(int currentBuildBlocks, int visibleDroppedObsidian) {
        reconcile(currentBuildBlocks);
        return currentBuildBlocks + knownPendingObsidian(visibleDroppedObsidian);
    }

    int additionalEnderChestsNeeded(int currentBuildBlocks, int targetBuildBlocks) {
        return additionalEnderChestsNeeded(currentBuildBlocks, targetBuildBlocks, 0);
    }

    int additionalEnderChestsNeeded(int currentBuildBlocks, int targetBuildBlocks, int visibleDroppedObsidian) {
        return PlaneInventoryQueries.additionalEnderChestsForTarget(
            effectiveBuildBlocks(currentBuildBlocks, visibleDroppedObsidian),
            targetBuildBlocks
        );
    }

    int safeAdditionalEnderChestsNeeded(int currentBuildBlocks, int targetBuildBlocks, int visibleDroppedObsidian) {
        int shortfall = Math.max(0, targetBuildBlocks - effectiveBuildBlocks(currentBuildBlocks, visibleDroppedObsidian));
        return shortfall / OBSIDIAN_PER_ENDER_CHEST;
    }

    boolean canFitAdditionalEnderChest(int currentBuildBlocks, int targetBuildBlocks, int visibleDroppedObsidian) {
        return safeAdditionalEnderChestsNeeded(currentBuildBlocks, targetBuildBlocks, visibleDroppedObsidian) > 0;
    }

    int pendingFarmedObsidian() {
        return pendingFarmedObsidian;
    }

    int knownPendingObsidian(int visibleDroppedObsidian) {
        return Math.max(pendingFarmedObsidian, Math.max(0, visibleDroppedObsidian));
    }

    private void reconcile(int currentBuildBlocks) {
        if (lastBuildBlocks >= 0 && currentBuildBlocks > lastBuildBlocks) {
            pendingFarmedObsidian = Math.max(0, pendingFarmedObsidian - (currentBuildBlocks - lastBuildBlocks));
        }

        lastBuildBlocks = currentBuildBlocks;
    }
}
