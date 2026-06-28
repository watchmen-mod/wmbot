package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotDeliveryTracker {
    private static final int REQUEST_RETRY_TICKS = 20 * 60;

    private final PlaneKitbotSupplyProbe supplyProbe;
    private final PlaneKitbotDroppedSupplyTracker droppedSupply;

    private boolean pending;
    private boolean requestSentThisCycle;
    private boolean requestSent;
    private boolean kitbotRequestActive;
    private int requestRetryTicks;
    private int failureRetryCooldownTicks;
    private DeliveryBaseline deliveryBaseline;

    PlaneKitbotDeliveryTracker(PlaneKitbotSupplyProbe supplyProbe, PlaneKitbotDroppedSupplyTracker droppedSupply) {
        this.supplyProbe = supplyProbe;
        this.droppedSupply = droppedSupply == null ? PlaneKitbotDroppedSupplyTrackers.none() : droppedSupply;
    }

    void reset() {
        pending = false;
        requestSentThisCycle = false;
        requestSent = false;
        kitbotRequestActive = false;
        requestRetryTicks = 0;
        failureRetryCooldownTicks = 0;
        deliveryBaseline = null;
        droppedSupply.reset();
    }

    boolean pending() {
        return pending;
    }

    void beginReplenishCycle() {
        requestSentThisCycle = false;
    }

    void handleKitbotDeliveryMessage(PlaneKitbotRefillDecisions.KitbotDeliveryMessage message) {
        if (message == PlaneKitbotRefillDecisions.KitbotDeliveryMessage.ACTIVE
            || message == PlaneKitbotRefillDecisions.KitbotDeliveryMessage.TPA_REQUESTED
            || message == PlaneKitbotRefillDecisions.KitbotDeliveryMessage.DELIVERED) {
            if (pending) kitbotRequestActive = true;
            return;
        }

        if (message == PlaneKitbotRefillDecisions.KitbotDeliveryMessage.FAILED) {
            pending = false;
            requestSentThisCycle = false;
            requestSent = false;
            kitbotRequestActive = false;
            requestRetryTicks = 0;
            failureRetryCooldownTicks = REQUEST_RETRY_TICKS;
            deliveryBaseline = null;
            droppedSupply.reset();
        }
    }

    Phase inventoryDeliveryPhase() {
        if (!pending || deliveryBaseline == null) {
            return PlaneKitbotRefillDecisions.waitingPhase(
                supplyProbe.hasLooseEnderChests(),
                supplyProbe.hasEnderChestShulker()
            );
        }

        return PlaneKitbotRefillDecisions.waitingPhase(
            deliveryBaseline.looseEnderChestCountIncreased(supplyProbe.looseEnderChestCount()),
            deliveryBaseline.enderChestsInShulkersCountIncreased(supplyProbe.enderChestsInShulkersCount())
        );
    }

    boolean hasNewDeliveryDrop() {
        return droppedSupply.hasNewDeliveryDrop();
    }

    Phase tickPickup() {
        return droppedSupply.tickPickup();
    }

    Phase requestOrMissingSupply(PlaneKitbotMessenger messenger) {
        requestSent = false;
        boolean waiting = requestIfNeeded(messenger);
        return waiting ? Phase.WAITING_FOR_KITBOT_REFILL : Phase.MISSING_ENDER_CHEST_SHULKER;
    }

    boolean consumeRequestSent() {
        boolean sent = requestSent;
        requestSent = false;
        return sent;
    }

    void markSuppliesAvailable() {
        pending = false;
        requestSentThisCycle = false;
        requestSent = false;
        kitbotRequestActive = false;
        requestRetryTicks = 0;
        failureRetryCooldownTicks = 0;
        deliveryBaseline = null;
        droppedSupply.reset();
    }

    private boolean requestIfNeeded(PlaneKitbotMessenger messenger) {
        if (pending) {
            retryPendingRequestIfReady(messenger);
            return true;
        }
        if (failureRetryCooldownTicks > 0) {
            failureRetryCooldownTicks--;
            return true;
        }
        if (requestSentThisCycle) return false;
        if (!sendRequest(messenger)) return false;

        pending = true;
        requestSentThisCycle = true;
        deliveryBaseline = DeliveryBaseline.capture(supplyProbe);
        droppedSupply.captureBaseline();
        return true;
    }

    private void retryPendingRequestIfReady(PlaneKitbotMessenger messenger) {
        if (kitbotRequestActive) return;
        if (requestRetryTicks > 0) {
            requestRetryTicks--;
            return;
        }

        sendRequest(messenger);
    }

    private boolean sendRequest(PlaneKitbotMessenger messenger) {
        if (!messenger.sendRefillRequest()) return false;

        requestSent = true;
        kitbotRequestActive = false;
        requestRetryTicks = REQUEST_RETRY_TICKS;
        return true;
    }

    private record DeliveryBaseline(int looseEnderChestCount, int enderChestsInShulkersCount) {
        static DeliveryBaseline capture(PlaneKitbotSupplyProbe supplyProbe) {
            return new DeliveryBaseline(
                supplyProbe.looseEnderChestCount(),
                supplyProbe.enderChestsInShulkersCount()
            );
        }

        boolean looseEnderChestCountIncreased(int currentLooseEnderChestCount) {
            return currentLooseEnderChestCount > looseEnderChestCount;
        }

        boolean enderChestsInShulkersCountIncreased(int currentEnderChestsInShulkersCount) {
            return currentEnderChestsInShulkersCount > enderChestsInShulkersCount;
        }
    }
}
