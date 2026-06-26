package com.watchmenbot.modules.planebuilder;

import java.util.ArrayList;
import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneKitbotRefillSupplyPureTest {
    private PlaneKitbotRefillSupplyPureTest() {
    }

    static void run() {
        skipsKitbotRequestWhenLooseSupplyIsEnough();
        skipsKitbotRequestWhenCombinedSupplyIsEnough();
        skipsKitbotRequestWhileAnyUsableSupplyExists();
        suppressesKitbotRequestWhileManagedSupplyIsActive();
        requestsKitbotWhenNoUsableSupplyExists();
        skipsClosedServiceHoleRequestWhenSupplyBecomesEnough();
        waitsForNewKitbotDeliveryAfterRequest();
        tracksDeliveryRequestState();
        waitsWhenOnlyStaleSupplyExistsAtRequest();
        ignoresStaleDroppedKitbotSupplyAtRequest();
        waitsForNewDroppedKitbotDeliveryAfterRequest();
        retriesPendingKitbotRefillAfterLongWait();
        doesNotRequestKitbotTwiceInOneReplenishCycle();
        clearsPendingKitbotWhenLocalSupplyRecovers();
    }

    private static void skipsKitbotRequestWhenLooseSupplyIsEnough() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(2, 0);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 2);

        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST),
            "enough loose ender chests return to source selection"
        );
        assertEquals(Phase.SELECTING_REPLENISH_SOURCE, workflow.afterServiceHoleClosed(), "enough loose supply skips request");
        assertFalse(workflow.pending(), "no request is pending when loose supply is enough");
        assertEquals(0, sent.size(), "no kitbot request is sent when loose supply is enough");
    }

    private static void skipsKitbotRequestWhenCombinedSupplyIsEnough() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(1, 3);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 4);

        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER),
            "loose plus shulker-contained ender chests return to source selection"
        );
        assertEquals(Phase.SELECTING_REPLENISH_SOURCE, workflow.afterServiceHoleClosed(), "combined supply skips request");
        assertFalse(workflow.pending(), "no request is pending when combined supply is enough");
        assertEquals(0, sent.size(), "no kitbot request is sent when combined supply is enough");
    }

    private static void skipsKitbotRequestWhileAnyUsableSupplyExists() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(1, 2);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 4);

        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST),
            "below-target but usable supply returns to source selection"
        );
        assertEquals(Phase.SELECTING_REPLENISH_SOURCE, workflow.afterServiceHoleClosed(), "usable supply skips kitbot request");
        assertFalse(workflow.pending(), "usable supply does not start a pending request");
        assertEquals(0, sent.size(), "usable supply does not send a kitbot request");
    }

    private static void suppressesKitbotRequestWhileManagedSupplyIsActive() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 1);

        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER, true),
            "managed placed shulker keeps missing phase local instead of closing for kitbot"
        );
        assertFalse(workflow.pending(), "managed placed shulker does not start pending refill");
        assertEquals(0, sent.size(), "managed placed shulker does not send kitbot request");

        assertEquals(
            Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER, false),
            "cleared managed shulker allows normal kitbot path"
        );

        boolean[] managedSupplyActive = {true};
        PlaneKitbotRefillWorkflow requestWorkflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "KitBot", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            PlaneKitbotDroppedSupplyTrackers.none(),
            null,
            () -> 1,
            () -> managedSupplyActive[0]
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            requestWorkflow.afterServiceHoleClosed(),
            "managed shulker suppresses the final kitbot request gate"
        );
        assertEquals(0, sent.size(), "managed shulker final gate does not send kitbot request");

        managedSupplyActive[0] = false;
        assertEquals(
            Phase.WAITING_FOR_KITBOT_REFILL,
            requestWorkflow.afterServiceHoleClosed(),
            "cleared managed shulker allows request at the final gate"
        );
        assertEquals(1, sent.size(), "cleared managed shulker sends kitbot request");
    }

    private static void requestsKitbotWhenNoUsableSupplyExists() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 1);

        assertEquals(
            Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST),
            "missing usable supply routes to kitbot refill"
        );
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "missing usable supply requests kitbot");
        assertTrue(workflow.pending(), "missing usable supply starts a pending request");
        assertEquals(1, sent.size(), "missing usable supply sends one kitbot request");
    }

    private static void skipsClosedServiceHoleRequestWhenSupplyBecomesEnough() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 2);

        assertEquals(
            Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER),
            "missing supply starts the close-service-hole refill path"
        );
        supply.enderChestsInShulkersCount = 2;
        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            workflow.afterServiceHoleClosed(),
            "newly sufficient supply is rechecked before sending the request"
        );
        assertFalse(workflow.pending(), "sufficient supply before request leaves no pending refill");
        assertEquals(0, sent.size(), "no kitbot request is sent after supply becomes sufficient");
    }

    private static void waitsForNewKitbotDeliveryAfterRequest() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, false);
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            (PlaneKitbotDroppedSupplyTracker) null,
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait with empty baseline");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "empty inventory keeps waiting");
        supply.looseEnderChestCount = 1;
        assertEquals(Phase.PLACING_ENDER_CHEST, workflow.waitForDelivery(), "new loose ender chest completes delivery");
        assertFalse(workflow.pending(), "confirmed delivery clears pending request");
    }

    private static void tracksDeliveryRequestState() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(2, false);
        PlaneKitbotDeliveryTracker delivery = new PlaneKitbotDeliveryTracker(supply, PlaneKitbotDroppedSupplyTrackers.none());
        PlaneKitbotMessenger messenger = new PlaneKitbotMessenger(
            () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
            sent::add,
            () -> true
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.requestOrMissingSupply(messenger), "delivery request starts pending wait");
        assertTrue(delivery.pending(), "delivery tracker records pending request");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.inventoryDeliveryPhase(), "baseline supply does not complete request");
        supply.looseEnderChestCount = 3;
        assertEquals(Phase.PLACING_ENDER_CHEST, delivery.inventoryDeliveryPhase(), "increased loose supply completes request");
        delivery.markSuppliesAvailable();
        assertFalse(delivery.pending(), "marking supplies clears pending request");

        supply.looseEnderChestCount = 0;
        assertEquals(Phase.MISSING_ENDER_CHEST_SHULKER, delivery.requestOrMissingSupply(messenger), "same cycle does not request twice");
        delivery.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.requestOrMissingSupply(messenger), "new cycle can request again");
        assertEquals(2, sent.size(), "delivery tracker sends one request per replenish cycle");
    }

    private static void waitsWhenOnlyStaleSupplyExistsAtRequest() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(2, 1);
        PlaneKitbotDeliveryTracker delivery = new PlaneKitbotDeliveryTracker(supply, PlaneKitbotDroppedSupplyTrackers.none());
        PlaneKitbotMessenger messenger = new PlaneKitbotMessenger(
            () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
            sent::add,
            () -> true
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.requestOrMissingSupply(messenger), "request captures stale supply baseline");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.inventoryDeliveryPhase(), "stale supply at baseline does not complete delivery");
        supply.looseEnderChestCount = 3;
        assertEquals(Phase.PLACING_ENDER_CHEST, delivery.inventoryDeliveryPhase(), "increased loose supply completes delivery");

        delivery.reset();
        supply.looseEnderChestCount = 0;
        supply.enderChestsInShulkersCount = 0;
        delivery.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.requestOrMissingSupply(messenger), "request captures missing shulker baseline");
        supply.enderChestsInShulkersCount = 1;
        assertEquals(Phase.PLACING_ENDER_CHEST_SHULKER, delivery.inventoryDeliveryPhase(), "new shulker completes delivery");

        delivery.markSuppliesAvailable();
        supply.looseEnderChestCount = 0;
        supply.enderChestsInShulkersCount = 1;
        delivery.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.requestOrMissingSupply(messenger), "request captures stale shulker baseline");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, delivery.inventoryDeliveryPhase(), "stale shulker at baseline does not complete delivery");
        supply.enderChestsInShulkersCount = 2;
        assertEquals(Phase.PLACING_ENDER_CHEST_SHULKER, delivery.inventoryDeliveryPhase(), "increased shulker-contained supply completes delivery");
    }

    private static void ignoresStaleDroppedKitbotSupplyAtRequest() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, false);
        PlaneKitbotRefillTestSupport.MutableDroppedSupplyTracker drops = new PlaneKitbotRefillTestSupport.MutableDroppedSupplyTracker("stale-drop");
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            drops,
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request captures stale dropped supply baseline");
        for (int i = 0; i < 5; i++) {
            assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "stale dropped supply wait tick " + i);
        }
        assertEquals(0, drops.pickupTicks, "stale dropped supply never starts pickup pathing");
        assertTrue(workflow.pending(), "stale dropped supply keeps request pending");
    }

    private static void waitsForNewDroppedKitbotDeliveryAfterRequest() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, false);
        PlaneKitbotRefillTestSupport.MutableDroppedSupplyTracker drops = new PlaneKitbotRefillTestSupport.MutableDroppedSupplyTracker(null);
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            drops,
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait with no dropped baseline");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "empty dropped delivery keeps waiting");
        drops.visibleDropId = "fresh-drop";
        assertEquals(Phase.PICKING_UP_KITBOT_REFILL, workflow.waitForDelivery(), "new dropped supply starts delivery pickup");
        assertEquals(1, drops.pickupTicks, "new dropped supply starts pickup pathing once");
        supply.looseEnderChestCount = 1;
        assertEquals(Phase.PLACING_ENDER_CHEST, workflow.pickUpDelivery(), "inventory pickup confirmation resumes replenish");
        assertFalse(workflow.pending(), "confirmed pickup delivery clears pending request");
    }

    private static void retriesPendingKitbotRefillAfterLongWait() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, false);
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            (PlaneKitbotDroppedSupplyTracker) null,
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts pending wait");
        assertEquals(1, sent.size(), "initial request sends once");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 39, "pending wait before first proactive tpy");
        assertEquals(1, sent.size(), "pending request does not send proactive tpy before delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "first proactive tpy tick");
        assertEquals(2, sent.size(), "pending request sends first proactive tpy after delay");
        assertEquals("/tpy whoahbuddy", sent.get(1), "initial proactive tpy sends once");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 99, "pending wait before proactive retry");
        assertEquals(2, sent.size(), "pending request throttles proactive tpy retry");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "proactive retry tick");
        assertEquals(3, sent.size(), "pending request retries proactive tpy at throttle interval");
        assertEquals("/tpy whoahbuddy", sent.get(2), "proactive retry uses configured accept command");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 1061, "pending wait before kit request retry");
        assertEquals(sent.get(0), sent.get(sent.size() - 1), "long pending wait retries the original refill command");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "pending wait before post-request proactive retry");
        assertEquals("/tpy whoahbuddy", sent.get(sent.size() - 1), "kit request retry re-arms proactive tpy delay");
    }

    private static void doesNotRequestKitbotTwiceInOneReplenishCycle() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, false);
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null,
            supply,
            (PlaneKitbotDroppedSupplyTracker) null,
            null
        );

        workflow.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "first missing supply requests kitbot");
        assertEquals(1, sent.size(), "first cycle sends one kitbot request");
        supply.looseEnderChestCount = 1;
        assertEquals(Phase.PLACING_ENDER_CHEST, workflow.waitForDelivery(), "delivered supply completes pending wait");

        supply.looseEnderChestCount = 0;
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            workflow.afterServiceHoleClosed(),
            "same replenish cycle does not ask kitbot again after a completed delivery"
        );
        assertEquals(1, sent.size(), "same replenish cycle does not send a second kitbot request");

        workflow.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "new replenish cycle may request kitbot again");
        assertEquals(2, sent.size(), "new replenish cycle sends a new kitbot request");
    }

    private static void clearsPendingKitbotWhenLocalSupplyRecovers() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillTestSupport.MutableSupplyProbe supply = new PlaneKitbotRefillTestSupport.MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = PlaneKitbotRefillTestSupport.refillWorkflow(sent, supply, 1);

        workflow.beginReplenishCycle();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "missing supply starts kitbot wait");
        assertTrue(workflow.pending(), "request starts pending delivery");
        assertEquals(1, sent.size(), "missing supply sends one kitbot request");

        supply.looseEnderChestCount = 1;
        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            workflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST_SHULKER, false),
            "locally recovered supply clears pending kitbot and resumes source selection"
        );
        assertFalse(workflow.pending(), "usable local supply clears pending kitbot delivery");

        supply.looseEnderChestCount = 0;
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            workflow.afterServiceHoleClosed(),
            "same cycle does not ask kitbot again after local recovery cleared pending"
        );
        assertEquals(1, sent.size(), "cleared pending kitbot does not create a premature second request");
    }
}
