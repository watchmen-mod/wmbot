package com.watchmenbot.modules.planebuilder;

final class PlaneTrashEdgeDecisions {
    private PlaneTrashEdgeDecisions() {
    }

    static FallWaitDecision fallWait(boolean inventoryHasTrashItems, PlaneTrashDropWait.Result dropWaitResult) {
        if (inventoryHasTrashItems || dropWaitResult == PlaneTrashDropWait.Result.TIMED_OUT) {
            return new FallWaitDecision(Phase.IDLE, true);
        }
        if (dropWaitResult == PlaneTrashDropWait.Result.WAITING) {
            return new FallWaitDecision(Phase.WAITING_FOR_TRASH_TO_FALL, false);
        }

        return new FallWaitDecision(Phase.IDLE, false);
    }

    record FallWaitDecision(Phase phase, boolean exhaustCleanupCycle) {
    }
}
