package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlaneCoordinatorOwnerWorkflow {
    private final PlaneAutoWalkController autoWalk;
    private final PlaneHoleEscapeController holeEscape;
    private final PlaneBuildLoop buildLoop;
    private final PlaneReplenishWorkflow replenish;
    private final PlaneBowDefenseWorkflow bowDefense;
    private final PlaneEndermanLookSafety endermanLookSafety;
    private final Callbacks callbacks;

    PlaneCoordinatorOwnerWorkflow(
        PlaneAutoWalkController autoWalk,
        PlaneHoleEscapeController holeEscape,
        PlaneBuildLoop buildLoop,
        PlaneReplenishWorkflow replenish,
        PlaneBowDefenseWorkflow bowDefense,
        PlaneEndermanLookSafety endermanLookSafety,
        Callbacks callbacks
    ) {
        this.autoWalk = autoWalk;
        this.holeEscape = holeEscape;
        this.buildLoop = buildLoop;
        this.replenish = replenish;
        this.bowDefense = bowDefense;
        this.endermanLookSafety = endermanLookSafety;
        this.callbacks = callbacks;
    }

    void tickReplenishOwner() {
        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.REPLENISH);
        holeEscape.reset();
        endermanLookSafety.lookDownIfUnsafe();
        syncBowDefenseAfterReplenishTick(replenish.tickResult());
    }

    void syncBowDefenseAfterReplenishTick(ReplenishTickResult replenishResult) {
        callbacks.setPhase(replenishResult.phase());
        if (PlaneCoordinatorTickPolicy.shouldResetBowDefenseAfterReplenishTick(replenishResult)) {
            bowDefense.reset();
            return;
        }

        boolean bowReplenishActive = PlanePhasePolicy.bowDefenseReplenishActive(callbacks.currentPhase(), replenish.active());
        boolean passiveBowDefenseWindow = PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(callbacks.currentPhase());
        bowDefense.tickResult(bowReplenishActive, passiveBowDefenseWindow);
    }

    void tickBuildLoopOwner(BlockPos playerPos) {
        BuildTickResult buildResult = buildLoop.tick(playerPos);
        if (buildResult.startReplenish()) {
            replenish.begin();
            autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.REPLENISH);
            bowDefense.reset();
            endermanLookSafety.lookDownIfUnsafe();
            callbacks.setPhase(replenish.tickResult().phase());
            return;
        }
        if (buildResult.resetBowDefense()) bowDefense.reset();
        if (buildResult.phase() == Phase.IDLE) endermanLookSafety.lookDown();
        callbacks.setPhase(buildResult.phase());
    }

    interface Callbacks {
        Phase currentPhase();

        void setPhase(Phase phase);
    }
}
