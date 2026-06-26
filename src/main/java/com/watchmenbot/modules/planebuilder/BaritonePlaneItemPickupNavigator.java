package com.watchmenbot.modules.planebuilder;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.util.math.BlockPos;

final class BaritonePlaneItemPickupNavigator implements PlaneItemPickupNavigator.BaritoneItemPickupPathing {
    private static final int PICKUP_GOAL_RADIUS = 1;

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
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(target, PICKUP_GOAL_RADIUS));
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
