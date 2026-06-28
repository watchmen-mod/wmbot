package com.watchmenbot.modules.planebuilder;

import java.util.ArrayList;
import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneKitbotRefillPureTest {
    private PlaneKitbotRefillPureTest() {
    }

    static void run() {
        plansKitbotRefillTransitions();
        sendsKitbotRefillOncePerWait();
        PlaneKitbotRefillTeleportPureTest.run();
        plansDroppedKitbotRefillPickupTransitions();
        PlaneKitbotRefillSupplyPureTest.run();
        allowsBowDefenseDuringKitbotRefill();
    }

    private static void plansKitbotRefillTransitions() {
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            PlaneKitbotRefillDecisions.missingSupplyPhase(false, false),
            "disabled refill keeps existing missing shulker phase"
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
            PlaneKitbotRefillDecisions.missingSupplyPhase(true, false),
            "enabled refill closes the service hole before requesting kitbot"
        );
        assertEquals(
            Phase.SELECTING_REPLENISH_SOURCE,
            PlaneKitbotRefillDecisions.missingSupplyPhase(true, true),
            "enough supply should return to source selection"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            PlaneKitbotRefillDecisions.missingSupplyPhase(true, false, true),
            "managed in-flight supply suppresses kitbot refill"
        );
        PlaneKitbotRefillWorkflow enabledWorkflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(true, "KitBot", "echest", 1),
                message -> {
                },
                () -> true
            ),
            null
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
            enabledWorkflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST),
            "enabled refill treats missing loose ender chest as kitbot refill supply exhaustion"
        );
        PlaneKitbotRefillWorkflow disabledWorkflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(false, "KitBot", "echest", 1),
                message -> {
                },
                () -> true
            ),
            null
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            disabledWorkflow.missingSupplyPhase(Phase.MISSING_ENDER_CHEST),
            "disabled refill keeps missing loose ender chest visible"
        );
        assertEquals(
            Phase.WAITING_FOR_KITBOT_REFILL,
            PlaneKitbotRefillDecisions.waitingPhase(false, false),
            "waiting with supplies still missing remains waiting"
        );
        assertEquals(
            Phase.PICKING_UP_KITBOT_REFILL,
            PlaneKitbotRefillDecisions.waitingPhase(false, false, true),
            "visible dropped supply starts kitbot pickup"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneKitbotRefillDecisions.waitingPhase(true, false),
            "loose ender chest delivery resumes ender chest placement"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneKitbotRefillDecisions.waitingPhase(false, true),
            "shulker delivery resumes shulker extraction"
        );
    }

    private static void plansDroppedKitbotRefillPickupTransitions() {
        assertEquals(
            Phase.PICKING_UP_KITBOT_REFILL,
            PlaneKitbotRefillDecisions.pickupPhase(false, false, true),
            "pickup continues while matching dropped supply remains available"
        );
        assertEquals(
            Phase.WAITING_FOR_KITBOT_REFILL,
            PlaneKitbotRefillDecisions.pickupPhase(false, false, false),
            "pickup falls back to waiting when target disappears before inventory changes"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneKitbotRefillDecisions.pickupPhase(true, false, true),
            "loose ender chest pickup resumes ender chest placement"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneKitbotRefillDecisions.pickupPhase(false, true, true),
            "ender chest shulker pickup resumes shulker extraction"
        );
    }

    private static void sendsKitbotRefillOncePerWait() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            " KitBot ",
            " echest ",
            3
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "first valid request starts waiting");
        assertTrue(workflow.pending(), "request becomes pending");
        assertEquals(1, sent.size(), "request sends only the whisper immediately");
        assertEquals("/w KitBot echest 3", sent.get(0), "request command is trimmed and formatted");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 160, "prompt-only accept wait");
        assertEquals(1, sent.size(), "waiting without a teleport prompt does not send tpy");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "pending request keeps waiting");
        assertEquals(1, sent.size(), "pending request does not repeat whisper or tpy");

        workflow.reset();
        assertFalse(workflow.pending(), "reset clears pending request");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "later wait can send again after reset");
        assertEquals(2, sent.size(), "request may send again on a later wait");

        PlaneKitbotRefillWorkflow disabled = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(false, "KitBot", "echest", 1),
                sent::add,
                () -> true
            ),
            null
        );
        assertEquals(Phase.MISSING_ENDER_CHEST_SHULKER, disabled.afterServiceHoleClosed(), "disabled refill does not send");
        assertEquals(
            null,
            PlaneKitbotRefillDecisions.requestCommand(new PlaneKitbotRefillConfig(true, "", "echest", 1)),
            "blank nickname does not produce a whisper command"
        );
    }

    private static void allowsBowDefenseDuringKitbotRefill() {
        assertFalse(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.WAITING_FOR_KITBOT_REFILL, true),
            "kitbot wait is treated as bow-defense eligible"
        );
        assertFalse(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.PICKING_UP_KITBOT_REFILL, true),
            "kitbot pickup movement is treated as bow-defense eligible"
        );
        assertTrue(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.PLACING_ENDER_CHEST, true),
            "ender chest placement keeps bow defense gated off"
        );
        assertTrue(
            PlaneBowDefenseDecisions.canRun(true, false, true, true, true, true),
            "bow defense can run when kitbot wait passes false replenish activity"
        );
    }

}
