package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;

import java.util.function.IntSupplier;

final class PlaneKitbotRefillWorkflow {
    private final PlaneKitbotMessenger messenger;
    private final PlaneKitbotSupplyProbe supplyProbe;
    private final PlaneKitbotDeliveryTracker delivery;
    private final PlaneKitbotTeleportAcceptWorkflow teleportAccept;
    private final IntSupplier requiredEnderChestSupply;

    private PlaneKitbotTeleportAcceptWorkflow.AcceptResult lastTeleportAcceptResult =
        new PlaneKitbotTeleportAcceptWorkflow.AcceptResult(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, null);

    PlaneKitbotRefillWorkflow(
        PlaneBuilderSettings.KitbotRefill settings,
        EnderChestSupplyInventory supply,
        PlaneInventory inventory,
        PlaneBuilderSettings.Replenish replenishSettings
    ) {
        this(
            new PlaneKitbotMessenger(settings),
            supply,
            PlaneKitbotSupplyProbes.from(supply),
            PlaneKitbotDroppedSupplyTrackers.from(new PlaneKitbotDroppedSupplyDetector(), 0),
            null,
            () -> inventory.requiredEnderChestsForTarget(PlaneReplenishTargets.effectiveTarget(inventory, replenishSettings))
        );
    }

    PlaneKitbotRefillWorkflow(PlaneKitbotMessenger messenger, EnderChestSupplyInventory supply) {
        this(
            messenger,
            supply,
            PlaneKitbotSupplyProbes.from(supply),
            PlaneKitbotDroppedSupplyTrackers.none(),
            new PlaneKitbotTeleportAcceptWorkflow(messenger),
            () -> 1
        );
    }

    PlaneKitbotRefillWorkflow(
        PlaneKitbotMessenger messenger,
        EnderChestSupplyInventory supply,
        PlaneDroppedItemPickupWorkflow<ItemEntity> pickupWorkflow
    ) {
        this(
            messenger,
            supply,
            PlaneKitbotSupplyProbes.from(supply),
            PlaneKitbotDroppedSupplyTrackers.from(pickupWorkflow),
            new PlaneKitbotTeleportAcceptWorkflow(messenger),
            () -> 1
        );
    }

    PlaneKitbotRefillWorkflow(
        PlaneKitbotMessenger messenger,
        EnderChestSupplyInventory supply,
        PlaneKitbotSupplyProbe supplyProbe,
        PlaneDroppedItemPickupWorkflow<ItemEntity> pickupWorkflow,
        PlaneKitbotTeleportAcceptWorkflow teleportAccept
    ) {
        this(messenger, supply, supplyProbe, PlaneKitbotDroppedSupplyTrackers.from(pickupWorkflow), teleportAccept, () -> 1);
    }

    PlaneKitbotRefillWorkflow(
        PlaneKitbotMessenger messenger,
        EnderChestSupplyInventory supply,
        PlaneKitbotSupplyProbe supplyProbe,
        PlaneKitbotDroppedSupplyTracker droppedSupply,
        PlaneKitbotTeleportAcceptWorkflow teleportAccept
    ) {
        this(messenger, supply, supplyProbe, droppedSupply, teleportAccept, () -> 1);
    }

    PlaneKitbotRefillWorkflow(
        PlaneKitbotMessenger messenger,
        EnderChestSupplyInventory supply,
        PlaneKitbotSupplyProbe supplyProbe,
        PlaneKitbotDroppedSupplyTracker droppedSupply,
        PlaneKitbotTeleportAcceptWorkflow teleportAccept,
        IntSupplier requiredEnderChestSupply
    ) {
        this.messenger = messenger;
        this.supplyProbe = supplyProbe == null ? PlaneKitbotSupplyProbes.from(supply) : supplyProbe;
        delivery = new PlaneKitbotDeliveryTracker(this.supplyProbe, droppedSupply);
        this.teleportAccept = teleportAccept == null ? new PlaneKitbotTeleportAcceptWorkflow(messenger) : teleportAccept;
        this.requiredEnderChestSupply = requiredEnderChestSupply == null ? () -> 1 : requiredEnderChestSupply;
    }

    void reset() {
        lastTeleportAcceptResult =
            new PlaneKitbotTeleportAcceptWorkflow.AcceptResult(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, null);
        teleportAccept.reset();
        delivery.reset();
    }

    boolean pending() {
        return delivery.pending();
    }

    void beginReplenishCycle() {
        delivery.beginReplenishCycle();
    }

    boolean hasQueuedTeleportAccept() {
        return teleportAccept.hasQueuedCommand();
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult handleTeleportPrompt(String message) {
        return teleportAccept.handlePrompt(message);
    }

    void tickQueuedTeleportAccept() {
        tickTeleportAccept();
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult consumeTeleportAcceptResult() {
        PlaneKitbotTeleportAcceptWorkflow.AcceptResult result = lastTeleportAcceptResult;
        lastTeleportAcceptResult =
            new PlaneKitbotTeleportAcceptWorkflow.AcceptResult(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, null);
        return result;
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return messenger.ignoredTeleportPrompt(message);
    }

    Phase missingSupplyPhase(Phase missingPhase) {
        return missingSupplyPhase(missingPhase, false);
    }

    Phase missingSupplyPhase(Phase missingPhase, boolean managedSupplyActive) {
        if (missingPhase != Phase.MISSING_ENDER_CHEST_SHULKER && missingPhase != Phase.MISSING_ENDER_CHEST) return missingPhase;
        boolean usableSupply = hasUsableSupply();
        if (usableSupply) {
            markSuppliesAvailable();
            return Phase.SELECTING_REPLENISH_SOURCE;
        }

        Phase refillPhase = PlaneKitbotRefillDecisions.missingSupplyPhase(messenger.enabled(), false, managedSupplyActive);
        if (refillPhase == Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL || refillPhase == Phase.SELECTING_REPLENISH_SOURCE) {
            return refillPhase;
        }

        return missingPhase;
    }

    Phase afterServiceHoleClosed() {
        return requestOrMissingSupply();
    }

    Phase waitForDelivery() {
        Phase next = delivery.inventoryDeliveryPhase();
        if (next != Phase.WAITING_FOR_KITBOT_REFILL) {
            markSuppliesAvailable();
            return next;
        }

        tickTeleportAccept();

        next = PlaneKitbotRefillDecisions.waitingPhase(false, false, delivery.hasNewDeliveryDrop());
        if (next == Phase.PICKING_UP_KITBOT_REFILL) {
            teleportAccept.reset();
            return delivery.tickPickup();
        }

        return requestOrMissingSupply();
    }

    Phase pickUpDelivery() {
        Phase next = delivery.inventoryDeliveryPhase();
        if (next != Phase.WAITING_FOR_KITBOT_REFILL) {
            markSuppliesAvailable();
            return next;
        }

        tickTeleportAccept();

        next = delivery.tickPickup();
        if (next == Phase.PICKING_UP_KITBOT_REFILL) teleportAccept.reset();
        return next == Phase.PICKING_UP_KITBOT_REFILL ? next : requestOrMissingSupply();
    }

    private void tickTeleportAccept() {
        recordTeleportAcceptResult(teleportAccept.tick());
    }

    private void recordTeleportAcceptResult(PlaneKitbotTeleportAcceptWorkflow.AcceptResult result) {
        if (result.status() != PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED) {
            lastTeleportAcceptResult = result;
        }
    }

    private Phase requestOrMissingSupply() {
        if (!delivery.pending() && hasUsableSupply()) return Phase.SELECTING_REPLENISH_SOURCE;

        Phase phase = delivery.requestOrMissingSupply(messenger);
        if (delivery.consumeRequestSent()) {
            recordTeleportAcceptResult(teleportAccept.queueConfiguredAcceptForRequest());
        }
        return phase;
    }

    private boolean hasUsableSupply() {
        return supplyProbe != null
            && (supplyProbe.hasLooseEnderChests()
            || supplyProbe.hasEnderChestShulker()
            || supplyProbe.enderChestSupplyCount() >= Math.max(1, requiredEnderChestSupply.getAsInt()));
    }

    private void markSuppliesAvailable() {
        delivery.markSuppliesAvailable();
        teleportAccept.reset();
    }
}
