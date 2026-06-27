package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class EnderChestShulkerExtractor {
    private final PlaneInventory inventory;
    private final PlanePlacement placement;
    private final PlaneActionGuards guards;
    private final PlaneWorldActions actions;
    private final PlaneActionExecutor actionExecutor;
    private final ShulkerExtractionSession session = new ShulkerExtractionSession();
    private final EnderChestShulkerExtractionBudget budget = new EnderChestShulkerExtractionBudget();
    private final EnderChestShulkerSourceMisses sourceMisses = new EnderChestShulkerSourceMisses();
    private final ShulkerScreenInteractor screens;
    private final PlaneBuilderSettings.Replenish replenishSettings;
    private final WorkflowLogger logger;
    private final EnderChestFarmProgress farmProgress;
    private final BooleanSupplier reserveManagedShulkerSlot;
    private final IntSupplier visibleDroppedObsidian;

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings
    ) {
        this(inventory, placement, guards, actions, replenishSettings, new PlaneEndermanLookSafety(), PlaneWorkflowLoggers.NOOP);
    }

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this(inventory, placement, guards, actions, replenishSettings, endermanLookSafety, PlaneWorkflowLoggers.NOOP);
    }

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger
    ) {
        this(inventory, placement, guards, actions, replenishSettings, endermanLookSafety, logger, new EnderChestFarmProgress());
    }

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress
    ) {
        this(
            inventory,
            placement,
            guards,
            actions,
            replenishSettings,
            endermanLookSafety,
            logger,
            farmProgress,
            () -> false,
            () -> 0
        );
    }

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress,
        BooleanSupplier reserveManagedShulkerSlot
    ) {
        this(
            inventory,
            placement,
            guards,
            actions,
            replenishSettings,
            endermanLookSafety,
            logger,
            farmProgress,
            reserveManagedShulkerSlot,
            () -> 0
        );
    }

    EnderChestShulkerExtractor(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress,
        BooleanSupplier reserveManagedShulkerSlot,
        IntSupplier visibleDroppedObsidian
    ) {
        this.inventory = inventory;
        this.placement = placement;
        this.guards = guards;
        this.actions = actions;
        this.replenishSettings = replenishSettings;
        this.logger = logger;
        this.farmProgress = farmProgress;
        this.reserveManagedShulkerSlot = reserveManagedShulkerSlot == null ? () -> false : reserveManagedShulkerSlot;
        this.visibleDroppedObsidian = visibleDroppedObsidian == null ? () -> 0 : visibleDroppedObsidian;
        actionExecutor = new PlaneActionExecutor(PlaneRuntimeConfig.DEFAULT, endermanLookSafety);
        screens = new ShulkerScreenInteractor(inventory, guards);
    }

    Phase place(ServiceHoleContext serviceHole) {
        if (timedOut(Phase.PLACING_ENDER_CHEST_SHULKER, Phase.MISSING_ENDER_CHEST_SHULKER)) {
            return Phase.MISSING_ENDER_CHEST_SHULKER;
        }
        if (!serviceHole.readyForWorkflow()) return Phase.SERVICE_HOLE_BLOCKED;

        return switch (serviceHole.status()) {
            case READY_SHULKER -> {
                ensureExtractionBaseline();
                yield Phase.ENDER_CHEST_SHULKER_PLACED;
            }
            case READY_ENDER_CHEST -> {
                session.reset();
                yield Phase.BREAKING_ENDER_CHEST;
            }
            case READY_REPLACEABLE -> placeAvailableShulker(serviceHole);
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    Phase open(ServiceHoleContext serviceHole) {
        if (timedOut(Phase.OPENING_ENDER_CHEST_SHULKER, Phase.MISSING_ENDER_CHEST_SHULKER)) {
            return Phase.MISSING_ENDER_CHEST_SHULKER;
        }
        if (!guards.clientReady()) return Phase.OPENING_ENDER_CHEST_SHULKER;
        if (screens.playerHasShulkerOpen()) {
            budget.reset();
            return Phase.TAKING_ENDER_CHESTS_FROM_SHULKER;
        }
        if (!guards.readyForUseAction()) return Phase.OPENING_ENDER_CHEST_SHULKER;
        if (!serviceHole.readyForWorkflow()) return Phase.SERVICE_HOLE_BLOCKED;

        return switch (serviceHole.status()) {
            case READY_SHULKER -> openShulkerBlock(serviceHole);
            case READY_REPLACEABLE -> {
                if (targetBuildBlockCountReached()) yield Phase.CLOSING_SERVICE_HOLE;
                yield session.afterShulkerRemoved(inventory.countLooseEnderChests());
            }
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    Phase take() {
        if (!guards.clientReady()) return Phase.TAKING_ENDER_CHESTS_FROM_SHULKER;
        if (screens.waitingForManagedScreen()) return Phase.TAKING_ENDER_CHESTS_FROM_SHULKER;

        ShulkerBoxScreenHandler handler = screens.openHandler();
        if (handler == null) {
            return Phase.OPENING_ENDER_CHEST_SHULKER;
        }

        ensureExtractionBaseline();
        int needed = farmProgress.safeAdditionalEnderChestsNeeded(
            inventory.countBuildBlock(),
            effectiveReplenishTarget(),
            visibleDroppedObsidian.getAsInt()
        );
        int currentLooseEnderChests = inventory.countLooseEnderChests();
        if (budget.stalledTake(currentLooseEnderChests)) {
            int baseline = session.baseline();
            closeScreenIfSafe();
            session.reset();
            budget.reset();
            logger.warning(
                "Reset ender chest shulker extraction after stalled transfer: looseEnderChests=%d needed=%d baseline=%d.",
                currentLooseEnderChests,
                needed,
                baseline
            );
            return Phase.MISSING_ENDER_CHEST;
        }
        if (currentLooseEnderChests >= needed) {
            screens.close();
            budget.reset();
            return Phase.BREAKING_ENDER_CHEST_SHULKER;
        }
        if (!inventory.hasInventorySpaceForEnderChestPreservingShulkerSlot()) {
            screens.close();
            return session.unavailablePhase(currentLooseEnderChests);
        }
        if (!inventory.hasInventorySpaceForEnderChest()) {
            screens.close();
            return session.unavailablePhase(currentLooseEnderChests);
        }

        int movedSlot = screens.findEnderChestSlot(handler);
        if (movedSlot < 0) {
            screens.close();
            return session.unavailablePhase(currentLooseEnderChests);
        }

        screens.quickMove(handler, movedSlot);
        return Phase.TAKING_ENDER_CHESTS_FROM_SHULKER;
    }

    Phase breakPlacedShulker(ServiceHoleContext serviceHole) {
        if (timedOut(Phase.BREAKING_ENDER_CHEST_SHULKER, Phase.MISSING_ENDER_CHEST_SHULKER)) {
            return Phase.MISSING_ENDER_CHEST_SHULKER;
        }
        if (!guards.clientReady()) return Phase.BREAKING_ENDER_CHEST_SHULKER;
        if (!guards.readyForWorldAction() && screens.onPlayerInventoryScreen()) {
            return Phase.BREAKING_ENDER_CHEST_SHULKER;
        }
        if (!screens.onPlayerInventoryScreen()) {
            screens.close();
            return Phase.BREAKING_ENDER_CHEST_SHULKER;
        }
        if (!serviceHole.readyForWorkflow()) return Phase.SERVICE_HOLE_BLOCKED;

        return switch (serviceHole.status()) {
            case READY_SHULKER -> breakCurrentShulker(serviceHole);
            case READY_REPLACEABLE -> {
                if (targetBuildBlockCountReached()) yield Phase.CLOSING_SERVICE_HOLE;
                yield session.afterShulkerRemoved(inventory.countLooseEnderChests());
            }
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    private Phase placeAvailableShulker(ServiceHoleContext serviceHole) {
        EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
        if (scan.hasMainInventorySource() && !scan.hasHotbarSource()) {
            inventory.prepareUsableEnderChestShulker();
            return unavailableShulkerSourcePhase(scan);
        }
        if (scan.hasVisibleSource() && !scan.hasHotbarSource()) {
            return unavailableShulkerSourcePhase(scan);
        }

        FindItemResult shulker = inventory.prepareUsableEnderChestShulker();
        if (!inventory.findResultMatchesEnderChestShulker(shulker)) {
            return unavailableShulkerSourcePhase(scan);
        }

        sourceMisses.reset();
        ensureExtractionBaseline();
        placement.placeShulkerInServiceHole(serviceHole.hole(), serviceHole.support(), shulker);
        return Phase.PLACING_ENDER_CHEST_SHULKER;
    }

    private Phase openShulkerBlock(ServiceHoleContext serviceHole) {
        ensureExtractionBaseline();
        Vec3d hit = Vec3d.ofCenter(serviceHole.hole());
        BlockHitResult hitResult = new BlockHitResult(hit, Direction.UP, serviceHole.hole(), false);
        actionExecutor.rotate(hit, () -> {
            if (!guards.readyForUseAction()) return;
            if (serviceHole.status() != ServiceHoleContext.Status.READY_SHULKER) return;
            actionExecutor.interact(hitResult, Hand.MAIN_HAND);
        });

        return Phase.OPENING_ENDER_CHEST_SHULKER;
    }

    private Phase breakCurrentShulker(ServiceHoleContext serviceHole) {
        Phase next = actions.breakWithPickaxe(serviceHole.hole(), Phase.BREAKING_ENDER_CHEST_SHULKER, true);
        if (next == Phase.BREAKING_ENDER_CHEST_SHULKER) session.reset();
        return next;
    }

    void resetExtractionBaseline() {
        session.reset();
        sourceMisses.reset();
        budget.reset();
    }

    private void ensureExtractionBaseline() {
        session.ensureBaseline(inventory.countLooseEnderChests());
    }

    private Phase unavailableShulkerSourcePhase(EnderChestShulkerSourceScan scan) {
        Phase phase = sourceMisses.phase(scan, guards.clientReady());
        logUnavailableShulkerSource(scan, phase);
        if (phase == Phase.MISSING_ENDER_CHEST_SHULKER) {
            return session.missingSourcePhase(false);
        }

        return session.missingSourcePhase(true);
    }

    private void logUnavailableShulkerSource(EnderChestShulkerSourceScan scan, Phase nextPhase) {
        logger.info(
            "Ender chest shulker placement waiting: phase=%s hotbarSlot=%d hotbarCount=%d mainSlot=%d offhand=%s cursor=%s looseEnderChests=%d shulkerStacks=%d containedEnderChests=%d consecutiveMisses=%d next=%s.",
            Phase.PLACING_ENDER_CHEST_SHULKER.label(),
            scan.hotbarSlot(),
            scan.hotbarStackCount(),
            scan.mainInventorySlot(),
            scan.offhand(),
            scan.cursor(),
            inventory.countLooseEnderChests(),
            scan.shulkerStacks(),
            scan.containedEnderChests(),
            sourceMisses.consecutiveReadyMisses(),
            nextPhase.label()
        );
    }

    private int effectiveReplenishTarget() {
        return PlaneReplenishTargets.effectiveTarget(
            inventory,
            replenishSettings.targetObsidian().get(),
            replenishSettings.useAvailableSafeInventorySpace().get(),
            reserveManagedShulkerSlot.getAsBoolean()
        );
    }

    private boolean targetBuildBlockCountReached() {
        return !farmProgress.canFitAdditionalEnderChest(
            inventory.countBuildBlock(),
            effectiveReplenishTarget(),
            visibleDroppedObsidian.getAsInt()
        );
    }

    private boolean timedOut(Phase timedPhase, Phase resetPhase) {
        if (!budget.timedOut(timedPhase)) return false;

        closeScreenIfSafe();
        session.reset();
        sourceMisses.reset();
        budget.reset();
        logger.warning("Reset ender chest shulker workflow after phase timeout: phase=%s next=%s.", timedPhase.label(), resetPhase.label());
        return true;
    }

    private void closeScreenIfSafe() {
        if (guards.safeToCloseManagedScreen()) screens.close();
    }
}
