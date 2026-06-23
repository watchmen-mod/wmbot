package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlanePlacementRecovery {
    static final int ATTEMPT_THRESHOLD = 3;
    static final int NUDGE_TICKS = 4;

    private BlockPos lastTarget;
    private int consecutiveAttempts;
    private BlockPos nudgeTarget;
    private int nudgeTicksRemaining;

    void reset() {
        lastTarget = null;
        consecutiveAttempts = 0;
        nudgeTarget = null;
        nudgeTicksRemaining = 0;
    }

    BlockPos activeNudgeTarget(BlockPos currentTarget) {
        if (currentTarget == null || !currentTarget.equals(nudgeTarget) || nudgeTicksRemaining <= 0) return null;

        nudgeTicksRemaining--;
        if (nudgeTicksRemaining == 0) {
            nudgeTarget = null;
        }
        return currentTarget;
    }

    void targetObserved(BlockPos target) {
        if (target == null) {
            reset();
            return;
        }

        BlockPos immutable = target.toImmutable();
        if (!immutable.equals(lastTarget)) {
            lastTarget = immutable;
            consecutiveAttempts = 0;
            nudgeTarget = null;
            nudgeTicksRemaining = 0;
        }
    }

    void placementDispatched(BlockPos target) {
        if (target == null) return;

        BlockPos immutable = target.toImmutable();
        if (!immutable.equals(lastTarget)) {
            lastTarget = immutable;
            consecutiveAttempts = 0;
        }

        consecutiveAttempts++;
        if (consecutiveAttempts >= ATTEMPT_THRESHOLD && nudgeTicksRemaining <= 0) {
            nudgeTarget = immutable;
            nudgeTicksRemaining = NUDGE_TICKS;
            consecutiveAttempts = 0;
        }
    }

    boolean nudging() {
        return nudgeTicksRemaining > 0;
    }
}
