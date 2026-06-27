package com.watchmenbot.modules.planebuilder;

final class PlaneBowDefenseDecisions {
    private PlaneBowDefenseDecisions() {
    }

    static boolean canRun(
        boolean enabled,
        boolean replenishActive,
        boolean readyForWorldAction,
        boolean hasHotbarBow,
        boolean hasArrows,
        boolean hasTarget
    ) {
        return enabled
            && !replenishActive
            && readyForWorldAction
            && hasHotbarBow
            && hasArrows
            && hasTarget;
    }

    static boolean canContinue(boolean readyForBowContinue, boolean replenishActive) {
        return readyForBowContinue && !replenishActive;
    }

    static boolean shouldRelease(int chargeTicks, int requiredChargeTicks, int stableDirectHitTicks, int requiredStableDirectHitTicks) {
        return chargeTicks >= requiredChargeTicks && stableDirectHitTicks >= requiredStableDirectHitTicks;
    }

    static boolean shouldReleaseWhenPredictionUnavailable(
        int chargeTicks,
        int requiredChargeTicks,
        int stableAimTicks,
        int requiredStableAimTicks
    ) {
        return chargeTicks >= requiredChargeTicks && stableAimTicks >= requiredStableAimTicks;
    }

    static boolean timedOutWaitingForDirectHit(int aimWaitTicks, int maxAimWaitTicks) {
        return aimWaitTicks >= maxAimWaitTicks;
    }
}
