package com.watchmenbot.modules.planebuilder;

import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;

final class PlaneKitbotRefillTestSupport {
    private PlaneKitbotRefillTestSupport() {
    }

    static void waitForDeliveryTicks(PlaneKitbotRefillWorkflow workflow, int ticks, String message) {
        for (int i = 0; i < ticks; i++) {
            assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), message + " " + i);
        }
    }

    static PlaneKitbotRefillWorkflow refillWorkflow(List<String> sent, MutableSupplyProbe supply, int requiredEnderChests) {
        return new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            (PlaneKitbotDroppedSupplyTracker) null,
            null,
            () -> requiredEnderChests
        );
    }

    static final class MutableSupplyProbe implements PlaneKitbotSupplyProbe {
        int looseEnderChestCount;
        int enderChestsInShulkersCount;

        MutableSupplyProbe(int looseEnderChestCount, boolean hasEnderChestShulker) {
            this(looseEnderChestCount, hasEnderChestShulker ? 1 : 0);
        }

        MutableSupplyProbe(int looseEnderChestCount, int enderChestsInShulkersCount) {
            this.looseEnderChestCount = looseEnderChestCount;
            this.enderChestsInShulkersCount = enderChestsInShulkersCount;
        }

        @Override
        public boolean hasLooseEnderChests() {
            return looseEnderChestCount > 0;
        }

        @Override
        public int looseEnderChestCount() {
            return looseEnderChestCount;
        }

        @Override
        public boolean hasEnderChestShulker() {
            return enderChestsInShulkersCount > 0;
        }

        @Override
        public int enderChestsInShulkersCount() {
            return enderChestsInShulkersCount;
        }

        @Override
        public int enderChestSupplyCount() {
            return looseEnderChestCount + enderChestsInShulkersCount;
        }
    }

    static final class MutableDroppedSupplyTracker implements PlaneKitbotDroppedSupplyTracker {
        String visibleDropId;
        private String baselineDropId;
        int pickupTicks;

        MutableDroppedSupplyTracker(String visibleDropId) {
            this.visibleDropId = visibleDropId;
        }

        @Override
        public void captureBaseline() {
            baselineDropId = visibleDropId;
        }

        @Override
        public boolean hasNewDeliveryDrop() {
            return visibleDropId != null && !visibleDropId.equals(baselineDropId);
        }

        @Override
        public Phase tickPickup() {
            if (!hasNewDeliveryDrop()) return Phase.WAITING_FOR_KITBOT_REFILL;

            pickupTicks++;
            return Phase.PICKING_UP_KITBOT_REFILL;
        }

        @Override
        public void reset() {
            baselineDropId = null;
            pickupTicks = 0;
        }
    }
}
