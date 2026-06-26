package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

final class PlaneReplenishManagedShulkerWorkflow {
    private final PlaneClientContext context;
    private final PlaneInventoryAccess inventory;
    private final ServiceHoleContext serviceHole;
    private final ServiceHoleWorkflow serviceHoles;
    private final EnderChestFarmWorkflow enderChestFarm;
    private final EnderChestShulkerExtractor shulkerExtractor;
    private final PlaneKitbotRefillWorkflow kitbotRefill;
    private final ServiceHoleExitWorkflow serviceHoleExit;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerRecovery;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> missingShulkerPickup;
    private final WorkflowLogger logger;
    private final Supplier<Phase> currentPhase;
    private final ManagedEnderChestShulkerState managedShulker = new ManagedEnderChestShulkerState();
    private BlockPos lastServiceHoleExitTarget;
    private int serviceHoleExitLogTicks;
    private int managedShulkerRecoveryLogTicks;

    PlaneReplenishManagedShulkerWorkflow(
        PlaneClientContext context,
        PlaneInventoryAccess inventory,
        ServiceHoleContext serviceHole,
        ServiceHoleWorkflow serviceHoles,
        EnderChestFarmWorkflow enderChestFarm,
        EnderChestShulkerExtractor shulkerExtractor,
        PlaneKitbotRefillWorkflow kitbotRefill,
        ServiceHoleExitWorkflow serviceHoleExit,
        PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerRecovery,
        PlaneDroppedItemPickupWorkflow<ItemEntity> missingShulkerPickup,
        WorkflowLogger logger,
        Supplier<Phase> currentPhase
    ) {
        this.context = context;
        this.inventory = inventory;
        this.serviceHole = serviceHole;
        this.serviceHoles = serviceHoles;
        this.enderChestFarm = enderChestFarm;
        this.shulkerExtractor = shulkerExtractor;
        this.kitbotRefill = kitbotRefill;
        this.serviceHoleExit = serviceHoleExit;
        this.managedShulkerRecovery = managedShulkerRecovery;
        this.missingShulkerPickup = missingShulkerPickup;
        this.logger = logger;
        this.currentPhase = currentPhase;
    }

    void reset() {
        managedShulker.reset();
        managedShulkerRecovery.reset();
        missingShulkerPickup.reset();
        serviceHoleExit.reset();
        managedShulkerRecoveryLogTicks = 0;
    }

    Phase placeEnderChestShulker() {
        if (enderChestFarm.targetBuildBlockCountReached()) {
            shulkerExtractor.resetExtractionBaseline();
            reset();
            return Phase.CLOSING_SERVICE_HOLE;
        }
        Phase unavailable = serviceHoles.requireOpen();
        if (unavailable != null) return unavailable;
        ServiceHoleExitWorkflow.ExitResult exit = serviceHoleExit.tick(context.player().getBlockPos(), serviceHole.hole());
        if (exit.active()) {
            logServiceHoleExitPause(exit);
            return Phase.PLACING_ENDER_CHEST_SHULKER;
        }
        lastServiceHoleExitTarget = null;
        serviceHoleExitLogTicks = 0;

        Phase next = shulkerExtractor.place(serviceHole);
        markManagedShulkerIfPlaced();
        return handleMissingSupply(next);
    }

    Phase openEnderChestShulker() {
        boolean wasManaged = managedShulker.trackingHole(serviceHole.hole());
        markManagedShulkerIfPlaced();
        Phase next = shulkerExtractor.open(serviceHole);
        if (next == Phase.TAKING_ENDER_CHESTS_FROM_SHULKER) managedShulker.markOpenedOrExtracted();
        markManagedOrPostBreak(wasManaged, next);
        return handleMissingSupply(next);
    }

    Phase takeEnderChestsFromShulker() {
        boolean wasManaged = managedShulker.trackingHole(serviceHole.hole());
        markManagedShulkerIfPlaced();
        Phase next = shulkerExtractor.take();
        managedShulker.markOpenedOrExtracted();
        markManagedOrPostBreak(wasManaged, next);
        return handleMissingSupply(next);
    }

    Phase breakEnderChestShulker() {
        boolean wasManagedPlaced = managedShulker.placedAt(serviceHole.hole(), serviceHole.status());
        Phase next = shulkerExtractor.breakPlacedShulker(serviceHole);
        if (next == Phase.PLACING_ENDER_CHEST || next == Phase.CLOSING_SERVICE_HOLE) {
            reset();
        }
        else if (wasManagedPlaced) markManagedOrPostBreak(true, next);
        else {
            markManagedShulkerIfPlaced();
        }

        return handleMissingSupply(next);
    }

    Phase pickUpMissingEnderChestShulker() {
        EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
        if (scan.hasVisibleSource()) {
            missingShulkerPickup.reset();
            return Phase.PLACING_ENDER_CHEST_SHULKER;
        }

        Phase recovery = missingShulkerPickup.tick();
        if (recovery != Phase.MISSING_ENDER_CHEST_SHULKER) return recovery;

        return kitbotRefill.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER, false);
    }

    Phase handleMissingSupply(Phase next) {
        if (!missingSupplyPhase(next)) return next;

        ServiceHoleContext.Status status = serviceHole.status();
        boolean managedPlaced = managedShulker.placedAt(serviceHole.hole(), status);
        if (managedPlaced) {
            EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
            logger.info(
                "Suppressed kitbot refill while managed ender chest shulker is placed: phase=%s serviceHoleStatus=%s looseEnderChests=%d hotbarSlot=%d mainSlot=%d shulkerStacks=%d containedEnderChests=%d.",
                currentPhase.get().label(),
                status,
                inventory.countLooseEnderChests(),
                scan.hotbarSlot(),
                scan.mainInventorySlot(),
                scan.shulkerStacks(),
                scan.containedEnderChests()
            );
            return Phase.ENDER_CHEST_SHULKER_PLACED;
        }

        if (managedShulker.postBreakRecovery()) {
            EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
            int looseEnderChests = inventory.countLooseEnderChests();
            boolean visibleShulkerSource = scan.hasVisibleSource();
            if (managedShulker.failedBeforeOpenRecovery() && (looseEnderChests > 0 || visibleShulkerSource)) {
                logger.warning(
                    "Managed ender chest shulker disappeared before opening; recovered supply and blocking service hole to avoid place/break loop: phase=%s serviceHoleStatus=%s playerPos=%s serviceHole=%s openedOrExtracted=%s looseEnderChests=%d hotbarSlot=%d mainSlot=%d shulkerStacks=%d containedEnderChests=%d failedOpenAttempts=%d next=%s.",
                    currentPhase.get().label(),
                    status,
                    context.player().getBlockPos(),
                    serviceHole.hole(),
                    managedShulker.openedOrExtracted(),
                    looseEnderChests,
                    scan.hotbarSlot(),
                    scan.mainInventorySlot(),
                    scan.shulkerStacks(),
                    scan.containedEnderChests(),
                    managedShulker.failedOpenAttempts(),
                    Phase.SELECTING_SERVICE_HOLE.label()
                );
                managedShulker.clearFailedBeforeOpenRecovery();
                managedShulker.clearPostBreakRecovery();
                managedShulkerRecovery.reset();
                managedShulkerRecoveryLogTicks = 0;
                serviceHole.markSelectedBlocked();
                return Phase.SELECTING_SERVICE_HOLE;
            }

            if (!shouldRecoverManagedShulkerDrop(managedShulker.failedBeforeOpenRecovery(), looseEnderChests, visibleShulkerSource)) {
                managedShulker.clearPostBreakRecovery();
                managedShulkerRecovery.reset();
                managedShulkerRecoveryLogTicks = 0;
                return kitbotRefill.missingSupplyPhase(next, false);
            }

            Phase recovery = managedShulkerRecovery.tick();
            boolean hasDrop = managedShulkerRecovery.hasTarget();
            logManagedShulkerRecovery(status, scan, hasDrop, recovery);
            if (recovery != Phase.MISSING_ENDER_CHEST_SHULKER) return recovery;

            managedShulker.clearPostBreakRecovery();
            managedShulkerRecoveryLogTicks = 0;
        }

        if (next == Phase.MISSING_ENDER_CHEST_SHULKER && missingShulkerPickup.hasTarget()) {
            return missingShulkerPickup.tick();
        }

        EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
        Phase recoveredSupply = PlaneReplenishDecisions.missingEnderChestRecoveryPhase(
            next,
            inventory.countLooseEnderChests(),
            scan.hasVisibleSource()
        );
        if (recoveredSupply != next) {
            missingShulkerPickup.reset();
            return recoveredSupply;
        }

        return kitbotRefill.missingSupplyPhase(next, managedShulker.suppressesRefill(serviceHole.hole(), status));
    }

    private void markManagedShulkerIfPlaced() {
        if (serviceHole.status() == ServiceHoleContext.Status.READY_SHULKER) {
            managedShulker.markPlaced(serviceHole.hole());
        }
    }

    private void markManagedOrPostBreak(boolean wasManaged, Phase next) {
        ServiceHoleContext.Status status = serviceHole.status();
        if (status == ServiceHoleContext.Status.READY_SHULKER) {
            managedShulker.markPlaced(serviceHole.hole());
        }
        else if (wasManaged && status == ServiceHoleContext.Status.READY_REPLACEABLE && missingSupplyPhase(next)) {
            managedShulker.markPostBreakRecovery();
        }
    }

    private void logServiceHoleExitPause(ServiceHoleExitWorkflow.ExitResult exit) {
        boolean targetChanged = exit.target() == null
            ? lastServiceHoleExitTarget != null
            : !exit.target().equals(lastServiceHoleExitTarget);
        if (!targetChanged && serviceHoleExitLogTicks++ < 20) return;

        serviceHoleExitLogTicks = 0;
        lastServiceHoleExitTarget = exit.target() == null ? null : exit.target().toImmutable();
        logger.info(
            "Paused ender chest shulker placement while exiting service hole: phase=%s playerPos=%s serviceHole=%s exitTarget=%s missingExitTarget=%s.",
            Phase.PLACING_ENDER_CHEST_SHULKER.label(),
            context.player().getBlockPos(),
            serviceHole.hole(),
            exit.target(),
            exit.missingTarget()
        );
    }

    private void logManagedShulkerRecovery(
        ServiceHoleContext.Status status,
        EnderChestShulkerSourceScan scan,
        boolean hasDrop,
        Phase recovery
    ) {
        if (recovery != Phase.MISSING_ENDER_CHEST_SHULKER
            && managedShulkerRecoveryLogTicks++ < PlanePickupSettings.MANAGED_SHULKER_RECOVERY_LOG_INTERVAL_TICKS) {
            return;
        }

        managedShulkerRecoveryLogTicks = 0;
        logger.info(
            "Suppressed kitbot refill while recovering broken managed ender chest shulker: phase=%s serviceHoleStatus=%s looseEnderChests=%d hotbarSlot=%d mainSlot=%d shulkerStacks=%d containedEnderChests=%d hasNearbyShulkerDrop=%s recovery=%s.",
            currentPhase.get().label(),
            status,
            inventory.countLooseEnderChests(),
            scan.hotbarSlot(),
            scan.mainInventorySlot(),
            scan.shulkerStacks(),
            scan.containedEnderChests(),
            hasDrop,
            recovery.label()
        );
    }

    private static boolean missingSupplyPhase(Phase phase) {
        return phase == Phase.MISSING_ENDER_CHEST || phase == Phase.MISSING_ENDER_CHEST_SHULKER;
    }

    static boolean shouldRecoverManagedShulkerDrop(
        boolean failedBeforeOpenRecovery,
        int looseEnderChests,
        boolean visibleShulkerSource
    ) {
        if (failedBeforeOpenRecovery && looseEnderChests > 0) return false;
        return !visibleShulkerSource;
    }
}
