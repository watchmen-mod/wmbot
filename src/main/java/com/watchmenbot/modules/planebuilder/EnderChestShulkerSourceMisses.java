package com.watchmenbot.modules.planebuilder;

final class EnderChestShulkerSourceMisses {
    static final int DEFAULT_CONFIRMED_MISSING_TICKS = 10;

    private final int confirmedMissingTicks;
    private int consecutiveReadyMisses;

    EnderChestShulkerSourceMisses() {
        this(DEFAULT_CONFIRMED_MISSING_TICKS);
    }

    EnderChestShulkerSourceMisses(int confirmedMissingTicks) {
        this.confirmedMissingTicks = Math.max(1, confirmedMissingTicks);
    }

    void reset() {
        consecutiveReadyMisses = 0;
    }

    Phase phase(EnderChestShulkerSourceScan scan, boolean clientReady) {
        if (!clientReady || scan.hasVisibleSource()) {
            reset();
            return Phase.PLACING_ENDER_CHEST_SHULKER;
        }

        consecutiveReadyMisses++;
        return consecutiveReadyMisses >= confirmedMissingTicks
            ? Phase.MISSING_ENDER_CHEST_SHULKER
            : Phase.PLACING_ENDER_CHEST_SHULKER;
    }

    int consecutiveReadyMisses() {
        return consecutiveReadyMisses;
    }
}
