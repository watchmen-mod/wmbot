package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class EnderChestBreakWatchdog {
    static final int DEFAULT_MAX_STALE_BREAK_TICKS = 80;

    private final int maxStaleBreakTicks;
    private BlockPos target;
    private int baselineBuildBlocks = -1;
    private int staleBreakTicks;

    EnderChestBreakWatchdog() {
        this(DEFAULT_MAX_STALE_BREAK_TICKS);
    }

    EnderChestBreakWatchdog(int maxStaleBreakTicks) {
        this.maxStaleBreakTicks = Math.max(1, maxStaleBreakTicks);
    }

    void reset() {
        target = null;
        baselineBuildBlocks = -1;
        staleBreakTicks = 0;
    }

    boolean timeout(BlockPos currentTarget, int buildBlockCount) {
        if (currentTarget == null) {
            reset();
            return false;
        }

        if (!currentTarget.equals(target) || buildBlockCount > baselineBuildBlocks) {
            target = currentTarget.toImmutable();
            baselineBuildBlocks = buildBlockCount;
            staleBreakTicks = 0;
        }

        staleBreakTicks++;
        return staleBreakTicks >= maxStaleBreakTicks;
    }

    int staleBreakTicks() {
        return staleBreakTicks;
    }
}
