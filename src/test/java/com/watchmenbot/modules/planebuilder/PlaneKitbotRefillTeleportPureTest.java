package com.watchmenbot.modules.planebuilder;

import java.util.ArrayList;
import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneKitbotRefillTeleportPureTest {
    private PlaneKitbotRefillTeleportPureTest() {
    }

    static void run() {
        queuesTeleportAcceptWorkflow();
        acceptsKitbotTeleportPromptDuringPendingRefill();
        diagnosesTeleportPromptFromUnexpectedKitbot();
        sendsQueuedTeleportAcceptOnceFromTick();
        stopsTeleportAcceptRetriesAfterServerConfirmation();
        acceptsAfterKitbotTpaNoticeWithoutServerPrompt();
        suppressesPastedLogRefillLoop();
        ignoresOwnPlaneBuilderChatMessages();
    }

    private static void queuesTeleportAcceptWorkflow() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1
        );
        PlaneKitbotMessenger messenger = new PlaneKitbotMessenger(() -> config, sent::add, () -> true);
        PlaneKitbotTeleportAcceptWorkflow accept = new PlaneKitbotTeleportAcceptWorkflow(messenger);

        assertFalse(accept.queueFromPrompt("otherbuddy wants to teleport to you."), "other player does not queue accept");
        PlaneKitbotTeleportAcceptWorkflow.AcceptResult sentResult = accept.handleMessage("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, sentResult.status(), "matching prompt queues accept");
        assertEquals(0, sent.size(), "chat prompt does not send inside receive event");
        for (int i = 0; i < 40; i++) {
            assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.tick().status(), "prompt accept delay tick " + i);
        }
        sentResult = accept.tick();
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.SENT, sentResult.status(), "delayed tick sends queued accept");
        assertEquals(1, sent.size(), "pending tick sends accept once");
        assertEquals("/tpy whoahbuddy", sent.get(0), "accept workflow sends configured command");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            accept.handleMessage("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
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
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.WAITING_FOR_PROMPT, sentResult.status(), "request-driven accept waits for prompt");
        for (int i = 0; i < 160; i++) {
            assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.tick().status(), "prompt-only accept wait tick " + i);
        }
        assertEquals(0, sent.size(), "request-driven accept never sends before a prompt");
    }

    private static void acceptsKitbotTeleportPromptDuringPendingRefill() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "the watchmen's echest's",
            1
        );
        PlaneKitbotRefillWorkflow prePendingWorkflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        PlaneKitbotTeleportAcceptWorkflow.AcceptResult accept = prePendingWorkflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "direct teleport prompt before pending is queued");
        assertEquals(0, sent.size(), "pre-pending prompt does not send tpy inside chat event");
        for (int i = 0; i < 40; i++) prePendingWorkflow.tickQueuedTeleportAccept();
        assertEquals(0, sent.size(), "pre-pending prompt waits before sending tpy");
        prePendingWorkflow.tickQueuedTeleportAccept();
        assertEquals("/tpy whoahbuddy", sent.get(0), "queued accept can flush before pending wait owns the tick");
        assertEquals(1, sent.size(), "queued accept flush sends once");
        prePendingWorkflow.reset();

        accept = prePendingWorkflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "reset allows another pre-pending prompt");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, prePendingWorkflow.afterServiceHoleClosed(), "pre-pending prompt still allows request");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(prePendingWorkflow, 40, "pre-pending prompt accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, prePendingWorkflow.waitForDelivery(), "pre-pending prompt sends after delay");
        assertEquals("/tpy whoahbuddy", sent.get(2), "pre-pending prompt sends deferred tpy after request starts");

        sent.clear();
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "refill request starts pending state");
        accept = workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "direct teleport prompt after refill request is queued");
        assertEquals("/w whoahbuddy the watchmen's echest's 1", sent.get(0), "refill request is still sent first after reset");
        assertEquals(1, sent.size(), "pending prompt does not send tpy command immediately");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "pending prompt accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "wait tick sends after prompt accept delay");
        assertEquals("/tpy whoahbuddy", sent.get(1), "wait tick sends prompt tpy command");

        workflow.reset();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "refill request starts pending state");
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.", "whoahbuddy"),
            "instruction prompt also matches"
        );
        accept = workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED, accept.status(), "instruction prompt after refill request is queued");
        assertEquals(3, sent.size(), "instruction prompt does not send tpy command immediately");
        accept = workflow.handleTeleportPrompt("[System] [CHAT] Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.");
        assertEquals(PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED, accept.status(), "second teleport prompt for same refill is ignored");
        assertEquals(3, sent.size(), "second prompt for same teleport is not duplicated immediately");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "instruction prompt accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "instruction prompt sends after delay");
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
                new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1),
                "whoahbuddy wants to teleport to you."
            ),
            "hardcoded accept command uses tpy with nickname"
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
            PlaneKitbotPromptParser.teleportPromptMatches("whoahbuddy is requesting to teleport to you.", "whoahbuddy"),
            "requesting teleport wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("whoahbuddy sent you a teleport request.", "whoahbuddy"),
            "teleport request sent-to-you wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("Teleport request from whoahbuddy", "whoahbuddy"),
            "teleport request from wording matches configured kitbot"
        );
        assertTrue(
            PlaneKitbotPromptParser.teleportPromptMatches("TPA request from: whoahbuddy", "whoahbuddy"),
            "colon tpa request from wording matches configured kitbot"
        );
        assertEquals(
            "whoahbuddy",
            PlaneKitbotPromptParser.teleportPromptRequester("whoahbuddy is requesting to teleport to you."),
            "requesting wording exposes requester"
        );
        assertEquals(
            "whoahbuddy",
            PlaneKitbotPromptParser.teleportPromptRequester("TPA request from: whoahbuddy"),
            "colon request-from wording exposes requester"
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
            1
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
                new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1),
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
            1
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "prompt after refill request queues while pending"
        );
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
            "duplicate prompt does not requeue"
        );
        assertEquals(1, sent.size(), "queued accept is not sent inside chat event");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "first prompt accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "first wait tick sends prompt accept");
        assertEquals("/tpy whoahbuddy", sent.get(1), "first accept sent from wait tick");
        for (int i = 0; i < 20; i++) {
            assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "post-accept wait tick " + i);
        }
        assertEquals(2, sent.size(), "prompt accept is sent only once during short wait");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "post-accept retry delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "prompt accept keeps waiting without retry");
        assertEquals(2, sent.size(), "prompt accept is not retried while delivery is pending");

        sent.clear();
        workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "second request starts wait");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 160, "wait without teleport prompt");
        assertEquals(1, sent.size(), "prompt-only accept does not send before prompt arrives");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy is requesting to teleport to you.").status(),
            "requesting prompt after waiting queues tpy"
        );
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("Type /tpy whoahbuddy to accept or /tpn whoahbuddy to deny.").status(),
            "duplicate prompt after waiting does not queue twice"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "prompt after waiting accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "prompt after waiting flushes after delay");
        assertEquals(2, sent.size(), "prompt sends one tpy");
        assertEquals("/tpy whoahbuddy", sent.get(1), "prompt accept uses configured accept command");
    }

    private static void stopsTeleportAcceptRetriesAfterServerConfirmation() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "teleport prompt queues accept"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "accept confirmation delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "first accept attempt is sent");
        assertEquals(2, sent.size(), "request and one tpy were sent");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.CONFIRMED,
            workflow.handleTeleportPrompt("Request from whoahbuddy accepted! [Cancel]").status(),
            "server acceptance confirms tpy"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 200, "accepted teleport waits for delivery");
        assertEquals(2, sent.size(), "confirmed accept stops tpy retries");

        workflow.reset();
        sent.clear();
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "second request starts wait");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "second prompt queues accept"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "gone request delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "second accept attempt is sent");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.REQUEST_GONE,
            workflow.handleTeleportPrompt("There is no request to accept from whoahbuddy!").status(),
            "missing request also stops retry spam"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 200, "gone request waits without retries");
        assertEquals(2, sent.size(), "gone request stops tpy retries");
    }

    private static void acceptsAfterKitbotTpaNoticeWithoutServerPrompt() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait");
        assertEquals(1, sent.size(), "initial kit request is sent");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Accepted 'echest' x1. Preparing to gather.").status(),
            "preparing message is active but does not queue tpy"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 70, "preparing fallback delay");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Gathering 'echest' x1 now.").status(),
            "gathering message is active but does not queue tpy"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 30, "legacy fallback waits after active request");
        assertEquals(1, sent.size(), "legacy fallback does not send before delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "legacy fallback sends one accept probe");
        assertEquals(2, sent.size(), "legacy fallback sends one tpy probe");
        assertEquals("/tpy whoahbuddy", sent.get(1), "legacy fallback uses configured accept command");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 1_100, "active request waits without duplicate whisper");
        assertEquals(2, sent.size(), "active kitbot request suppresses duplicate refill and tpy probes");

        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.IGNORED,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Accepted 'echest' x1. I already have it in inventory; sending TPA now.").status(),
            "kitbot tpa notice is ignored after legacy fallback probe"
        );
        assertEquals(2, sent.size(), "tpa notice does not send inside chat event");
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "kitbot tpa accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "ignored tpa notice keeps waiting");
        assertEquals(2, sent.size(), "ignored tpa notice does not send accept command without server prompt");

        sent.clear();
        workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "fresh request starts wait");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.WAITING_FOR_PROMPT,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Accepted 'echest' x1. I already have it in inventory; sending TPA now.").status(),
            "kitbot tpa notice waits for server prompt before legacy fallback"
        );
        assertEquals(1, sent.size(), "fresh tpa notice does not send inside chat event");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.QUEUED,
            workflow.handleTeleportPrompt("whoahbuddy wants to teleport to you.").status(),
            "server prompt after kitbot tpa notice queues accept"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "server prompt accept delay");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.waitForDelivery(), "server prompt sends accept after delay");
        assertEquals(2, sent.size(), "server prompt sends one accept command");
        assertEquals("/tpy whoahbuddy", sent.get(1), "server prompt accept uses configured command");

        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.CONFIRMED,
            workflow.handleTeleportPrompt("Request from whoahbuddy accepted! [Cancel]").status(),
            "server acceptance confirms kitbot tpa accept"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 200, "confirmed kitbot tpa waits for delivery");
        assertEquals(2, sent.size(), "confirmed kitbot tpa accept does not retry");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.DELIVERY_FINISHED,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Finished 'echest' delivery. Ready for the next request.").status(),
            "finished delivery clears accept queue"
        );
    }

    private static void suppressesPastedLogRefillLoop() {
        List<String> sent = new ArrayList<>();
        PlaneKitbotRefillConfig config = new PlaneKitbotRefillConfig(
            true,
            "whoahbuddy",
            "echest",
            1
        );
        PlaneKitbotRefillWorkflow workflow = new PlaneKitbotRefillWorkflow(
            new PlaneKitbotMessenger(() -> config, sent::add, () -> true),
            null
        );

        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "request starts wait");
        assertEquals("/w whoahbuddy echest 1", sent.get(0), "initial refill request is sent");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.WAITING_FOR_PROMPT,
            workflow.handleTeleportPrompt("whoahbuddy whispers: Accepted 'echest' x1. I already have it in inventory; sending TPA now.").status(),
            "kitbot tpa notice waits for server prompt"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "kitbot tpa notice without server prompt");
        assertEquals(1, sent.size(), "kitbot tpa notice alone does not send tpy");
        assertEquals(
            PlaneKitbotTeleportAcceptWorkflow.AcceptStatus.REQUEST_GONE,
            workflow.handleTeleportPrompt("There is no request to accept from whoahbuddy!").status(),
            "stale no-request response stops accept attempts"
        );
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 80, "no request response wait");
        assertEquals(1, sent.size(), "no-request response does not retry tpy or refill request");
        workflow.handleTeleportPrompt("whoahbuddy whispers: Delivery timed out waiting for /tpy. Returning to stash position.");
        assertFalse(workflow.pending(), "kitbot timeout clears pending delivery");
        assertEquals(Phase.WAITING_FOR_KITBOT_REFILL, workflow.afterServiceHoleClosed(), "kitbot timeout enters retry cooldown");
        assertEquals(1, sent.size(), "kitbot timeout does not immediately send duplicate refill request");
    }

    private static void ignoresOwnPlaneBuilderChatMessages() {
        assertTrue(
            PlaneBuilder.shouldIgnoreReceivedMessage("[Meteor] [Plane Builder] Stopped kitbot teleport accept because delivery finished: /tpy whoahbuddy"),
            "own Meteor plane builder status messages are ignored"
        );
        assertTrue(
            PlaneBuilder.shouldIgnoreReceivedMessage("[Plane Builder] Kitbot teleport accept confirmed by server: /tpy whoahbuddy"),
            "own plane builder status messages without Meteor prefix are ignored"
        );
        assertFalse(
            PlaneBuilder.shouldIgnoreReceivedMessage("Request from whoahbuddy accepted! [Cancel]"),
            "server accept messages are still processed"
        );
        assertEquals(
            PlaneKitbotRefillDecisions.KitbotDeliveryMessage.IGNORED,
            PlaneKitbotRefillDecisions.kitbotDeliveryMessage(
                new PlaneKitbotRefillConfig(true, "whoahbuddy", "echest", 1),
                "[Meteor] [Plane Builder] Stopped kitbot teleport accept because delivery finished: /tpy whoahbuddy"
            ),
            "own status messages are not classified as kitbot delivery whispers"
        );
    }
}
