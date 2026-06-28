package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class ServiceHoleOpenWatchdog {
    static final int DEFAULT_MAX_STALE_OPEN_TICKS = EnderChestBreakWatchdog.DEFAULT_MAX_STALE_BREAK_TICKS;

    private final int maxStaleOpenTicks;
    private BlockPos target;
    private ServiceHoleContext.HoleBlock block;
    private int staleOpenTicks;

    ServiceHoleOpenWatchdog() {
        this(DEFAULT_MAX_STALE_OPEN_TICKS);
    }

    ServiceHoleOpenWatchdog(int maxStaleOpenTicks) {
        this.maxStaleOpenTicks = Math.max(1, maxStaleOpenTicks);
    }

    void reset() {
        target = null;
        block = null;
        staleOpenTicks = 0;
    }

    boolean timeout(BlockPos currentTarget, ServiceHoleContext.HoleBlock currentBlock) {
        if (currentTarget == null || currentBlock == ServiceHoleContext.HoleBlock.REPLACEABLE) {
            reset();
            return false;
        }

        if (currentBlock != ServiceHoleContext.HoleBlock.BUILD_BLOCK
            && currentBlock != ServiceHoleContext.HoleBlock.BREAKABLE_CAP) {
            reset();
            return false;
        }

        if (!currentTarget.equals(target) || currentBlock != block) {
            target = currentTarget.toImmutable();
            block = currentBlock;
            staleOpenTicks = 0;
        }

        staleOpenTicks++;
        return staleOpenTicks >= maxStaleOpenTicks;
    }

    int staleOpenTicks() {
        return staleOpenTicks;
    }
}
