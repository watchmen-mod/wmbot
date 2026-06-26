package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Blocks;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class EnderChestFarmWorkflow {
    private final PlaneInventory inventory;
    private final PlanePlacement placement;
    private final PlaneActionGuards guards;
    private final ServiceHoleContext serviceHole;
    private final ServiceHoleWorkflow serviceHoles;
    private final PlaneWorldActions actions;
    private final PlaneBuilderSettings.Replenish replenishSettings;
    private final WorkflowLogger logger;
    private final EnderChestFarmProgress farmProgress;
    private final BooleanSupplier reserveManagedShulkerSlot;
    private final IntSupplier visibleDroppedObsidian;
    private final EnderChestBreakWatchdog breakWatchdog = new EnderChestBreakWatchdog();
    private boolean waitingForBreakCompletion;

    EnderChestFarmWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        ServiceHoleContext serviceHole,
        ServiceHoleWorkflow serviceHoles,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        WorkflowLogger logger
    ) {
        this(inventory, placement, guards, serviceHole, serviceHoles, actions, replenishSettings, logger, new EnderChestFarmProgress());
    }

    EnderChestFarmWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        ServiceHoleContext serviceHole,
        ServiceHoleWorkflow serviceHoles,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress
    ) {
        this(
            inventory,
            placement,
            guards,
            serviceHole,
            serviceHoles,
            actions,
            replenishSettings,
            logger,
            farmProgress,
            () -> false,
            () -> 0
        );
    }

    EnderChestFarmWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        ServiceHoleContext serviceHole,
        ServiceHoleWorkflow serviceHoles,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress,
        BooleanSupplier reserveManagedShulkerSlot
    ) {
        this(
            inventory,
            placement,
            guards,
            serviceHole,
            serviceHoles,
            actions,
            replenishSettings,
            logger,
            farmProgress,
            reserveManagedShulkerSlot,
            () -> 0
        );
    }

    EnderChestFarmWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneActionGuards guards,
        ServiceHoleContext serviceHole,
        ServiceHoleWorkflow serviceHoles,
        PlaneWorldActions actions,
        PlaneBuilderSettings.Replenish replenishSettings,
        WorkflowLogger logger,
        EnderChestFarmProgress farmProgress,
        BooleanSupplier reserveManagedShulkerSlot,
        IntSupplier visibleDroppedObsidian
    ) {
        this.inventory = inventory;
        this.placement = placement;
        this.guards = guards;
        this.serviceHole = serviceHole;
        this.serviceHoles = serviceHoles;
        this.actions = actions;
        this.replenishSettings = replenishSettings;
        this.logger = logger;
        this.farmProgress = farmProgress;
        this.reserveManagedShulkerSlot = reserveManagedShulkerSlot == null ? () -> false : reserveManagedShulkerSlot;
        this.visibleDroppedObsidian = visibleDroppedObsidian == null ? () -> 0 : visibleDroppedObsidian;
    }

    Phase selectSource() {
        return recoverMissingEnderChest();
    }

    void reset() {
        breakWatchdog.reset();
        waitingForBreakCompletion = false;
    }

    void resetFarmProgress() {
        farmProgress.reset();
        reset();
    }

    Phase recoverMissingEnderChest() {
        Phase unavailable = serviceHoles.requireOpen();
        if (unavailable != null) return unavailable;

        int buildBlocks = inventory.countBuildBlock();
        EnderChestShulkerSourceScan scan = inventory.scanEnderChestShulkerSources();
        int targetBuildBlocks = effectiveReplenishTarget();
        int visibleDrops = visibleDroppedObsidian.getAsInt();
        int effectiveBuildBlocks = farmProgress.effectiveBuildBlocks(buildBlocks, visibleDrops);
        int sourceTarget = farmProgress.canFitAdditionalEnderChest(buildBlocks, targetBuildBlocks, visibleDrops)
            ? targetBuildBlocks
            : effectiveBuildBlocks;

        return PlaneReplenishDecisions.sourcePhase(
            effectiveBuildBlocks,
            sourceTarget,
            inventory.countLooseEnderChests(),
            scan.hasVisibleSource()
        );
    }

    Phase place() {
        if (targetBuildBlockCountReached()) return Phase.CLOSING_SERVICE_HOLE;
        Phase unavailable = serviceHoles.requireOpen();
        if (unavailable != null) return unavailable;

        Phase existingBlockPhase = PlaneReplenishDecisions.enderChestPlacementPhase(serviceHole.status());
        if (existingBlockPhase != Phase.PLACING_ENDER_CHEST) return existingBlockPhase;

        FindItemResult enderChest = inventory.prepareUsableEnderChest();
        if (!inventory.findResultMatchesBlock(enderChest, Blocks.ENDER_CHEST)) {
            return PlaneReplenishDecisions.enderChestInventoryPhase(
                false,
                inventory.findMainInventoryEnderChestSlot() >= 0
            );
        }

        placement.placeBlock(serviceHole.hole(), enderChest, Blocks.ENDER_CHEST);
        return Phase.PLACING_ENDER_CHEST;
    }

    Phase breakPlacedEnderChest() {
        if (!guards.readyForWorldAction()) return Phase.BREAKING_ENDER_CHEST;
        Phase unavailable = serviceHoles.requireReady();
        if (unavailable != null) {
            breakWatchdog.reset();
            return unavailable;
        }

        ServiceHoleContext.Status status = serviceHole.status();
        int buildBlocks = inventory.countBuildBlock();
        int targetBuildBlocks = effectiveReplenishTarget();
        if (status != ServiceHoleContext.Status.READY_ENDER_CHEST) {
            if (waitingForBreakCompletion && status == ServiceHoleContext.Status.READY_REPLACEABLE) {
                farmProgress.recordFarmedEnderChest(buildBlocks);
                buildBlocks = farmProgress.effectiveBuildBlocks(buildBlocks, visibleDroppedObsidian.getAsInt());
            }
            else {
                buildBlocks = farmProgress.effectiveBuildBlocks(buildBlocks, visibleDroppedObsidian.getAsInt());
            }
            waitingForBreakCompletion = false;
            breakWatchdog.reset();
            int sourceTarget = farmProgress.canFitAdditionalEnderChest(inventory.countBuildBlock(), targetBuildBlocks, visibleDroppedObsidian.getAsInt())
                ? targetBuildBlocks
                : buildBlocks;
            return PlaneReplenishDecisions.afterEnderChestBreak(status, buildBlocks, sourceTarget);
        }

        if (breakWatchdog.timeout(serviceHole.hole(), buildBlocks)) {
            logger.warning(
                "Recovering stale ender chest break: phase=%s serviceHole=%s serviceHoleStatus=%s buildBlocks=%d targetBuildBlocks=%d breakTicks=%d.",
                Phase.BREAKING_ENDER_CHEST.label(),
                serviceHole.hole(),
                status,
                buildBlocks,
                targetBuildBlocks,
                breakWatchdog.staleBreakTicks()
            );
            actions.clearInstantRebreakTarget();
            breakWatchdog.reset();
            serviceHole.markSelectedBlocked();
            return Phase.SELECTING_SERVICE_HOLE;
        }

        Phase next = actions.breakWithPickaxe(serviceHole.hole(), Phase.BREAKING_ENDER_CHEST, false);
        if (next == Phase.BREAKING_ENDER_CHEST) waitingForBreakCompletion = true;
        if (next != Phase.BREAKING_ENDER_CHEST) breakWatchdog.reset();
        return next;
    }

    boolean targetBuildBlockCountReached() {
        return !farmProgress.canFitAdditionalEnderChest(
            inventory.countBuildBlock(),
            effectiveReplenishTarget(),
            visibleDroppedObsidian.getAsInt()
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
}
