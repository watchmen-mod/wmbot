package com.watchmenbot.modules.stash;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

final class StashNavigator {
    private static final int REPATH_COOLDOWN_TICKS = 40;

    private String lastGoalKey;
    private int repathCooldown;

    void pathNear(StashTarget target, int interactionRange) {
        int radius = Math.max(2, interactionRange);
        rememberGoal("near:" + target.id() + ":" + radius);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(target.interactionPos(), radius));
    }

    void pathToScannerTarget(StashTarget target, int interactionRange, Optional<BlockPos> standingPos) {
        if (standingPos.isEmpty()) {
            pathNear(target, interactionRange);
            return;
        }

        BlockPos pos = standingPos.get();
        rememberGoal(scannerGoalKey(target, interactionRange, standingPos));
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
    }

    void returnTo(BlockPos startPos) {
        rememberGoal("return:" + startPos.getX() + "," + startPos.getY() + "," + startPos.getZ());
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(startPos, 1));
    }

    void ensurePathNear(StashTarget target, int interactionRange) {
        int radius = Math.max(2, interactionRange);
        String goalKey = "near:" + target.id() + ":" + radius;
        if (!shouldRepath(goalKey)) return;

        pathNear(target, interactionRange);
    }

    void ensureScannerPathTo(StashTarget target, int interactionRange, Optional<BlockPos> standingPos) {
        String goalKey = scannerGoalKey(target, interactionRange, standingPos);
        if (!shouldRepath(goalKey)) return;

        pathToScannerTarget(target, interactionRange, standingPos);
    }

    void ensureReturnTo(BlockPos startPos) {
        String goalKey = "return:" + startPos.getX() + "," + startPos.getY() + "," + startPos.getZ();
        if (!shouldRepath(goalKey)) return;

        returnTo(startPos);
    }

    boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }

    boolean hasPath() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().hasPath();
    }

    void stop() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
        lastGoalKey = null;
        repathCooldown = 0;
    }

    private boolean shouldRepath(String goalKey) {
        if (isPathing()) return false;
        if (!goalKey.equals(lastGoalKey)) return true;
        if (repathCooldown > 0) {
            repathCooldown--;
            return false;
        }

        return true;
    }

    private void rememberGoal(String goalKey) {
        lastGoalKey = goalKey;
        repathCooldown = REPATH_COOLDOWN_TICKS;
    }

    private String scannerGoalKey(StashTarget target, int interactionRange, Optional<BlockPos> standingPos) {
        if (standingPos.isEmpty()) return "near:" + target.id() + ":" + Math.max(2, interactionRange);

        BlockPos pos = standingPos.get();
        return "scanner:" + target.id() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
