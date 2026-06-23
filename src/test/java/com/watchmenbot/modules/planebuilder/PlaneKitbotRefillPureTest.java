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
        queuesTeleportAcceptWorkflow();
        acceptsKitbotTeleportPromptDuringPendingRefill();
        diagnosesTeleportPromptFromUnexpectedKitbot();
        sendsQueuedTeleportAcceptOnceFromTick();
        plansDroppedKitbotRefillPickupTransitions();
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
                () -> new PlaneKitbotRefillConfig(true, "KitBot", "echest", 1, "/w", "/tpy"),
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
                () -> new PlaneKitbotRefillConfig(false, "KitBot", "echest", 1, "/w", "/tpy"),
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
            3,
            " /w ",
            " /tpy "
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "first valid request starts waiting");
        assertTrue(workflow.pending(), "request becomes pending");
        assertEquals(1, sent.size(), "request sends only the whisper immediately");
        assertEquals("/w KitBot echest 3", sent.get(0), "request command is trimmed and formatted");
        waitForDeliveryTicks(workflow, 39, "delayed proactive accept wait");
        assertEquals(1, sent.size(), "proactive accept waits before sending");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "delayed proactive accept fires");
        assertEquals(2, sent.size(), "delayed proactive accept sends once");
        assertEquals("/tpy KitBot", sent.get(1), "proactive accept command is trimmed and formatted");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "pending request keeps waiting");
        assertEquals(2, sent.size(), "pending request does not repeat whisper or tpy");

        workflow.reset();
        assertFalse(workflow.pending(), "reset clears pending request");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "later wait can send again after reset");
        assertEquals(3, sent.size(), "request may send again on a later wait");

        PlaneKitbotRefillWorkflow disabled = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(
                () -> new PlaneKitbotRefillConfig(false, "KitBot", "echest", 1, "/w", "/tpy"),
                sent::add,
                () -> true
            ),
            null
        );
        assertEquals(Phase.MISSING_ENDER_CHEST_SHULKER, disabled.afterServiceHoleClosed(), "disabled refill does not send");
        assertEquals(
            null,
            PlaneKitbotRefillDecisions.requestCommand(new PlaneKitbotRefillConfig(true, "", "echest", 1, "/w", "/tpy")),
            "blank nickname does not produce a whisper command"
        );
    }

    private static void queuesTeleportAcceptWorkflow() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1,
            "/w",
            "/tpy"
        );
        PlaneKitbotMessenger messenger = new PlaneKitbotMessenger(() -> config, sent::add, () -> true);
        PlaneKitbotTeleportAcceptWorkflow accept = new PlaneKitbotTeleportAcceptWorkflow(messenger);

        assertFalse(accept.queueFromPrompt("otherbuddy wants to teleport to you."), "other player does not queue accept");
        PlaneKitbotTeleportAcceptWorkflow.AcceptResult sentResult = accept.handlePrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, sentResult.status(), "matching prompt queues accept");
        assertEquals(0, sent.size(), "chat prompt does not send inside receive event");
        sentResult = accept.tick();
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.SENT, sentResult.status(), "next tick sends queued accept");
        assertEquals(1, sent.size(), "pending tick sends accept once");
        assertEquals("/tpy whoahbuddy", sent.get(0), "accept workflow sends configured command");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            accept.handlePrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
            "duplicate prompt does not queue twice"
        );
        assertEquals(1, sent.size(), "successful accept is not repeated");
        assertFalse(accept.queueFromPrompt("whoahbuddy wants to teleport to you."), "sent accept suppresses later duplicate prompts");
        accept.reset();
        assertTrue(accept.queueFromPrompt("whoahbuddy wants to teleport to you."), "reset allows next refill prompt");
        assertEquals(1, sent.size(), "reset prompt is queued but not sent immediately");

        accept.reset();
        sent.clear();
        sentResult = accept.queueConfiguredAcceptForRequest();
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.ARMED, sentResult.status(), "request-driven accept arms delayed retry");
        for (int i = 0; i < 39; i++) {
            assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.tick().status(), "armed accept delay tick " + i);
        }
        sentResult = accept.tick();
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.SENT, sentResult.status(), "armed accept sends after delay");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptSource.PROACTIVE, sentResult.source(), "armed accept is marked proactive");
        assertEquals("/tpy whoahbuddy", sent.get(0), "armed accept uses configured command");
        for (int i = 0; i < 99; i++) {
            assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.tick().status(), "armed accept retry delay tick " + i);
        }
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.SENT, accept.tick().status(), "armed accept retries while active");
        assertEquals(2, sent.size(), "armed accept retries at throttle interval");
    }

    private static void acceptsKitbotTeleportPromptDuringPendingRefill() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "the watchmen's echest's",
            1,
            "/w",
            "/tpy"
        );
        PlaneKitbotRefillWorkflow prePendingWorkflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        PlaneKitbotTeleportAcceptWorkflow.AcceptResult accept = prePendingWorkflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "direct teleport prompt before pending is queued");
        assertEquals(0, sent.size(), "pre-pending prompt does not send tpy inside chat event");
        prePendingWorkflow.tickQueuedTeleportAccept();
        assertEquals("/tpy whoahbuddy", sent.get(0), "queued accept can flush before pending wait owns the tick");
        assertEquals(1, sent.size(), "queued accept flush sends once");
        prePendingWorkflow.reset();

        accept = prePendingWorkflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "reset allows another pre-pending prompt");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, prePendingWorkflow.afterServiceHoleClosed(), "pre-pending prompt still allows request");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, prePendingWorkflow.waitForDelivery(), "pre-pending prompt sends on wait tick");
        assertEquals("/tpy whoahbuddy", sent.get(2), "pre-pending prompt sends deferred tpy after request starts");

        sent.clear();
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "refill request starts pending state");
        accept = workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "direct teleport prompt after proactive arm is queued");
        assertEquals("/w whoahbuddy the watchmen's echest's 1", sent.get(0), "refill request is still sent first after reset");
        assertEquals(1, sent.size(), "pending prompt does not send tpy command immediately");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "wait tick continues waiting after prompt accept");
        assertEquals("/tpy whoahbuddy", sent.get(1), "wait tick sends prompt tpy command");

        workflow.reset();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "refill request starts pending state");
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.", "whoahbuddy"),
            "instruction prompt also matches"
        );
        accept = workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "instruction prompt after proactive arm is queued");
        assertEquals(3, sent.size(), "instruction prompt does not send tpy command immediately");
        accept = workflow.handleTeleportPrompt("[System] [CHAT] Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.status(), "second teleport prompt for same refill is ignored");
        assertEquals(3, sent.size(), "second prompt for same teleport is not duplicated immediately");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "instruction prompt wait tick continues waiting");
        assertEquals("/tpy whoahbuddy", sent.get(3), "instruction prompt sends tpy command on wait tick");
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("[System] [CHAT] whoahbuddy wants to teleport to you.", "whoahbuddy"),
            "log-prefixed direct prompt matches"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("[13:24:00] [Render thread/INFO]: [System] [CHAT] Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.", "whoahbuddy"),
            "full logged instruction prompt matches"
        );
        assertEquals(
            "whoahbuddy",
            PlaneKitbotPromptParser.teleportPromptRequester("[17:56:45] [Render thread/INFO]: [System] [CHAT] Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny."),
            "logged instruction prompt exposes requester for diagnostics"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpaccept whoahbuddy to accept the teleport request.", "whoahbuddy"),
            "tpaccept instruction prompt also matches"
        );
        assertEquals(
            "/tpy whoahbuddy",
            PlaneKitbotRefillDecisions.teleportAcceptCommand(
                new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", ""),
                "whoahbuddy wants to teleport to you."
            ),
            "blank accept command falls back to tpy with nickname"
        );
        assertFalse(
            PlaneKitbotPromptParser.teleportPromptMatches("otherbuddy wants to teleport to you.", "whoahbuddy"),
            "other players are ignored"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("whoahbuddy sent you a /tpa request", "whoahbuddy"),
            "tpa request wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("whoahbuddy has requested to teleport to you", "whoahbuddy"),
            "requested teleport wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Teleport request from whoahbuddy", "whoahbuddy"),
            "teleport request from wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpy whoahbuddy to accept the /tpa request", "whoahbuddy"),
            "tpa instruction wording matches configured kitbot"
        );
        assertFalse(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpy otherbuddy to accept the /tpa request", "whoahbuddy"),
            "tpa wording for another player is ignored"
        );
        assertFalse(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpy to accept the /tpa request", "whoahbuddy"),
            "tpa wording without configured nickname is ignored"
        );
    }

    private static void diagnosesTeleportPromptFromUnexpectedKitbot() {
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "WatchmenBOT",
            "the watchmen's echest's",
            1,
            "/w",
            "/tpy"
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, message -> {
            }, () -> true),
            null
        );

        String prompt = "[17:56:45] [Render thread/INFO]: [System] [CHAT] Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.";
        assertFalse(
            workflow.handleTeleportPrompt(prompt).accepted(),
            "prompt from non-configured kitbot must not queue accept"
        );
        PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignored = workflow.ignoredTeleportPrompt(prompt);
        assertEquals("whoahbuddy", ignored.requester(), "diagnostic reports prompt requester");
        assertEquals("WatchmenBOT", ignored.configuredNickname(), "diagnostic reports configured nickname");
        assertEquals(
            null,
            PlaneKitbotRefillDecisions.ignoredTeleportPrompt(
                new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1, "/w", "/tpy"),
                prompt
            ),
            "matching configured nickname does not produce ignored diagnostic"
        );
    }

    private static void sendsQueuedTeleportAcceptOnceFromTick() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1,
            "/w",
            "/tpy"
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "prompt after proactive arm queues while pending"
        );
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
            "duplicate prompt does not requeue"
        );
        assertEquals(1, sent.size(), "queued accept is not sent inside chat event");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "first wait tick sends prompt accept");
        assertEquals("/tpy whoahbuddy", sent.get(1), "first accept sent from wait tick");
        for (int i = 0; i < 20; i++) {
            assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "post-accept wait tick " + i);
        }
        assertEquals(2, sent.size(), "prompt accept is sent only once during short wait");

        sent.clear();
        workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "second request starts wait");
        waitForDeliveryTicks(workflow, 40, "wait for early proactive accept");
        assertEquals("/tpy whoahbuddy", sent.get(1), "early proactive accept sends before prompt arrives");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "prompt after earlier proactive accept queues another immediate tpy"
        );
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
            "duplicate prompt after earlier proactive accept does not queue twice"
        );
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "prompt after proactive accept flushes on next wait tick");
        assertEquals(3, sent.size(), "prompt after proactive accept sends one extra tpy");
        assertEquals("/tpy whoahbuddy", sent.get(2), "prompt retry uses configured accept command");
    }

    private static void skipsKitbotRequestWhenLooseSupplyIsEnough() {
        List<String> sent = new ArrayList<>();
        MutableSupplyProbe supply = new MutableSupplyProbe(2, 0);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 2);

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
        MutableSupplyProbe supply = new MutableSupplyProbe(1, 3);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 4);

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
        MutableSupplyProbe supply = new MutableSupplyProbe(1, 2);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 4);

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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 1);

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
    }

    private static void requestsKitbotWhenNoUsableSupplyExists() {
        List<String> sent = new ArrayList<>();
        MutableSupplyProbe supply = new MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 1);

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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 2);

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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, false);
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
        MutableSupplyProbe supply = new MutableSupplyProbe(2, false);
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
        MutableSupplyProbe supply = new MutableSupplyProbe(2, 1);
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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, false);
        MutableDroppedSupplyTracker drops = new MutableDroppedSupplyTracker("stale-drop");
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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, false);
        MutableDroppedSupplyTracker drops = new MutableDroppedSupplyTracker(null);
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
        MutableSupplyProbe supply = new MutableSupplyProbe(0, false);
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
        waitForDeliveryTicks(workflow, 39, "pending wait before first proactive tpy");
        assertEquals(1, sent.size(), "pending request does not send proactive tpy before delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "first proactive tpy tick");
        assertEquals(2, sent.size(), "pending request sends first proactive tpy after delay");
        assertEquals("/tpy whoahbuddy", sent.get(1), "initial proactive tpy sends once");
        waitForDeliveryTicks(workflow, 99, "pending wait before proactive retry");
        assertEquals(2, sent.size(), "pending request throttles proactive tpy retry");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "proactive retry tick");
        assertEquals(3, sent.size(), "pending request retries proactive tpy at throttle interval");
        assertEquals("/tpy whoahbuddy", sent.get(2), "proactive retry uses configured accept command");
        waitForDeliveryTicks(workflow, 1061, "pending wait before kit request retry");
        assertEquals(sent.get(0), sent.get(sent.size() - 1), "long pending wait retries the original refill command");
        waitForDeliveryTicks(workflow, 40, "pending wait before post-request proactive retry");
        assertEquals("/tpy whoahbuddy", sent.get(sent.size() - 1), "kit request retry re-arms proactive tpy delay");
    }

    private static void doesNotRequestKitbotTwiceInOneReplenishCycle() {
        List<String> sent = new ArrayList<>();
        MutableSupplyProbe supply = new MutableSupplyProbe(0, false);
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

    private static void clearsPendingKitbotWhenLocalSupplyRecovers() {
        List<String> sent = new ArrayList<>();
        MutableSupplyProbe supply = new MutableSupplyProbe(0, 0);
        PlaneKitbotRefillWorkflow workflow = refillWorkflow(sent, supply, 1);

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

    private static PlaneKitbotRefillWorkflow refillWorkflow(List<String> sent, MutableSupplyProbe supply, int requiredEnderChests) {
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

    private static void waitForDeliveryTicks(PlaneKitbotRefillWorkflow workflow, int ticks, String message) {
        for (int i = 0; i < ticks; i++) {
            assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), message + " " + i);
        }
    }

    private static final class MutableSupplyProbe implements PlaneKitbotSupplyProbe {
        private int looseEnderChestCount;
        private int enderChestsInShulkersCount;

        private MutableSupplyProbe(int looseEnderChestCount, boolean hasEnderChestShulker) {
            this(looseEnderChestCount, hasEnderChestShulker ? 1 : 0);
        }

        private MutableSupplyProbe(int looseEnderChestCount, int enderChestsInShulkersCount) {
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

    private static final class MutableDroppedSupplyTracker implements PlaneKitbotDroppedSupplyTracker {
        private String visibleDropId;
        private String baselineDropId;
        private int pickupTicks;

        private MutableDroppedSupplyTracker(String visibleDropId) {
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
