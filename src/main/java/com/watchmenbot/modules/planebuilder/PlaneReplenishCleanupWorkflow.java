package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;

final class PlaneReplenishCleanupWorkflow {
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerCleanup;
    private final PlaneInventoryAccess inventory;
    private final ManagedEnderChestShulkerState managedShulker;
    private final PlaneTrashEdgeWorkflow trashCleanup;

    PlaneReplenishCleanupWorkflow(
        PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup,
        PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerCleanup,
        PlaneInventoryAccess inventory,
        ManagedEnderChestShulkerState managedShulker,
        PlaneTrashEdgeWorkflow trashCleanup
    ) {
        this.dropCleanup = dropCleanup;
        this.managedShulkerCleanup = managedShulkerCleanup;
        this.inventory = inventory;
        this.managedShulker = managedShulker;
        this.trashCleanup = trashCleanup;
    }

    void reset() {
        dropCleanup.reset();
        managedShulkerCleanup.reset();
        trashCleanup.resetAll();
    }

    void beginCleanupCycle() {
        trashCleanup.beginCleanupCycle();
    }

    void pauseMovement() {
        trashCleanup.pauseMovement();
    }

    Phase pickUpReplenishDrops() {
        if (managedShulker.reservesInventorySlot()) {
            if (inventory.scanEnderChestShulkerSources().hasVisibleSource()) {
                managedShulker.markRecovered();
                managedShulkerCleanup.reset();
            }
            else if (managedShulkerCleanup.hasTarget()) {
                return managedShulkerCleanup.tick();
            }
        }

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
