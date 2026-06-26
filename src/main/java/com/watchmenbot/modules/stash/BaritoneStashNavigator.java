package com.watchmenbot.modules.stash;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.util.math.BlockPos;

final class BaritoneStashNavigator implements StashNavigator.BaritonePathing {
    @Override
    public void pathNear(BlockPos pos, int radius) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, radius));
    }

    @Override
    public void pathToBlock(BlockPos pos) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
    }

    @Override
    public boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    @Override
    public boolean hasPath() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().hasPath();
    }

    @Override
    public void stop() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
    }
}
