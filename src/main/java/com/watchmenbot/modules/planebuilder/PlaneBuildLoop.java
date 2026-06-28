package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.util.math.BlockPos;

final class PlaneBuildLoop {
    private final PlaneInventory inventory;
    private final PlaneAreaScanner scanner;
    private final PlanePlacement placement;
    private final PlanePlacementStatsTracker stats;
    private final PlaneAutoWalkController autoWalk;
    private final PlaneRuntimeConfig config;
    private final PlanePlacementRecovery placementRecovery;

    PlaneBuildLoop(
        PlaneInventory inventory,
        PlaneAreaScanner scanner,
        PlanePlacement placement,
        PlanePlacementStatsTracker stats,
        PlaneAutoWalkController autoWalk
    ) {
        this(inventory, scanner, placement, stats, autoWalk, PlaneRuntimeConfig.DEFAULT);
    }

    PlaneBuildLoop(
        PlaneInventory inventory,
        PlaneAreaScanner scanner,
        PlanePlacement placement,
        PlanePlacementStatsTracker stats,
        PlaneAutoWalkController autoWalk,
        PlaneRuntimeConfig config
    ) {
        this(inventory, scanner, placement, stats, autoWalk, config, new PlanePlacementRecovery());
    }

    PlaneBuildLoop(
        PlaneInventory inventory,
        PlaneAreaScanner scanner,
        PlanePlacement placement,
        PlanePlacementStatsTracker stats,
        PlaneAutoWalkController autoWalk,
        PlaneRuntimeConfig config,
        PlanePlacementRecovery placementRecovery
    ) {
        this.inventory = inventory;
        this.scanner = scanner;
        this.placement = placement;
        this.stats = stats;
        this.autoWalk = autoWalk;
        this.config = config;
        this.placementRecovery = placementRecovery;
    }

    BuildTickResult tick(BlockPos playerPos) {
        int buildBlockCount = inventory.countBuildBlock();
        BuildTickResult supplyResult = PlaneBuildLoopDecisions.resultFor(
            buildBlockCount,
            config.replenishMinBuildBlocks(),
            true,
            true,
            Phase.IDLE
        );
        if (supplyResult.startReplenish()) {
            placementRecovery.reset();
            autoWalk.reset();
            return supplyResult;
        }

        BuildBlockPreparation obsidianPreparation = inventory.prepareBuildBlock();
        FindItemResult obsidian = obsidianPreparation.result();
        boolean preparedBuildBlock = inventory.findResultMatchesBuildBlock(obsidian);
        if (!preparedBuildBlock) {
            placementRecovery.reset();
            autoWalk.reset();
            return PlaneBuildLoopDecisions.resultFor(
                inventory.countBuildBlock(),
                config.replenishMinBuildBlocks(),
                false,
                true,
                Phase.IDLE
            );
        }

        BlockPos target = scanner.nearestPlaceableTarget();
        if (target == null) {
            placementRecovery.reset();
            if (!autoWalk.flying()) autoWalk.releaseAutoElytraLockout();
            Phase autoWalkPhase = autoWalk.tick(playerPos);
            return PlaneBuildLoopDecisions.resultFor(
                config.replenishMinBuildBlocks(),
                config.replenishMinBuildBlocks(),
                true,
                false,
                autoWalkPhase
            );
        }

        placementRecovery.targetObserved(target);
        if (autoWalk.flying()) {
            autoWalk.tick(playerPos);
            placeTarget(target, obsidian);
            return PlaneBuildLoopDecisions.resultFor(
                config.replenishMinBuildBlocks(),
                config.replenishMinBuildBlocks(),
                true,
                true,
                Phase.AUTO_ELYTRA_FLYING
            );
        }

        Phase landingPhase = autoWalk.landBeforeWorldAction(playerPos, PlaneAutoWalkController.LockoutReason.PLACEMENT);
        if (landingPhase != Phase.IDLE) {
            return BuildTickResult.phase(landingPhase);
        }

        BlockPos nudgeTarget = placementRecovery.activeNudgeTarget(target);
        if (nudgeTarget != null) {
            autoWalk.nudgeTowardPlacementTarget(nudgeTarget);
            return PlaneBuildLoopDecisions.resultFor(
                config.replenishMinBuildBlocks(),
                config.replenishMinBuildBlocks(),
                true,
                true,
                Phase.IDLE
            );
        }

        autoWalk.suspend();
        placeTarget(target, obsidian);
        return PlaneBuildLoopDecisions.resultFor(
            config.replenishMinBuildBlocks(),
            config.replenishMinBuildBlocks(),
            true,
            true,
            Phase.IDLE
        );
    }

    private void placeTarget(BlockPos target, FindItemResult obsidian) {
        if (placement.placeObsidian(target, obsidian)) {
            placementRecovery.placementDispatched(target);
            stats.attemptedPlacement(target);
        }
    }

    void reset() {
        placementRecovery.reset();
    }
}
