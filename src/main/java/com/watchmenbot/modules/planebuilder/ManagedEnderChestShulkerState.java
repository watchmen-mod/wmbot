package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class ManagedEnderChestShulkerState {
    private BlockPos placedHole;
    private boolean postBreakRecovery;
    private boolean openedOrExtracted;
    private boolean failedBeforeOpenRecovery;
    private int failedOpenAttempts;

    void reset() {
        placedHole = null;
        postBreakRecovery = false;
        openedOrExtracted = false;
        failedBeforeOpenRecovery = false;
        failedOpenAttempts = 0;
    }

    void markPlaced(BlockPos hole) {
        if (hole == null) return;

        placedHole = hole.toImmutable();
        postBreakRecovery = false;
        failedBeforeOpenRecovery = false;
    }

    void markOpenedOrExtracted() {
        if (placedHole != null || postBreakRecovery) openedOrExtracted = true;
    }

    void markPostBreakRecovery() {
        if (!openedOrExtracted) {
            failedBeforeOpenRecovery = true;
            failedOpenAttempts++;
        }
        placedHole = null;
        postBreakRecovery = true;
    }

    void clearPostBreakRecovery() {
        postBreakRecovery = false;
    }

    void clearFailedBeforeOpenRecovery() {
        failedBeforeOpenRecovery = false;
    }

    void markRecovered() {
        reset();
    }

    boolean placedAt(BlockPos hole, ServiceHoleContext.Status status) {
        return placedHole != null
            && hole != null
            && placedHole.equals(hole)
            && status == ServiceHoleContext.Status.READY_SHULKER;
    }

    boolean trackingHole(BlockPos hole) {
        return placedHole != null && hole != null && placedHole.equals(hole);
    }

    boolean postBreakRecovery() {
        return postBreakRecovery;
    }

    boolean reservesInventorySlot() {
        return placedHole != null || postBreakRecovery;
    }

    boolean openedOrExtracted() {
        return openedOrExtracted;
    }

    boolean failedBeforeOpenRecovery() {
        return failedBeforeOpenRecovery;
    }

    int failedOpenAttempts() {
        return failedOpenAttempts;
    }

    boolean suppressesRefill(BlockPos hole, ServiceHoleContext.Status status) {
        return placedAt(hole, status) || postBreakRecovery;
    }
}
