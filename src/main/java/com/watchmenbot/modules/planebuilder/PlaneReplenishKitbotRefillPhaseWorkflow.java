package com.watchmenbot.modules.planebuilder;

final class PlaneReplenishKitbotRefillPhaseWorkflow {
    private final ServiceHoleWorkflow serviceHoles;
    private final PlaneKitbotRefillWorkflow kitbotRefill;

    PlaneReplenishKitbotRefillPhaseWorkflow(ServiceHoleWorkflow serviceHoles, PlaneKitbotRefillWorkflow kitbotRefill) {
        this.serviceHoles = serviceHoles;
        this.kitbotRefill = kitbotRefill;
    }

    void reset() {
        kitbotRefill.reset();
    }

    void beginReplenishCycle() {
        kitbotRefill.beginReplenishCycle();
    }

    boolean pending() {
        return kitbotRefill.pending();
    }

    boolean pendingOwnsPhase(Phase phase) {
        return ownsPhase(phase);
    }

    boolean hasQueuedTeleportAccept() {
        return kitbotRefill.hasQueuedTeleportAccept();
    }

    void tickQueuedTeleportAccept() {
        kitbotRefill.tickQueuedTeleportAccept();
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult handleMessage(String message) {
        return kitbotRefill.handleTeleportPrompt(message);
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return kitbotRefill.ignoredTeleportPrompt(message);
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult consumeTeleportAcceptResult() {
        return kitbotRefill.consumeTeleportAcceptResult();
    }

    Phase closeServiceHoleForKitbotRefill() {
        Phase closePhase = serviceHoles.close();
        if (closePhase == Phase.IDLE) return requestKitbotRefillOrMissingSupply();
        if (closePhase == Phase.CLOSING_SERVICE_HOLE) return Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL;

        return closePhase;
    }

    Phase waitForKitbotRefill() {
        return kitbotRefill.waitForDelivery();
    }

    Phase pickUpKitbotRefill() {
        return kitbotRefill.pickUpDelivery();
    }

    static boolean ownsPhase(Phase phase) {
        return phase == Phase.WAITING_FOR_KITBOT_REFILL
            || phase == Phase.PICKING_UP_KITBOT_REFILL;
    }

    private Phase requestKitbotRefillOrMissingSupply() {
        return kitbotRefill.afterServiceHoleClosed();
    }
}
