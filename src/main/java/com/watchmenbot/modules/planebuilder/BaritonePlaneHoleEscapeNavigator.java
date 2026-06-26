package com.watchmenbot.modules.planebuilder;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

final class BaritonePlaneHoleEscapeNavigator implements PlaneHoleEscapeNavigator.BaritoneHoleEscapePathing {
    private final PlaneBaritoneSafetyGuard safetyGuard = new PlaneBaritoneSafetyGuard();

    @Override
    public void applySafety() {
        safetyGuard.apply();
    }

    @Override
    public void restoreSafety() {
        safetyGuard.restore();
    }

    @Override
    public void pathTo(BlockPos target) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
    }

    @Override
    public boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    @Override
    public void stop() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
    }
}
