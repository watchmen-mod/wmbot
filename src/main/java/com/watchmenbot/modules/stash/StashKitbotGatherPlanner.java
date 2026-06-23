package com.watchmenbot.modules.stash;

final class StashKitbotGatherPlanner {
    private StashKitbotGatherPlanner() {
    }

    static int confirmedGathered(int requestCount, int initialInventoryCount, int currentInventoryCount) {
        int initialFulfilled = Math.min(Math.max(0, requestCount), Math.max(0, initialInventoryCount));
        int transferred = Math.max(0, currentInventoryCount - Math.max(0, initialInventoryCount));
        return Math.min(Math.max(0, requestCount), initialFulfilled + transferred);
    }

    static TransferVerifyDecision transferVerifyDecision(
        int confirmedGathered,
        int requestCount,
        int beforeTransferInventoryCount,
        int currentInventoryCount,
        boolean containerOpen,
        boolean verifyExpired
    ) {
        if (confirmedGathered >= requestCount) return TransferVerifyDecision.COMPLETE_REQUEST;
        if (!containerOpen) return TransferVerifyDecision.CONTAINER_CLOSED;
        if (currentInventoryCount > beforeTransferInventoryCount || verifyExpired) return TransferVerifyDecision.CONTINUE_TAKING;
        return TransferVerifyDecision.WAIT;
    }

    enum TransferVerifyDecision {
        WAIT,
        CONTINUE_TAKING,
        COMPLETE_REQUEST,
        CONTAINER_CLOSED
    }
}
