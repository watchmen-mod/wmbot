package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

final class PlaneReplenishWorkflow {
    private final ServiceHoleContext serviceHole;
    private final PlaneClientContext context;
    private final PlaneInventoryAccess inventory;
    private final ServiceHoleWorkflow serviceHoles;
    private final EnderChestFarmWorkflow enderChestFarm;
    private final EnderChestShulkerExtractor shulkerExtractor;
    private final PlaneKitbotRefillWorkflow kitbotRefill;
    private final ServiceHoleExitWorkflow serviceHoleExit;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerRecovery;
    private final PlaneDroppedItemPickupWorkflow<ItemEntity> missingShulkerPickup;
    private final PlaneTrashEdgeWorkflow trashCleanup;
    private final PlaneRuntimeConfig config;
    private final WorkflowLogger logger;
    private final ManagedEnderChestShulkerState managedShulker = new ManagedEnderChestShulkerState();
    private final Map<Phase, PlaneReplenishTransition> transitions;
    private BlockPos lastServiceHoleExitTarget;
    private int serviceHoleExitLogTicks;
    private int managedShulkerRecoveryLogTicks;

    private Phase phase = Phase.IDLE;

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            PlaneRuntimeConfig.DEFAULT
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config
    ) {
        this(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, new PlaneClientContext());
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            config,
            context,
            new PlaneEndermanLookSafety()
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            config,
            context,
            endermanLookSafety,
            PlaneWorkflowLoggers.NOOP
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger
    ) {
        this(PlaneReplenishComponents.create(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, context, endermanLookSafety, logger));
    }

    PlaneReplenishWorkflow(PlaneReplenishComponents components) {
        inventory = components.inventory();
        context = components.context();
        serviceHole = components.serviceHole();
        serviceHoles = components.serviceHoles();
        enderChestFarm = components.enderChestFarm();
        shulkerExtractor = components.shulkerExtractor();
        kitbotRefill = components.kitbotRefill();
        serviceHoleExit = components.serviceHoleExit();
        dropCleanup = components.dropCleanup();
        managedShulkerRecovery = components.managedShulkerRecovery();
        missingShulkerPickup = components.missingShulkerPickup();
        trashCleanup = components.trashCleanup();
        config = components.config();
        logger = components.logger();
        transitions = PlaneReplenishTransitionTable.create(
            serviceHoles,
            enderChestFarm,
            this::closeServiceHole,
            this::placeEnderChestShulker,
            this::openEnderChestShulker,
            this::takeEnderChestsFromShulker,
            this::breakEnderChestShulker,
            this::closeServiceHoleForKitbotRefill,
            this::waitForKitbotRefill,
            this::pickUpKitbotRefill,
            this::pickUpMissingEnderChestShulker,
            this::pickUpReplenishDrops,
            this::moveToTrashEdge,
            this::dropTrashOffEdge,
            this::waitForTrashToFall,
            this::recoverMissingEnderChest,
            this::recoverMissingObsidian
        );
    }

    void reset() {
        phase = Phase.IDLE;
        serviceHole.reset();
        enderChestFarm.resetFarmProgress();
        serviceHoleExit.reset();
        managedShulker.reset();
        managedShulkerRecovery.reset();
        missingShulkerPickup.reset();
        managedShulkerRecoveryLogTicks = 0;
        kitbotRefill.reset();
        dropCleanup.reset();
        trashCleanup.resetAll();
    }

    void pauseMovement() {
        trashCleanup.pauseMovement();
    }

    Phase phase() {
        return phase;
    }

    boolean active() {
        return PlaneReplenishDecisions.active(phase, serviceHole.selected())
            || (kitbotRefill.pending() && pendingKitbotRefillOwnsPhase(phase));
    }

    boolean allowsBowDefenseDuringReplenish() {
        return PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase);
    }

    boolean hasQueuedTeleportAccept() {
        return kitbotRefill.hasQueuedTeleportAccept();
    }

    void tickQueuedTeleportAccept() {
        kitbotRefill.tickQueuedTeleportAccept();
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult handleMessage(String message) {
        return kitbotRefill.handleTeleportPrompt(message);
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return kitbotRefill.ignoredTeleportPrompt(message);
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult consumeTeleportAcceptResult() {
        return kitbotRefill.consumeTeleportAcceptResult();
    }

    void begin() {
        serviceHole.reset();
        enderChestFarm.resetFarmProgress();
        kitbotRefill.beginReplenishCycle();
        trashCleanup.beginCleanupCycle();
        phase = Phase.SELECTING_SERVICE_HOLE;
    }

    Phase tick() {
        return tickResult().phase();
    }

    ReplenishTickResult tickResult() {
        Phase previousPhase = phase;

        PlaneReplenishTransition transition = transitions.get(phase);
        phase = transition == null ? phase : transition.next();
        if (previousPhase == Phase.BREAKING_ENDER_CHEST && phase != Phase.BREAKING_ENDER_CHEST) {
            enderChestFarm.reset();
        }
        if (previousPhase != Phase.SELECTING_SERVICE_HOLE && phase == Phase.SELECTING_SERVICE_HOLE) {
            enderChestFarm.resetFarmProgress();
        }
        return new ReplenishTickResult(phase, allowsBowDefenseDuringReplenish());
    }

    static Set<Phase> transitionPhases() {
        return PlaneReplenishTransitionTable.transitionPhases();
    }

    static boolean pendingKitbotRefillOwnsPhase(Phase phase) {
        return phase == Phase.WAITING_FOR_KITBOT_REFILL
            || phase == Phase.PICKING_UP_KITBOT_REFILL;
    }

    private Phase placeEnderChestShulker() {
        if (enderChestFarm.targetBuildBlockCountReached()) {
            shulkerExtractor.resetExtractionBaseline();
            managedShulker.reset();
            managedShulkerRecovery.reset();
            missingShulkerPickup.reset();
            serviceHoleExit.reset();
            managedShulkerRecoveryLogTicks = 0;
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

    private Phase openEnderChestShulker() {
        boolean wasManaged = managedShulker.trackingHole(serviceHole.hole());
        markManagedShulkerIfPlaced();
        Phase next = shulkerExtractor.open(serviceHole);
        if (next == Phase.TAKING_ENDER_CHESTS_FROM_SHULKER) managedShulker.markOpenedOrExtracted();
        markManagedOrPostBreak(wasManaged, next);
        return handleMissingSupply(next);
    }

    private Phase takeEnderChestsFromShulker() {
        boolean wasManaged = managedShulker.trackingHole(serviceHole.hole());
        markManagedShulkerIfPlaced();
        Phase next = shulkerExtractor.take();
        managedShulker.markOpenedOrExtracted();
        markManagedOrPostBreak(wasManaged, next);
        return handleMissingSupply(next);
    }

    private Phase breakEnderChestShulker() {
        boolean wasManagedPlaced = managedShulker.placedAt(serviceHole.hole(), serviceHole.status());
        Phase next = shulkerExtractor.breakPlacedShulker(serviceHole);
        ServiceHoleContext.Status statusAfterBreak = serviceHole.status();
        if (next == Phase.PLACING_ENDER_CHEST || next == Phase.CLOSING_SERVICE_HOLE) {
            managedShulker.reset();
            managedShulkerRecovery.reset();
            missingShulkerPickup.reset();
            serviceHoleExit.reset();
            managedShulkerRecoveryLogTicks = 0;
        }
        else if (wasManagedPlaced) markManagedOrPostBreak(true, next);
        else {
            markManagedShulkerIfPlaced();
        }

        return handleMissingSupply(next);
    }

    private Phase closeServiceHoleForKitbotRefill() {
        Phase closePhase = serviceHoles.close();
        if (closePhase == Phase.IDLE) return requestKitbotRefillOrMissingSupply();
        if (closePhase == Phase.CLOSING_SERVICE_HOLE) return Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL;

        return closePhase;
    }

    private Phase closeServiceHole() {
        managedShulker.reset();
        managedShulkerRecovery.reset();
        missingShulkerPickup.reset();
        serviceHoleExit.reset();
        managedShulkerRecoveryLogTicks = 0;
        Phase next = PlaneReplenishDecisions.afterNormalServiceHoleClose(serviceHoles.close());
        if (next == Phase.PICKING_UP_REPLENISH_DROPS) trashCleanup.beginCleanupCycle();
        return next;
    }

    private Phase recoverMissingObsidian() {
        Phase recovery = PlaneReplenishDecisions.missingObsidianRecoveryPhase(
            inventory.countBuildBlock(),
            config.replenishMinBuildBlocks(),
            serviceHole.selected()
        );
        if (recovery == Phase.CLOSING_SERVICE_HOLE) return closeServiceHole();

        return recovery;
    }

    private Phase recoverMissingEnderChest() {
        return handleMissingSupply(enderChestFarm.recoverMissingEnderChest());
    }

    private Phase waitForKitbotRefill() {
        return kitbotRefill.waitForDelivery();
    }

    private Phase pickUpKitbotRefill() {
        return kitbotRefill.pickUpDelivery();
    }

    private Phase pickUpMissingEnderChestShulker() {
        EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
        if (scan.hasVisibleSource()) {
            missingShulkerPickup.reset();
            return Phase.PLACING_ENDER_CHEST_SHULKER;
        }

        Phase recovery = missingShulkerPickup.tick();
        if (recovery != Phase.MISSING_ENDER_CHEST_SHULKER) return recovery;

        return kitbotRefill.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER, false);
    }

    private Phase pickUpReplenishDrops() {
        return dropCleanup.tick();
    }

    private Phase moveToTrashEdge() {
        return trashCleanup.moveToEdge();
    }

    private Phase dropTrashOffEdge() {
        return trashCleanup.dropOffEdge();
    }

    private Phase waitForTrashToFall() {
        return trashCleanup.waitForTrashToFall();
    }

    private Phase handleMissingSupply(Phase next) {
        if (!missingSupplyPhase(next)) return next;

        ServiceHoleContext.Status status = serviceHole.status();
        boolean managedPlaced = managedShulker.placedAt(serviceHole.hole(), status);
        if (managedPlaced) {
            EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
            logger.info(
                "Suppressed kitbot refill while managed ender chest shulker is placed: phase=%s serviceHoleStatus=%s looseEnderChests=%d hotbarSlot=%d mainSlot=%d shulkerStacks=%d containedEnderChests=%d.",
                phase.label(),
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
            if (inventory.countLooseEnderChests() > 0 || scan.hasVisibleSource()) {
                if (managedShulker.failedBeforeOpenRecovery()) {
                    logger.warning(
                        "Managed ender chest shulker disappeared before opening; recovered supply and blocking service hole to avoid place/break loop: phase=%s serviceHoleStatus=%s playerPos=%s serviceHole=%s openedOrExtracted=%s looseEnderChests=%d hotbarSlot=%d mainSlot=%d shulkerStacks=%d containedEnderChests=%d failedOpenAttempts=%d next=%s.",
                        phase.label(),
                        status,
                        context.player().getBlockPos(),
                        serviceHole.hole(),
                        managedShulker.openedOrExtracted(),
                        inventory.countLooseEnderChests(),
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

    private Phase requestKitbotRefillOrMissingSupply() {
        return kitbotRefill.afterServiceHoleClosed();
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
            phase.label(),
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

    private boolean missingSupplyPhase(Phase phase) {
        return phase == Phase.MISSING_ENDER_CHEST || phase == Phase.MISSING_ENDER_CHEST_SHULKER;
    }
}
