package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class StashScannerPathProgress {
    static final int GRACE_TICKS = 40;
    static final int NO_PROGRESS_TIMEOUT_TICKS = 60;
    static final int NO_PATH_GRACE_TICKS = 20;
    static final int NO_PATH_TIMEOUT_TICKS = 30;

    private static final double DISTANCE_IMPROVEMENT_SQ = 1.0;

    private String targetId;
    private BlockPos lastPlayerPos;
    private double bestDistanceSq;
    private int elapsedTicks;
    private int noProgressTicks;
    private int noPathTicks;

    void reset(StashTarget target, BlockPos playerPos, Vec3d playerEyes) {
        targetId = target == null ? null : target.id();
        lastPlayerPos = playerPos == null ? null : playerPos.toImmutable();
        bestDistanceSq = target == null || playerEyes == null ? Double.MAX_VALUE : target.distanceSq(playerEyes);
        elapsedTicks = 0;
        noProgressTicks = 0;
        noPathTicks = 0;
    }

    FastFailDecision tick(StashTarget target, BlockPos playerPos, Vec3d playerEyes, boolean baritoneHasPath) {
        if (target == null || playerPos == null || playerEyes == null) return FastFailDecision.WAIT;
        if (targetId == null || !targetId.equals(target.id())) reset(target, playerPos, playerEyes);

        elapsedTicks++;
        double distanceSq = target.distanceSq(playerEyes);
        boolean movedBlocks = lastPlayerPos != null && !lastPlayerPos.equals(playerPos);
        boolean improvedDistance = distanceSq <= bestDistanceSq - DISTANCE_IMPROVEMENT_SQ;
        boolean progressing = movedBlocks || improvedDistance;

        if (progressing) {
            bestDistanceSq = Math.min(bestDistanceSq, distanceSq);
            lastPlayerPos = playerPos.toImmutable();
            noProgressTicks = 0;
            noPathTicks = 0;
            return FastFailDecision.WAIT;
        }

        if (baritoneHasPath) noPathTicks = 0;
        else noPathTicks++;

        if (elapsedTicks >= NO_PATH_GRACE_TICKS && noPathTicks >= NO_PATH_TIMEOUT_TICKS) {
            return FastFailDecision.FAST_FAIL;
        }

        if (elapsedTicks >= GRACE_TICKS) noProgressTicks++;

        if (elapsedTicks >= GRACE_TICKS && noProgressTicks >= NO_PROGRESS_TIMEOUT_TICKS) {
            return FastFailDecision.FAST_FAIL;
        }

        return FastFailDecision.WAIT;
    }

    void clear() {
        targetId = null;
        lastPlayerPos = null;
        bestDistanceSq = Double.MAX_VALUE;
        elapsedTicks = 0;
        noProgressTicks = 0;
        noPathTicks = 0;
    }

    enum FastFailDecision {
        WAIT,
        FAST_FAIL
    }
}
