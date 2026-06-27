package com.watchmenbot.modules.planebuilder;

final class PlaneBowFiringPolicy {
    static final int RELIABLE_USE_TICKS = 30;
    static final int DRAW_START_TIMEOUT_TICKS = 12;
    static final int DRAW_STALL_TIMEOUT_TICKS = 6;
    static final int REQUIRED_AIM_SETTLE_TICKS = 4;
    static final int REQUIRED_DIRECT_HIT_TICKS = 2;
    static final int REQUIRED_FALLBACK_AIM_TICKS = 10;
    static final double MAX_STABLE_AIM_DELTA_DEGREES = 2.0;
    static final double MAX_FALLBACK_TARGET_SPEED_SQUARED = 0.12 * 0.12;

    private PlaneBowFiringPolicy() {
    }

    static boolean drawStarted(boolean usingItem, int useTicks) {
        return usingItem && useTicks > 0;
    }

    static boolean drawStartTimedOut(int stateTicks) {
        return stateTicks >= DRAW_START_TIMEOUT_TICKS;
    }

    static boolean drawStalled(boolean usingItem, int useTicks, int previousUseTicks, int unchangedTicks) {
        return !usingItem || (useTicks <= previousUseTicks && unchangedTicks >= DRAW_STALL_TIMEOUT_TICKS);
    }

    static boolean enoughDraw(int useTicks) {
        return useTicks >= RELIABLE_USE_TICKS;
    }

    static boolean aimStable(PlaneBowAimController.Aim previous, PlaneBowAimController.Aim current) {
        if (previous == null || current == null) return false;
        return Math.abs(previous.yaw() - current.yaw()) <= MAX_STABLE_AIM_DELTA_DEGREES
            && Math.abs(previous.pitch() - current.pitch()) <= MAX_STABLE_AIM_DELTA_DEGREES;
    }

    static boolean targetStable(double velocitySquared) {
        return velocitySquared <= MAX_FALLBACK_TARGET_SPEED_SQUARED;
    }

    static boolean shouldReleaseDirect(int useTicks, int settleTicks, int directHitTicks) {
        return enoughDraw(useTicks)
            && settleTicks >= REQUIRED_AIM_SETTLE_TICKS
            && directHitTicks >= REQUIRED_DIRECT_HIT_TICKS;
    }

    static boolean shouldReleaseFallback(int useTicks, int settleTicks, int fallbackAimTicks, double targetVelocitySquared) {
        return enoughDraw(useTicks)
            && settleTicks >= REQUIRED_AIM_SETTLE_TICKS
            && fallbackAimTicks >= REQUIRED_FALLBACK_AIM_TICKS
            && targetStable(targetVelocitySquared);
    }
}
