package com.watchmenbot.modules.stash;

import com.watchmenbot.hud.StashKitbotStatsHudText;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;

final class StashKitbotStatsHudPureTest {
    private StashKitbotStatsHudPureTest() {
    }

    static void run() {
        formatsDiscordWebhookPayload();
        recordsKitbotDeliveryStats();
        sortsTopKitbotRequesters();
        sanitizesKitbotStatsJson();
        buildsKitbotHudText();
    }

    private static void formatsDiscordWebhookPayload() {
        KitRequest request = new KitRequest(
            "Alice_\"One\"",
            "Blue `EChest`",
            "blue echest",
            2,
            List.of(new KitSource("a", new net.minecraft.util.math.BlockPos(10, 64, 10), List.of(0), 2)),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );
        request.delivery.delivered = 2;

        String payload = StashKitbotDiscordWebhook.payload(request, Instant.parse("2026-06-11T12:00:00Z"));

        assertTrue(payload.contains("\"username\": \"WMBot Kitbot\""), "webhook username");
        assertTrue(payload.contains("Alice_\\\"One\\\""), "requester is json escaped");
        assertTrue(payload.contains("Blue 'EChest'"), "kit markdown backticks are softened in description");
        assertTrue(payload.contains("\"value\": \"2/2\""), "delivered count field");
        assertTrue(payload.contains("\"timestamp\": \"2026-06-11T12:00:00Z\""), "timestamp field");
    }

    private static void recordsKitbotDeliveryStats() {
        StashKitbotStats.CounterState empty = new StashKitbotStats.CounterState(0, 0, Map.of());

        StashKitbotStats.CounterState first = StashKitbotStats.recordDelivery(empty, "Alice", 3);
        assertEquals(1L, first.completedDeliveries(), "first delivery increments completed request count");
        assertEquals(3L, first.deliveredShulkers(), "first delivery increments delivered shulkers");
        assertEquals(3L, first.requesterShulkers().get("Alice"), "first delivery records requester shulkers");

        StashKitbotStats.CounterState second = StashKitbotStats.recordDelivery(first, "Alice", 2);
        assertEquals(2L, second.completedDeliveries(), "second delivery increments completed request count");
        assertEquals(5L, second.deliveredShulkers(), "second delivery increments total shulkers");
        assertEquals(5L, second.requesterShulkers().get("Alice"), "same requester totals merge");
    }

    private static void sortsTopKitbotRequesters() {
        StashKitbotStats.CounterState state = new StashKitbotStats.CounterState(3, 18, Map.of(
            "Bob", 5L,
            "Alice", 5L,
            "Carl", 8L
        ));

        assertEquals(List.of(
            new StashKitbot.RequesterDeliveryCount("Carl", 8),
            new StashKitbot.RequesterDeliveryCount("Alice", 5),
            new StashKitbot.RequesterDeliveryCount("Bob", 5)
        ), StashKitbotStats.snapshot(state).topRequesters(), "top requesters sort by shulkers then name");
    }

    private static void sanitizesKitbotStatsJson() {
        assertEquals(
            new StashKitbotStats.CounterState(0, 0, Map.of()),
            StashKitbotStats.fromJson("{bad json"),
            "malformed stats json falls back to empty"
        );
        assertEquals(
            new StashKitbotStats.CounterState(0, 0, Map.of()),
            StashKitbotStats.fromJson(""),
            "blank stats json falls back to empty"
        );

        StashKitbotStats.CounterState sanitized = StashKitbotStats.fromJson("""
            {
              "schemaVersion": 1,
              "completedDeliveries": -3,
              "deliveredShulkers": -7,
              "requesterShulkers": {
                "Alice": -4,
                "": 9
              }
            }
            """);
        assertEquals(0L, sanitized.completedDeliveries(), "negative completed deliveries sanitize to zero");
        assertEquals(0L, sanitized.deliveredShulkers(), "negative delivered shulkers sanitize to zero");
        assertEquals(Map.of("Alice", 0L), sanitized.requesterShulkers(), "requester totals sanitize values and names");
    }

    private static void buildsKitbotHudText() {
        assertEquals(List.of(
            "Stash Kitbot",
            "Status: inactive",
            "Phase: idle",
            "Queued requests: 0",
            "Completed deliveries: 0",
            "Delivered shulkers: 0",
            "Top requesters:",
            "  none"
        ), StashKitbotStatsHudText.buildLines(new StashKitbot.KitbotStatsSnapshot(
            false,
            "idle",
            null,
            0,
            0,
            0,
            List.of()
        )), "inactive kitbot hud lines");

        assertEquals(List.of(
            "Stash Kitbot",
            "Status: active",
            "Phase: idle",
            "Queued requests: 2",
            "Completed deliveries: 1",
            "Delivered shulkers: 4",
            "Top requesters:",
            "  Dana: 4"
        ), StashKitbotStatsHudText.buildLines(new StashKitbot.KitbotStatsSnapshot(
            true,
            "idle",
            null,
            2,
            1,
            4,
            List.of(new StashKitbot.RequesterDeliveryCount("Dana", 4))
        )), "active kitbot no request hud lines");

        assertEquals(List.of(
            "Stash Kitbot",
            "Status: active",
            "Phase: throwing",
            "Queued requests: 1",
            "Completed deliveries: 2",
            "Delivered shulkers: 9",
            "Alice: Blue EChest x5",
            "Gathered: 5/5",
            "Delivered: 3/5",
            "Top requesters:",
            "  Bob: 6",
            "  Alice: 3"
        ), StashKitbotStatsHudText.buildLines(new StashKitbot.KitbotStatsSnapshot(
            true,
            "throwing",
            new StashKitbot.CurrentRequest("Alice", "Blue EChest", 5, 5, 3),
            1,
            2,
            9,
            List.of(
                new StashKitbot.RequesterDeliveryCount("Bob", 6),
                new StashKitbot.RequesterDeliveryCount("Alice", 3)
            )
        )), "active kitbot current request hud lines");
    }
}
