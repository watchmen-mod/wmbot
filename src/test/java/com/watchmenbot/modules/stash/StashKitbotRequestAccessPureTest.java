package com.watchmenbot.modules.stash;

import java.util.List;
import java.util.Optional;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertFalse;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertNull;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;

final class StashKitbotRequestAccessPureTest {
    private StashKitbotRequestAccessPureTest() {
    }

    static void run() {
        parsesWhisperFormats();
        parsesKitCommands();
        parsesKitListIntent();
        validatesAllowedRequesters();
        resolvesTieredRequesterAccess();
        appliesTierCooldownsPerTier();
        plansTieredKitAccess();
        sanitizesKitbotCooldownsJson();
    }

    private static void parsesWhisperFormats() {
        StashKitbotRequestParser parser = new StashKitbotRequestParser();

        assertEquals(new Whisper("Alice", "echest 5"), parser.parseWhisper("Alice whispers to you: echest 5"), "plain whisper");
        assertEquals(new Whisper("Bot_One", "red kit 2"), parser.parseWhisper("[Bot_One -> me] red kit 2"), "bracket whisper");
        assertEquals(new Whisper("Steve", "pvp 1"), parser.parseWhisper("From Steve: pvp 1"), "from whisper");
    }

    private static void parsesKitCommands() {
        StashKitbotRequestParser parser = new StashKitbotRequestParser();

        assertEquals(new KitCommand("echest", 5), parser.parseCommand("echest 5"), "single-token command");
        assertEquals(new KitCommand("red kit", 12), parser.parseCommand("red kit 12"), "multi-token command");
        assertEquals(new KitCommand("watchmen kit", 2, true), parser.parseCommand("\"watchmen kit\" 2"), "quoted multi-word command");
        assertEquals(new KitCommand("echest", 2), parser.parseCommand("kit echest 2"), "optional kit prefix before plain command");
        assertEquals(new KitCommand("the watchmen's kit", 2, true), parser.parseCommand("kit \"the watchmen's kit\" 2"), "optional kit prefix before quoted command");
        assertEquals(new KitCommand("the watchmen's kit", 2, true), parser.parseCommand(" KIT   \"the watchmen's kit\" 2 "), "optional kit prefix trims spacing and ignores case");
        assertEquals(new KitCommand("echest", 3), parser.parseCommand("3", true, "echest"), "tier 2 count-only command defaults to echest");
        assertNull(parser.parseCommand("echest"), "missing count");
        assertNull(parser.parseCommand("echest five"), "non-numeric count");
        assertNull(parser.parseCommand("\"watchmen kit\" two"), "quoted command still requires numeric count");
        assertNull(parser.parseCommand("kit \"watchmen kit\" two"), "prefixed quoted command still requires numeric count");
        assertNull(parser.parseCommand("3", false, "echest"), "tier 1 does not accept count-only command");
    }

    private static void parsesKitListIntent() {
        StashKitbotRequestParser parser = new StashKitbotRequestParser();

        assertTrue(parser.parseIntent("kits").isListKits(), "kits is a kit-list request");
        assertTrue(parser.parseIntent(" KITS ").isListKits(), "kit-list request is trimmed and case-insensitive");
        assertFalse(parser.parseIntent("echest 1").isListKits(), "delivery command is not a kit-list request");
        assertNull(parser.parseCommand("kits"), "kit-list trigger is not parsed as a delivery command");
    }

    private static void validatesAllowedRequesters() {
        StashKitbotRequestParser parser = new StashKitbotRequestParser();

        assertTrue(parser.isAllowed("Alice", "Alice,Bob"), "listed requester allowed");
        assertTrue(parser.isAllowed("alice", " Alice , BOB "), "requester allowlist is trimmed and case-insensitive");
        assertFalse(parser.isAllowed("Charlie", "Alice,Bob"), "unlisted requester rejected");
    }

    private static void resolvesTieredRequesterAccess() {
        Optional<KitbotRequesterAccess> tier1 = StashKitbotAccessPlanner.resolveAccess(
            "alice",
            "Alice, Bob",
            1200,
            "alice, Charlie",
            2400
        );

        assertTrue(tier1.isPresent(), "tier 1 requester resolved");
        assertEquals(KitbotTier.TIER_1, tier1.get().tier(), "tier 1 wins overlap");
        assertEquals("alice", tier1.get().normalizedRequester(), "requester normalized");
        assertEquals(1200, tier1.get().cooldownTicks(), "tier 1 cooldown applies to every tier 1 user");

        Optional<KitbotRequesterAccess> tier2 = StashKitbotAccessPlanner.resolveAccess(
            "CHARLIE",
            "Alice, Bob",
            1200,
            "alice, Charlie",
            2400
        );

        assertTrue(tier2.isPresent(), "tier 2 requester resolved");
        assertEquals(KitbotTier.TIER_2, tier2.get().tier(), "tier 2 resolved after tier 1 miss");
        assertEquals(2400, tier2.get().cooldownTicks(), "tier 2 cooldown applies to every tier 2 user");
        assertTrue(StashKitbotAccessPlanner.resolveAccess("Dana", "Alice", 1200, "Bob", 2400).isEmpty(), "unlisted requester rejected");
    }

    private static void appliesTierCooldownsPerTier() {
        assertEquals(List.of("alice", "bob"), StashKitbotAccessPlanner.splitList(" Alice, ,BOB "), "nickname list trims and skips blanks");

        Optional<KitbotRequesterAccess> firstTierUser = StashKitbotAccessPlanner.resolveAccess(
            "Alice",
            "Alice, Bob",
            1200,
            "",
            0
        );
        Optional<KitbotRequesterAccess> secondTierUser = StashKitbotAccessPlanner.resolveAccess(
            "Bob",
            "Alice, Bob",
            1200,
            "",
            0
        );
        Optional<KitbotRequesterAccess> negativeCooldown = StashKitbotAccessPlanner.resolveAccess(
            "Charlie",
            "Charlie",
            -5,
            "",
            0
        );

        assertTrue(firstTierUser.isPresent(), "first tier user resolved");
        assertTrue(secondTierUser.isPresent(), "second tier user resolved");
        assertEquals(1200, firstTierUser.get().cooldownTicks(), "first user gets tier cooldown");
        assertEquals(1200, secondTierUser.get().cooldownTicks(), "second user gets same tier cooldown");
        assertTrue(negativeCooldown.isPresent(), "negative cooldown requester resolved");
        assertEquals(0, negativeCooldown.get().cooldownTicks(), "negative tier cooldown sanitizes to zero");
    }

    private static void plansTieredKitAccess() {
        KitbotRequesterAccess tier1 = new KitbotRequesterAccess("Alice", "alice", KitbotTier.TIER_1, 1200);
        KitbotRequesterAccess tier2 = new KitbotRequesterAccess("Bob", "bob", KitbotTier.TIER_2, 2400);

        assertTrue(StashKitbotAccessPlanner.requestAllowed(tier1, new KitCommand("any rare kit", 1), "echest"), "tier 1 can request any kit query");
        assertTrue(StashKitbotAccessPlanner.requestAllowed(tier2, new KitCommand("echest", 1), "echest,pvp"), "tier 2 can request whitelisted alias");
        assertFalse(StashKitbotAccessPlanner.requestAllowed(tier2, new KitCommand("rare kit", 1), "echest,pvp"), "tier 2 rejects non-whitelisted alias");
        assertTrue(StashKitbotAccessPlanner.tier2KitAllowed(" EChest ", "echest,pvp"), "tier 2 whitelist is case-insensitive");
        assertEquals(60_000L, StashKitbotAccessPlanner.cooldownExpiryMillis(0L, 1200), "cooldown ticks convert to wall-clock millis");
        assertEquals(45_000L, StashKitbotAccessPlanner.remainingCooldownMillis(15_000L, 60_000L), "remaining cooldown clamps to future millis");
        assertEquals(0L, StashKitbotAccessPlanner.remainingCooldownMillis(60_001L, 60_000L), "expired cooldown clamps to zero");
        assertTrue(StashKitbotAccessPlanner.shouldStartCooldown(true, 1), "accepted request starts configured cooldown");
        assertFalse(StashKitbotAccessPlanner.shouldStartCooldown(false, 1200), "rejected request does not start cooldown");
        assertFalse(StashKitbotAccessPlanner.shouldStartCooldown(true, 0), "zero cooldown does not write cooldown state");
    }

    private static void sanitizesKitbotCooldownsJson() {
        StashKitbotCooldowns.CooldownState state = StashKitbotCooldowns.fromJson("""
            {
              "schemaVersion": 1,
              "cooldowns": {
                " Alice ": {
                  "tier": "TIER_1",
                  "expiresAtMillis": 2000
                },
                "Bob": {
                  "tier": "TIER_2",
                  "expiresAtMillis": 900
                },
                "": {
                  "tier": "TIER_1",
                  "expiresAtMillis": 3000
                },
                "NoTier": {
                  "expiresAtMillis": 3000
                }
              }
            }
            """, 1000L);

        assertEquals(1, state.entries().size(), "expired and malformed cooldown entries are pruned");
        assertTrue(state.entries().containsKey("alice"), "cooldown requester key normalized");
        assertEquals(new StashKitbotCooldowns.CooldownEntry(KitbotTier.TIER_1, 2000L), state.entries().get("alice"), "valid cooldown entry preserved");
    }
}
