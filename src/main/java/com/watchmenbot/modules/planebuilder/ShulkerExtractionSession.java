package com.watchmenbot.modules.planebuilder;

final class ShulkerExtractionSession {
    private int looseEnderChestsBeforeShulker = -1;

    void reset() {
        looseEnderChestsBeforeShulker = -1;
    }

    void ensureBaseline(int currentLooseEnderChests) {
        if (looseEnderChestsBeforeShulker < 0) {
            looseEnderChestsBeforeShulker = currentLooseEnderChests;
        }
    }

    boolean complete(int currentLooseEnderChests, int neededEnderChests) {
        return PlaneInventoryQueries.shulkerExtractionComplete(
            looseEnderChestsBeforeShulker,
            currentLooseEnderChests,
            neededEnderChests
        );
    }

    Phase unavailablePhase(int currentLooseEnderChests) {
        Phase phase = PlaneReplenishDecisions.shulkerExtractionUnavailable(
            looseEnderChestsBeforeShulker,
            currentLooseEnderChests
        );
        if (phase == Phase.MISSING_ENDER_CHEST) reset();
        return phase;
    }

    Phase missingSourcePhase(boolean hasAvailableShulkerSource) {
        if (!hasAvailableShulkerSource) reset();
        return PlaneReplenishDecisions.unavailableShulkerSource(hasAvailableShulkerSource);
    }

    Phase afterShulkerRemoved(int looseEnderChestCount) {
        reset();
        return PlaneReplenishDecisions.afterShulkerRemoved(looseEnderChestCount);
    }

    int baseline() {
        return looseEnderChestsBeforeShulker;
    }
}
