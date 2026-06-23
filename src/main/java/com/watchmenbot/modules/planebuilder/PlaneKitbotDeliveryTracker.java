package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotDeliveryTracker {
    private static final int REQUEST_RETRY_TICKS = 20 * 60;

    private final PlaneKitbotSupplyProbe supplyProbe;
    private final PlaneKitbotDroppedSupplyTracker droppedSupply;

    private boolean pending;
    private boolean requestSentThisCycle;
    private boolean requestSent;
    private int requestRetryTicks;
    private DeliveryBaseline deliveryBaseline;

    PlaneKitbotDeliveryTracker(PlaneKitbotSupplyProbe supplyProbe, PlaneKitbotDroppedSupplyTracker droppedSupply) {
        this.supplyProbe = supplyProbe;
        this.droppedSupply = droppedSupply == null ? PlaneKitbotDroppedSupplyTrackers.none() : droppedSupply;
    }

    void reset() {
        pending = false;
        requestSentThisCycle = false;
        requestSent = false;
        requestRetryTicks = 0;
        deliveryBaseline = null;
        droppedSupply.reset();
    }

    boolean pending() {
        return pending;
    }

    void beginReplenishCycle() {
        requestSentThisCycle = false;
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
        requestIfNeeded(messenger);
        return pending ? Phase.WAITING_FOR_KITBOT_REFILL : Phase.MISSING_ENDER_CHEST_SHULKER;
    }

    boolean consumeRequestSent() {
        boolean sent = requestSent;
        requestSent = false;
        return sent;
    }

    void markSuppliesAvailable() {
        pending = false;
        requestSent = false;
        requestRetryTicks = 0;
        deliveryBaseline = null;
        droppedSupply.reset();
    }

    private boolean requestIfNeeded(PlaneKitbotMessenger messenger) {
        if (pending) {
            retryPendingRequestIfReady(messenger);
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
        if (requestRetryTicks > 0) {
            requestRetryTicks--;
            return;
        }

        sendRequest(messenger);
    }

    private boolean sendRequest(PlaneKitbotMessenger messenger) {
        if (!messenger.sendRefillRequest()) return false;

        requestSent = true;
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
