package com.watchmenbot.modules.planebuilder;

final class PlaneBuildLoopDecisions {
    private PlaneBuildLoopDecisions() {
    }

    static BuildTickResult resultFor(
        int buildBlockCount,
        int replenishMinBuildBlocks,
        boolean preparedBuildBlock,
        boolean targetFound,
        Phase autoWalkPhase
    ) {
        if (buildBlockCount < replenishMinBuildBlocks) return BuildTickResult.startingReplenish();
        if (!preparedBuildBlock) return BuildTickResult.missingObsidian();
        if (!targetFound) return BuildTickResult.phase(autoWalkPhase);

        return BuildTickResult.phase(Phase.PLACING_OBSIDIAN);
    }
}
