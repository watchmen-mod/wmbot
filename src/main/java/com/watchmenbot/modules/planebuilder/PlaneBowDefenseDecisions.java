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

    static boolean shouldRelease(int chargeTicks, int requiredChargeTicks, boolean directHit) {
        return chargeTicks >= requiredChargeTicks && directHit;
    }

    static boolean shouldCheckDirectHit(int chargeTicks, int requiredChargeTicks, int chargedAimTicks, int aimSettleTicks) {
        return chargeTicks >= requiredChargeTicks && chargedAimTicks >= aimSettleTicks;
    }

    static boolean timedOutWaitingForDirectHit(int aimWaitTicks, int maxAimWaitTicks) {
        return aimWaitTicks >= maxAimWaitTicks;
    }

    static boolean suppressesTarget(int suppressedTargetId, int targetId, int suppressionTicksRemaining) {
        return suppressedTargetId >= 0
            && targetId == suppressedTargetId
            && suppressionTicksRemaining > 0;
    }

    static boolean shouldClearSuppression(int suppressionTicksRemaining, boolean suppressedTargetSafe) {
        return suppressionTicksRemaining <= 0 || !suppressedTargetSafe;
    }
}
