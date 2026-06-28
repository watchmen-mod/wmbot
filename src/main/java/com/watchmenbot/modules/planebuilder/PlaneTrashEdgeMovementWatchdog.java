package com.watchmenbot.modules.planebuilder;

final class PlaneTrashEdgeMovementWatchdog {
    static final int DEFAULT_MAX_TARGET_TICKS = 200;

    private final int maxTargetTicks;
    private int targetTicks;

    PlaneTrashEdgeMovementWatchdog() {
        this(DEFAULT_MAX_TARGET_TICKS);
    }

    PlaneTrashEdgeMovementWatchdog(int maxTargetTicks) {
        this.maxTargetTicks = Math.max(1, maxTargetTicks);
    }

    boolean tickTimedOut() {
        if (targetTicks >= maxTargetTicks) {
            reset();
            return true;
        }

        targetTicks++;
        return false;
    }

    void reset() {
        targetTicks = 0;
    }

    int targetTicks() {
        return targetTicks;
    }
}
