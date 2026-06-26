package com.watchmenbot.modules.stash;

import com.watchmenbot.util.BaritoneCompatibility;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

final class StashNavigator {
    private static final int REPATH_COOLDOWN_TICKS = 40;

    private final BaritonePathing pathing;
    private String lastGoalKey;
    private int repathCooldown;

    StashNavigator() {
        pathing = BaritoneCompatibility.available() ? new BaritoneStashNavigator() : null;
    }

    void pathNear(StashTarget target, int interactionRange) {
        if (pathing == null) return;

        int radius = Math.max(2, interactionRange);
        rememberGoal("near:" + target.id() + ":" + radius);
        pathing.pathNear(target.interactionPos(), radius);
    }

    void pathToScannerTarget(StashTarget target, int interactionRange, Optional<BlockPos> standingPos) {
        if (pathing == null) return;

        if (standingPos.isEmpty()) {
            pathNear(target, interactionRange);
            return;
        }

        BlockPos pos = standingPos.get();
        rememberGoal(scannerGoalKey(target, interactionRange, standingPos));
        pathing.pathToBlock(pos);
    }

    void returnTo(BlockPos startPos) {
        if (pathing == null) return;

        rememberGoal("return:" + startPos.getX() + "," + startPos.getY() + "," + startPos.getZ());
        pathing.pathNear(startPos, 1);
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
        return pathing != null && pathing.isPathing();
    }

    boolean hasPath() {
        return pathing != null && pathing.hasPath();
    }

    void stop() {
        if (pathing != null) pathing.stop();
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

    interface BaritonePathing {
        void pathNear(BlockPos pos, int radius);

        void pathToBlock(BlockPos pos);

        boolean isPathing();

        boolean hasPath();

        void stop();
    }
}
