package com.watchmenbot.modules.planebuilder;

final class PlaneTrashCleanupCycle {
    private boolean exhausted;

    void begin() {
        exhausted = false;
    }

    void markExhausted() {
        exhausted = true;
    }

    boolean canStartTrashEdgeCleanup() {
        return !exhausted;
    }
}
