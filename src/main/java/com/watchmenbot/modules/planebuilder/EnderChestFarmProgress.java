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
        reconcile(currentBuildBlocks);
        return currentBuildBlocks + pendingFarmedObsidian;
    }

    int additionalEnderChestsNeeded(int currentBuildBlocks, int targetBuildBlocks) {
        return PlaneInventoryQueries.additionalEnderChestsForTarget(
            effectiveBuildBlocks(currentBuildBlocks),
            targetBuildBlocks
        );
    }

    int pendingFarmedObsidian() {
        return pendingFarmedObsidian;
    }

    private void reconcile(int currentBuildBlocks) {
        if (lastBuildBlocks >= 0 && currentBuildBlocks > lastBuildBlocks) {
            pendingFarmedObsidian = Math.max(0, pendingFarmedObsidian - (currentBuildBlocks - lastBuildBlocks));
        }

        lastBuildBlocks = currentBuildBlocks;
    }
}
