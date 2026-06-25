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
        PlaneKitbotRefillTestSupport.waitForDeliveryTicks(workflow, 40, "wait for early proactive accept");
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
}
