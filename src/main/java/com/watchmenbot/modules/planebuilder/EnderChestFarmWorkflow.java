package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Blocks;

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
        this.inventory = inventory;
        this.placement = placement;
        this.guards = guards;
        this.serviceHole = serviceHole;
        this.serviceHoles = serviceHoles;
        this.actions = actions;
        this.replenishSettings = replenishSettings;
        this.logger = logger;
        this.farmProgress = farmProgress;
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
        return PlaneReplenishDecisions.sourcePhase(
            farmProgress.effectiveBuildBlocks(buildBlocks),
            effectiveReplenishTarget(),
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
                buildBlocks = farmProgress.effectiveBuildBlocks(buildBlocks);
            }
            else {
                buildBlocks = farmProgress.effectiveBuildBlocks(buildBlocks);
            }
            waitingForBreakCompletion = false;
            breakWatchdog.reset();
            return PlaneReplenishDecisions.afterEnderChestBreak(status, buildBlocks, targetBuildBlocks);
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
        return farmProgress.effectiveBuildBlocks(inventory.countBuildBlock()) >= effectiveReplenishTarget();
    }

    private int effectiveReplenishTarget() {
        return PlaneReplenishTargets.effectiveTarget(inventory, replenishSettings);
    }
}
