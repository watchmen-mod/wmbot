package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlanePlacementStatsTracker {
    private final PlaneWorldAccess world;
    private final PlaneClientContext context;
    private final PlaneBuilderStats stats;
    private final PlaneRuntimeConfig config;

    PlanePlacementStatsTracker() {
        this(new PlaneClientContext(), new PlaneBuilderStats());
    }

    PlanePlacementStatsTracker(PlaneClientContext context, PlaneBuilderStats stats) {
        this(context, stats, PlaneRuntimeConfig.DEFAULT);
    }

    PlanePlacementStatsTracker(PlaneClientContext context, PlaneBuilderStats stats, PlaneRuntimeConfig config) {
        this.context = context;
        this.world = new PlaneWorldAccess(context);
        this.stats = stats;
        this.config = config;
    }

    void reset() {
        stats.reset();
    }

    void startSession(long nowMillis) {
        stats.start(nowMillis);
    }

    void attemptedPlacement(BlockPos target) {
        stats.attemptedPlacement(target);
    }

    void confirmPendingPlacements(long nowMillis) {
        if (!context.worldReady()) return;

        for (BlockPos target : stats.pendingTargets()) {
            if (world.isBlock(target, config.buildBlock())) {
                stats.confirmPlaced(target, nowMillis);
            }
        }
    }

    PlaneBuilderStats.Snapshot snapshot(long nowMillis) {
        confirmPendingPlacements(nowMillis);
        return stats.snapshot(nowMillis);
    }
}
