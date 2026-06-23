package com.watchmenbot.modules.stash;

final class StashKitbotQueuePlanner {
    private StashKitbotQueuePlanner() {
    }

    static boolean shouldQueue(boolean activeRequest, int queuedCount) {
        return activeRequest || queuedCount > 0;
    }

    static boolean canEnqueue(int maxQueuedRequests, int queuedCount) {
        return maxQueuedRequests > 0 && queuedCount < maxQueuedRequests;
    }

    static int queuePosition(int queuedCount) {
        return queuedCount + 1;
    }
}
