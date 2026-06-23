package com.watchmenbot.modules.stash;

import com.watchmenbot.util.TickTimer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

final class StashKitbotSession {
    private KitbotPhase phase = KitbotPhase.IDLE;
    private KitRequest activeRequest;
    private StashTarget currentTarget;
    private final TickTimer gatherTimer = new TickTimer();
    private final Queue<QueuedKitRequest> queuedRequests = new ArrayDeque<>();

    boolean hasActiveRequest() {
        return activeRequest != null;
    }

    KitRequest activeRequest() {
        return activeRequest;
    }

    void startRequest(KitRequest request) {
        activeRequest = request;
        phase = KitbotPhase.IDLE;
        currentTarget = null;
    }

    void clearRequest() {
        activeRequest = null;
        currentTarget = null;
        phase = KitbotPhase.IDLE;
    }

    void enqueue(QueuedKitRequest request) {
        if (request != null) queuedRequests.add(request);
    }

    RequesterRequestStatus requestStatus(String normalizedRequester) {
        if (normalizedRequester == null || normalizedRequester.isBlank()) return RequesterRequestStatus.NONE;

        if (activeRequest != null && normalizedRequester.equals(StashKitbotAccessPlanner.normalize(activeRequest.requester))) {
            return RequesterRequestStatus.ACTIVE;
        }

        for (QueuedKitRequest request : queuedRequests) {
            if (request != null && request.access() != null && normalizedRequester.equals(request.access().normalizedRequester())) {
                return RequesterRequestStatus.QUEUED;
            }
        }

        return RequesterRequestStatus.NONE;
    }

    void replaceQueuedRequests(List<QueuedKitRequest> requests) {
        queuedRequests.clear();
        if (requests == null) return;

        for (QueuedKitRequest request : requests) {
            if (request != null) queuedRequests.add(request);
        }
    }

    QueuedKitRequest pollNextQueuedRequest() {
        return queuedRequests.poll();
    }

    List<QueuedKitRequest> queuedRequestsSnapshot() {
        return new ArrayList<>(queuedRequests);
    }

    int queuedCount() {
        return queuedRequests.size();
    }

    void clearQueuedRequests() {
        queuedRequests.clear();
    }

    KitbotPhase phase() {
        return phase;
    }

    void phase(KitbotPhase phase) {
        this.phase = phase;
    }

    StashTarget currentTarget() {
        return currentTarget;
    }

    void currentTarget(StashTarget currentTarget) {
        this.currentTarget = currentTarget;
    }

    void clearCurrentTarget() {
        currentTarget = null;
    }

    void beginGatherPhase(KitbotPhase phase, int timeoutTicks) {
        this.phase = phase;
        gatherTimer.reset(timeoutTicks);
    }

    boolean tickGatherTimeoutExpired() {
        return gatherTimer.tickOrElapsedExpired();
    }

    boolean isDeliveryPhase() {
        return switch (phase) {
            case TPA_REQUEST, WAITING_FOR_TPY, REACQUIRE_REQUESTER, MOVE_TO_DELIVERY_SPOT, THROWING, PREPARED_THROW, HOME_REQUEST, HOME_COOLDOWN, HOME_CONFIRM, RETURN_TO_ORIGIN -> true;
            default -> false;
        };
    }

    String infoString() {
        String queued = queuedCount() <= 0 ? "" : " +%d queued".formatted(queuedCount());
        if (activeRequest == null) return queuedCount() <= 0 ? null : "%d queued".formatted(queuedCount());
        return "%s %d/%d %s%s".formatted(activeRequest.kitName, activeRequest.gather.gathered, activeRequest.count, phase.name().toLowerCase(Locale.ROOT), queued);
    }
}

enum RequesterRequestStatus {
    NONE,
    ACTIVE,
    QUEUED
}
