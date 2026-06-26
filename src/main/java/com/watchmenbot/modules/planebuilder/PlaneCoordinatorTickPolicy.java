package com.watchmenbot.modules.planebuilder;

final class PlaneCoordinatorTickPolicy {
    private PlaneCoordinatorTickPolicy() {
    }

    static TickOwner owner(boolean replenishActive, boolean bowDefenseActive, boolean readyForWorldAction) {
        if (replenishActive) return TickOwner.REPLENISH;
        if (bowDefenseActive) return TickOwner.BOW_DEFENSE;
        if (!readyForWorldAction) return TickOwner.GUARD_PAUSED;

        return TickOwner.BUILD_LOOP;
    }

    static boolean shouldTickBowDefenseDuringReplenish(ReplenishTickResult result) {
        return result.bowDefenseAllowed() || PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(result.phase());
    }

    static boolean shouldResetBowDefenseAfterReplenishTick(ReplenishTickResult result) {
        return !shouldTickBowDefenseDuringReplenish(result);
    }

    static boolean shouldPreemptReplenishForSafety(Phase phase, boolean bowDefenseActive, boolean playerUsingItem) {
        return playerUsingItem
            || (bowDefenseActive && PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase));
    }

    static boolean shouldTickReplenishDuringSafetyPreemption(Phase phase) {
        return phase == Phase.WAITING_FOR_TRASH_TO_FALL;
    }

    static boolean shouldContinueAutoElytraLanding(Phase phase) {
        return phase == Phase.AUTO_ELYTRA_LANDING;
    }

    static boolean shouldCheckHoleEscapeDuringReplenish(Phase phase) {
        return phase == Phase.SERVICE_HOLE_BLOCKED
            || phase == Phase.CLOSING_SERVICE_HOLE
            || phase == Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL
            || phase == Phase.MOVING_TO_TRASH_EDGE;
    }

    enum TickOwner {
        REPLENISH,
        BOW_DEFENSE,
        GUARD_PAUSED,
        BUILD_LOOP
    }
}
