package com.watchmenbot.modules.planebuilder;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

final class PlaneHoleEscapeNavigator implements PlaneHoleEscapeController.Navigator {
    private final PlaneBaritoneSafetyGuard safetyGuard = new PlaneBaritoneSafetyGuard();

    private BlockPos targetPos;
    private boolean active;

    @Override
    public void pathTo(BlockPos target) {
        if (target == null) return;

        safetyGuard.apply();
        active = true;

        if (target.equals(targetPos) && isPathing()) return;

        targetPos = target.toImmutable();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos));
    }

    @Override
    public void stop() {
        if (active) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
        }

        active = false;
        targetPos = null;
        safetyGuard.restore();
    }

    private boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }
}
