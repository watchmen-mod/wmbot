package com.watchmenbot.modules.planebuilder;

final class PlaneTrashDropWait {
    static final int DEFAULT_CLEAR_GRACE_TICKS = 10;
    static final int DEFAULT_MAX_WAIT_TICKS = 200;

    private final int clearGraceTicks;
    private final int maxWaitTicks;
    private int clearTicksRemaining;
    private int ticksWaiting;

    PlaneTrashDropWait() {
        this(DEFAULT_CLEAR_GRACE_TICKS, DEFAULT_MAX_WAIT_TICKS);
    }

    PlaneTrashDropWait(int clearGraceTicks) {
        this(clearGraceTicks, DEFAULT_MAX_WAIT_TICKS);
    }

    PlaneTrashDropWait(int clearGraceTicks, int maxWaitTicks) {
        this.clearGraceTicks = Math.max(0, clearGraceTicks);
        this.maxWaitTicks = Math.max(1, maxWaitTicks);
    }

    void start() {
        clearTicksRemaining = clearGraceTicks;
        ticksWaiting = 0;
    }

    void reset() {
        clearTicksRemaining = 0;
        ticksWaiting = 0;
    }

    Result tick(boolean nearbyTrashDrop) {
        ticksWaiting++;
        if (ticksWaiting >= maxWaitTicks) {
            reset();
            return Result.TIMED_OUT;
        }

        if (nearbyTrashDrop) {
            clearTicksRemaining = clearGraceTicks;
            return Result.WAITING;
        }
        if (clearTicksRemaining <= 1) {
            reset();
            return Result.CLEARED;
        }

        clearTicksRemaining--;
        return Result.WAITING;
    }

    enum Result {
        WAITING,
        CLEARED,
        TIMED_OUT
    }
}
