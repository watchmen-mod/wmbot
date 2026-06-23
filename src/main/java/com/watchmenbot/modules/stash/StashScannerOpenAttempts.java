package com.watchmenbot.modules.stash;

final class StashScannerOpenAttempts {
    static final int MAX_ATTEMPTS = 4;
    static final int RETRY_DELAY_TICKS = 8;
    static final int CALLBACK_WAIT_TICKS = 12;

    private String targetId;
    private int attempts;
    private int ticksSinceAttempt;
    private boolean callbackRan;
    private boolean accepted;

    void start(StashTarget target) {
        targetId = target == null ? null : target.id();
        attempts = 0;
        ticksSinceAttempt = 0;
        callbackRan = false;
        accepted = false;
    }

    void markAttemptSent(StashTarget target) {
        if (target == null) return;
        if (targetId == null || !targetId.equals(target.id())) start(target);

        attempts++;
        ticksSinceAttempt = 0;
        callbackRan = false;
        accepted = false;
    }

    void recordInteraction(InteractionResult result) {
        if (result == null) return;

        callbackRan = result.callbackRan();
        accepted = result.accepted();
    }

    Decision tick() {
        ticksSinceAttempt++;

        if (attempts <= 0) return Decision.RETRY;
        if (!callbackRan && ticksSinceAttempt >= CALLBACK_WAIT_TICKS) return retryOrFail();
        if (callbackRan && !accepted && ticksSinceAttempt >= RETRY_DELAY_TICKS) return retryOrFail();
        if (callbackRan && accepted && ticksSinceAttempt >= RETRY_DELAY_TICKS) return retryOrFail();
        return Decision.WAIT;
    }

    void clear() {
        targetId = null;
        attempts = 0;
        ticksSinceAttempt = 0;
        callbackRan = false;
        accepted = false;
    }

    int attempts() {
        return attempts;
    }

    private Decision retryOrFail() {
        return attempts >= MAX_ATTEMPTS ? Decision.FAIL : Decision.RETRY;
    }

    enum Decision {
        WAIT,
        RETRY,
        FAIL
    }

    record InteractionResult(boolean callbackRan, boolean accepted) {
    }
}
