package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;

final class PlaneReplenishCleanupWorkflow {
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup;
    private final PlaneTrashEdgeWorkflow trashCleanup;

    PlaneReplenishCleanupWorkflow(
        PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup,
        PlaneTrashEdgeWorkflow trashCleanup
    ) {
        this.dropCleanup = dropCleanup;
        this.trashCleanup = trashCleanup;
    }

    void reset() {
        dropCleanup.reset();
        trashCleanup.resetAll();
    }

    void beginCleanupCycle() {
        trashCleanup.beginCleanupCycle();
    }

    void pauseMovement() {
        trashCleanup.pauseMovement();
    }

    Phase pickUpReplenishDrops() {
        return dropCleanup.tick();
    }

    Phase moveToTrashEdge() {
        return trashCleanup.moveToEdge();
    }

    Phase dropTrashOffEdge() {
        return trashCleanup.dropOffEdge();
    }

    Phase waitForTrashToFall() {
        return trashCleanup.waitForTrashToFall();
    }
}
