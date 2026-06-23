package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class ServiceHoleExitWorkflow {
    private final ServiceHoleExitPlanner planner;
    private final PlaneHoleEscapeController.Navigator navigator;

    ServiceHoleExitWorkflow(ServiceHoleExitPlanner planner, PlaneHoleEscapeController.Navigator navigator) {
        this.planner = planner;
        this.navigator = navigator;
    }

    ExitResult tick(BlockPos playerPos, BlockPos serviceHole) {
        if (!planner.insideServiceHole(playerPos, serviceHole)) {
            reset();
            return new ExitResult(false, null, false);
        }

        BlockPos target = planner.exitTarget(playerPos, serviceHole);
        if (target != null) navigator.pathTo(target);
        else navigator.stop();

        return new ExitResult(true, target, target == null);
    }

    void reset() {
        navigator.stop();
    }

    record ExitResult(boolean active, BlockPos target, boolean missingTarget) {
    }
}
