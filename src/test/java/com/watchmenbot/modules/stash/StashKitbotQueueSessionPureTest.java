package com.watchmenbot.modules.stash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertFalse;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertNull;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.target;

final class StashKitbotQueueSessionPureTest {
    private StashKitbotQueueSessionPureTest() {
    }

    static void run() {
        managesKitbotSessionState();
        managesKitbotRequestQueue();
        plansKitbotQueueCapacity();
        persistsKitbotQueueState();
        sanitizesKitbotQueueState();
        restoresPersistedQueueIntoSession();
        tracksGatherStartNotificationState();
        reportsNavigatorPathCapability();
    }

    private static void managesKitbotSessionState() {
        StashKitbotSession session = new StashKitbotSession();
        KitRequest request = new KitRequest(
            "Alice",
            "Blue EChest",
            "blue echest",
            2,
            List.of(new KitSource("a", new net.minecraft.util.math.BlockPos(10, 64, 10), List.of(0), 3)),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );

        session.startRequest(request);

        assertTrue(session.hasActiveRequest(), "session has active request");
        assertEquals(request, session.activeRequest(), "session active request");
        assertEquals(KitbotPhase.IDLE, session.phase(), "session starts idle");
        assertEquals("Blue EChest 0/2 idle", session.infoString(), "session info string");

        session.currentTarget(target("minecraft:overworld:10,64,10"));
        session.beginGatherPhase(KitbotPhase.PATHING, 20);
        assertFalse(session.isDeliveryPhase(), "pathing is not delivery");
        assertEquals(KitbotPhase.PATHING, session.phase(), "session gather phase");

        session.phase(KitbotPhase.TPA_REQUEST);
        assertTrue(session.isDeliveryPhase(), "tpa is delivery");

        session.clearRequest();
        assertFalse(session.hasActiveRequest(), "session cleared request");
        assertNull(session.activeRequest(), "session active request cleared");
        assertNull(session.currentTarget(), "session current target cleared");
        assertEquals(KitbotPhase.IDLE, session.phase(), "session clear returns idle");
    }

    private static void managesKitbotRequestQueue() {
        StashKitbotSession session = new StashKitbotSession();
        QueuedKitRequest first = queued("Alice", "blue", 2);
        QueuedKitRequest second = queued("Bob", "red", 1);

        session.enqueue(first);
        session.enqueue(second);

        assertEquals(2, session.queuedCount(), "queued count after enqueue");
        assertEquals(RequesterRequestStatus.QUEUED, session.requestStatus("alice"), "queued requester has queued request status");
        assertEquals(RequesterRequestStatus.QUEUED, session.requestStatus("bob"), "different queued requester has queued request status");
        assertEquals(RequesterRequestStatus.NONE, session.requestStatus("dana"), "missing queued requester has no request status");
        assertEquals("2 queued", session.infoString(), "queued-only session info string");
        assertEquals(first, session.pollNextQueuedRequest(), "queue polls first request first");
        assertEquals(second, session.pollNextQueuedRequest(), "queue polls second request second");
        assertNull(session.pollNextQueuedRequest(), "empty queue polls null");

        session.enqueue(first);
        session.enqueue(second);
        session.startRequest(new KitRequest(
            " Carl ",
            "Green Kit",
            "green",
            3,
            List.of(new KitSource("a", new net.minecraft.util.math.BlockPos(10, 64, 10), List.of(0), 3)),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        ));
        assertEquals(RequesterRequestStatus.ACTIVE, session.requestStatus("carl"), "active requester has active request status");
        assertEquals(RequesterRequestStatus.ACTIVE, session.requestStatus(StashKitbotAccessPlanner.normalize(" CARL ")), "active requester match ignores case and spacing");
        assertEquals(RequesterRequestStatus.QUEUED, session.requestStatus("alice"), "queued requester remains queued while another request is active");
        assertEquals(RequesterRequestStatus.NONE, session.requestStatus("eve"), "different requester may still request");
        assertEquals("Green Kit 0/3 idle +2 queued", session.infoString(), "active info string includes queued count");

        session.clearRequest();
        assertEquals(2, session.queuedCount(), "clearing active request preserves queue");
        session.clearQueuedRequests();
        assertEquals(0, session.queuedCount(), "queue clear removes queued requests");
    }

    private static void plansKitbotQueueCapacity() {
        assertFalse(StashKitbotQueuePlanner.shouldQueue(false, 0), "idle empty queue starts immediately");
        assertTrue(StashKitbotQueuePlanner.shouldQueue(true, 0), "active request sends new request to queue path");
        assertTrue(StashKitbotQueuePlanner.shouldQueue(false, 1), "queued backlog preserves FIFO before immediate start");

        assertFalse(StashKitbotQueuePlanner.canEnqueue(0, 0), "disabled queue rejects first queued request");
        assertTrue(StashKitbotQueuePlanner.canEnqueue(2, 0), "queue with capacity accepts first request");
        assertTrue(StashKitbotQueuePlanner.canEnqueue(2, 1), "queue with remaining capacity accepts next request");
        assertFalse(StashKitbotQueuePlanner.canEnqueue(2, 2), "full queue rejects at cap");
        assertEquals(3, StashKitbotQueuePlanner.queuePosition(2), "queue position is one-based");
    }

    private static void persistsKitbotQueueState() {
        QueuedKitRequest first = queued("Alice", "blue", 2);
        QueuedKitRequest second = new QueuedKitRequest(
            new KitbotRequesterAccess("Bob", "bob", KitbotTier.TIER_2, 2400),
            new KitCommand("red kit", 1, true)
        );
        StashKitbotQueueState.DeliveryResume resume = new StashKitbotQueueState.DeliveryResume(
            "Carl",
            "green",
            3,
            2,
            "minecraft:overworld",
            "2026-06-15T12:00:00Z"
        );

        try {
            Path dir = Files.createTempDirectory("wmbot-kitbot-queue");
            Path file = dir.resolve("stash_kitbot_queue.json");
            StashKitbotQueueState.write(file, new StashKitbotQueueState.State(List.of(first, second), resume));

            StashKitbotQueueState.State state = StashKitbotQueueState.read(file);
            assertEquals(List.of(first, second), state.queuedRequests(), "persisted queue preserves FIFO request order");
            assertEquals(resume, state.deliveryResume(), "persisted delivery resume round trips");
        }
        catch (IOException exception) {
            throw new AssertionError("queue state persistence failed", exception);
        }
    }

    private static void sanitizesKitbotQueueState() {
        StashKitbotQueueState.State malformed = StashKitbotQueueState.fromJson("{bad json");
        assertEquals(List.of(), malformed.queuedRequests(), "malformed queue json falls back to empty queue");
        assertNull(malformed.deliveryResume(), "malformed queue json drops resume");

        StashKitbotQueueState.State state = StashKitbotQueueState.fromJson("""
            {
              "schemaVersion": 1,
              "queuedRequests": [
                {
                  "access": {
                    "requester": " Alice ",
                    "normalizedRequester": "",
                    "tier": "TIER_1",
                    "cooldownTicks": -20
                  },
                  "command": {
                    "name": " blue ",
                    "count": 2,
                    "quotedSearch": true
                  }
                },
                {
                  "access": {
                    "requester": "",
                    "normalizedRequester": "",
                    "tier": "TIER_1",
                    "cooldownTicks": 1200
                  },
                  "command": {
                    "name": "red",
                    "count": 1,
                    "quotedSearch": false
                  }
                },
                {
                  "access": {
                    "requester": "Bob",
                    "normalizedRequester": "bob",
                    "tier": "TIER_2",
                    "cooldownTicks": 1200
                  },
                  "command": {
                    "name": "",
                    "count": -1,
                    "quotedSearch": false
                  }
                }
              ],
              "deliveryResume": {
                "requester": " Carl ",
                "kitName": " green ",
                "requestedCount": 3,
                "gatheredCount": 7,
                "preTpaDimension": " minecraft:overworld ",
                "savedAt": " 2026-06-15T12:00:00Z "
              }
            }
            """);

        assertEquals(1, state.queuedRequests().size(), "invalid queue entries are pruned");
        assertEquals(
            new QueuedKitRequest(
                new KitbotRequesterAccess("Alice", "alice", KitbotTier.TIER_1, 0),
                new KitCommand("blue", 2, true)
            ),
            state.queuedRequests().getFirst(),
            "valid queue entry is trimmed and normalized"
        );
        assertEquals(
            new StashKitbotQueueState.DeliveryResume("Carl", "green", 3, 3, "minecraft:overworld", "2026-06-15T12:00:00Z"),
            state.deliveryResume(),
            "delivery resume trims values and caps gathered count"
        );

        StashKitbotQueueState.State invalidResume = StashKitbotQueueState.fromJson("""
            {
              "schemaVersion": 1,
              "queuedRequests": [],
              "deliveryResume": {
                "requester": "Carl",
                "kitName": "green",
                "requestedCount": 3,
                "gatheredCount": 0,
                "preTpaDimension": "minecraft:overworld"
              }
            }
            """);
        assertNull(invalidResume.deliveryResume(), "invalid delivery resume is dropped");
    }

    private static void restoresPersistedQueueIntoSession() {
        StashKitbotSession session = new StashKitbotSession();
        List<QueuedKitRequest> queued = List.of(queued("Alice", "blue", 2), queued("Bob", "red", 1));

        session.replaceQueuedRequests(queued);

        assertEquals(2, session.queuedCount(), "restored queue count matches session queue count");
        assertEquals(queued, session.queuedRequestsSnapshot(), "restored queue snapshot preserves order");
    }

    private static void tracksGatherStartNotificationState() {
        KitRequest request = new KitRequest(
            "Alice",
            "Blue EChest",
            "blue echest",
            2,
            List.of(new KitSource("a", new net.minecraft.util.math.BlockPos(10, 64, 10), List.of(0), 3)),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );

        assertFalse(request.gather.gatherStartNotified, "new request has not announced gathering start");
        request.gather.gatherStartNotified = true;
        assertTrue(request.gather.gatherStartNotified, "gather start notification is tracked on the request");
    }

    private static void reportsNavigatorPathCapability() {
        StashNavigator noPathing = new StashNavigator(null);
        StashTarget target = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));

        assertFalse(noPathing.canPath(), "navigator without pathing reports unavailable");
        assertFalse(noPathing.pathToScannerTarget(target, 4, java.util.Optional.of(new net.minecraft.util.math.BlockPos(9, 64, 10))), "navigator without pathing cannot start scanner path");

        RecordingPathing pathing = new RecordingPathing();
        StashNavigator navigator = new StashNavigator(pathing);
        assertTrue(navigator.canPath(), "navigator with pathing reports available");
        assertTrue(navigator.pathToScannerTarget(target, 4, java.util.Optional.of(new net.minecraft.util.math.BlockPos(9, 64, 10))), "navigator with pathing starts scanner path");
        assertEquals(new net.minecraft.util.math.BlockPos(9, 64, 10), pathing.lastBlockGoal, "scanner path uses standing position");
    }

    private static QueuedKitRequest queued(String requester, String kitName, int count) {
        String normalized = StashKitbotAccessPlanner.normalize(requester);
        return new QueuedKitRequest(
            new KitbotRequesterAccess(requester, normalized, KitbotTier.TIER_1, 1200),
            new KitCommand(kitName, count)
        );
    }

    private static final class RecordingPathing implements StashNavigator.BaritonePathing {
        private net.minecraft.util.math.BlockPos lastBlockGoal;

        @Override
        public void pathNear(net.minecraft.util.math.BlockPos pos, int radius) {
        }

        @Override
        public void pathToBlock(net.minecraft.util.math.BlockPos pos) {
            lastBlockGoal = pos;
        }

        @Override
        public boolean isPathing() {
            return false;
        }

        @Override
        public boolean hasPath() {
            return lastBlockGoal != null;
        }

        @Override
        public void stop() {
            lastBlockGoal = null;
        }
    }
}
