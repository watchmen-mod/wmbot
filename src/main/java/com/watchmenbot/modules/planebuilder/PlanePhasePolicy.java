package com.watchmenbot.modules.planebuilder;

final class PlanePhasePolicy {
    private PlanePhasePolicy() {
    }

    static boolean replenishActive(Phase phase, boolean serviceHoleSelected) {
        return phase.replenishActive();
    }

    static boolean shouldKeepBowDefenseDuringReplenish(Phase phase) {
        return phase.replenishActive()
            && passiveBowDefenseReplenishPhase(phase);
    }

    static boolean bowDefenseReplenishActive(Phase phase, boolean replenishActive) {
        return shouldKeepBowDefenseDuringReplenish(phase) ? false : replenishActive;
    }

    private static boolean passiveBowDefenseReplenishPhase(Phase phase) {
        return phase == Phase.MISSING_OBSIDIAN
            || phase == Phase.SELECTING_SERVICE_HOLE
            || phase == Phase.SERVICE_HOLE_OPEN
            || phase == Phase.SELECTING_REPLENISH_SOURCE
            || phase == Phase.SERVICE_HOLE_BLOCKED
            || phase == Phase.MISSING_ENDER_CHEST
            || phase == Phase.MISSING_ENDER_CHEST_SHULKER
            || phase == Phase.WAITING_FOR_KITBOT_REFILL
            || phase == Phase.PICKING_UP_KITBOT_REFILL
            || phase == Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER
            || phase == Phase.PICKING_UP_REPLENISH_DROPS
            || phase == Phase.MOVING_TO_TRASH_EDGE
            || phase == Phase.WAITING_FOR_TRASH_TO_FALL;
    }
}
