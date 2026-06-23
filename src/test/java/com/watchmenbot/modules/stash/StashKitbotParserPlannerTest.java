package com.watchmenbot.modules.stash;

import com.watchmenbot.hud.StashKitbotStatsHudText;
import com.watchmenbot.util.AtomicJsonFile;
import com.watchmenbot.util.TickTimer;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StashKitbotParserPlannerTest {
    private StashKitbotParserPlannerTest() {
    }

    public static void main(String[] args) {
        parsesWhisperFormats();
        parsesKitCommands();
        parsesKitListIntent();
        validatesAllowedRequesters();
        resolvesTieredRequesterAccess();
        appliesTierCooldownsPerTier();
        plansTieredKitAccess();
        readsAndWritesAtomicJsonFiles();
        sanitizesKitbotCooldownsJson();
        parsesCooldownDurations();
        ignoresTeleportCountdownAsCooldown();
        plansReliableDeliveryPositioning();
        plansResilientDeliveryPositionFallback();
        plansCrossDimensionTeleportDetection();
        plansRequesterReacquirePolicy();
        plansRequesterSearchReadiness();
        plansGatherConfirmation();
        plansDeliveryCommandPolicy();
        plansKillHomeRespawnPolicy();
        normalizesFancyKitNames();
        selectsEchestKitsByPureContent();
        ignoresNamedEchestKitsWithoutPureContent();
        keepsNonEchestKitMatchingNameBased();
        buildsEchestSourcePlansFromPureContent();
        selectsFancyKitByPlainAlias();
        selectsQuotedKitByPhraseAlias();
        resolvesAmbiguousKitSearches();
        formatsAmbiguousKitChoices();
        listsAvailableKitsByTier();
        formatsAvailableKitListMessages();
        combinesInventoryAndCachedKitCounts();
        combinesFancyAliasVariants();
        selectsInventoryOnlyKit();
        buildsSourcePlans();
        buildsFancyAliasSourcePlans();
        choosesClosestKitbotSourceFromCurrentLocation();
        reranksKitbotSourcesAfterMovement();
        skipsFailedKitbotSourcesForNextClosestSource();
        suppressesFailedKitbotSourceNeighborhoodButAllowsUnrelatedSources();
        reconsidersSuppressedKitbotSourceNeighborhoodAfterMovement();
        updatesCachedContainerAndItemIndex();
        depletesCachedSourceSlotAfterConfirmedTransfer();
        decrementsCachedSourceSlotStackAfterPartialTransfer();
        keepsCacheWhenDepletedSourceSlotDoesNotMatch();
        depletesPureEchestCachedSourceSlot();
        groupsScannerTopShulkersByNormalizedName();
        cachesScannerStatsUntilSessionContentChanges();
        dedupesLoadedContainersById();
        removesStaleContainersWhenSkipped();
        clearsAlreadyKnownSkippedTarget();
        doesNotQueueCachedSkippedTargets();
        plansScannerNearestTargetOrdering();
        plansScannerAisleFriendlyOrdering();
        penalizesScannerTimeoutNeighborhoodsConservatively();
        reconsidersScannerTimeoutNeighborhoodsAfterMovement();
        scannerPathTimeoutSkipWireBehaviorIsUnchanged();
        resetsScannerPathProgressPerTarget();
        scannerPathProgressAllowsDistanceImprovement();
        scannerPathProgressAllowsBlockMovement();
        scannerPathProgressFastFailsNoBaritonePath();
        scannerPathProgressFastFailsNoProgress();
        scannerFastFailKeepsPathTimeoutSkipSemantics();
        allowsScannerPathStartBurst();
        throttlesScannerPathStartsAfterBurst();
        restoresScannerPathStartsAfterCooldown();
        suppressesRepeatedScannerFailureNeighborhood();
        reconsidersScannerSuppressionAfterMovement();
        keepsSuppressedScannerTargetsQueuedWhileSelectingOtherTargets();
        selectsRollingNearbyReachableScannerTarget();
        ignoresOutOfRangeRollingScannerTargets();
        requeuesInterruptedScannerPathTarget();
        preservesInterruptedTargetAfterNearbySkip();
        retriesScannerOpenWhenCallbackNeverFires();
        retriesScannerOpenWhenInteractionRejected();
        failsScannerOpenAfterRetryBudget();
        clearsScannerOpenAttemptsAfterCurrentClear();
        appliesShulkerScanSkipToggle();
        expiresTimerByElapsedTime();
        keepsSkipReasonWireValues();
        managesKitbotSessionState();
        managesKitbotRequestQueue();
        plansKitbotQueueCapacity();
        persistsKitbotQueueState();
        sanitizesKitbotQueueState();
        restoresPersistedQueueIntoSession();
        formatsDiscordWebhookPayload();
        recordsKitbotDeliveryStats();
        sortsTopKitbotRequesters();
        sanitizesKitbotStatsJson();
        buildsKitbotHudText();
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

    private static void readsAndWritesAtomicJsonFiles() {
        try {
            Path tempDir = Files.createTempDirectory("watchmenbot-atomic-json-test");
            Path file = tempDir.resolve("state.json");

            AtomicJsonFile.ReadResult<String> missing = AtomicJsonFile.readIfExists(file, "fallback", reader -> "unexpected");
            assertEquals("fallback", missing.value(), "missing atomic json file returns fallback");
            assertFalse(missing.failed(), "missing atomic json file is not a read failure");

            Files.writeString(file, "{not-json");
            AtomicJsonFile.ReadResult<Integer> failed = AtomicJsonFile.readIfExists(file, 7, reader -> {
                throw new IOException("malformed");
            });
            assertEquals(7, failed.value(), "failed atomic json read returns fallback");
            assertTrue(failed.failed(), "failed atomic json read surfaces error result");

            AtomicJsonFile.write(file, writer -> writer.write("{\"ok\":true}"));
            assertEquals("{\"ok\":true}", Files.readString(file), "atomic json write replaces file contents");
        }
        catch (Exception exception) {
            throw new AssertionError("atomic json file test failed", exception);
        }
    }

    private static void parsesCooldownDurations() {
        StashKitbotDelivery delivery = new StashKitbotDelivery(null);

        assertEquals(200, delivery.parseCooldownTicks("Please wait 10s before using this command."), "seconds cooldown");
        assertEquals(3000, delivery.parseCooldownTicks("Try again in 2m 30s."), "compact minutes seconds cooldown");
        assertEquals(1800, delivery.parseCooldownTicks("Available in 00:01:30."), "clock cooldown");
    }

    private static void ignoresTeleportCountdownAsCooldown() {
        StashKitbotDelivery delivery = new StashKitbotDelivery(null);

        assertFalse(delivery.looksLikeCooldown("Teleporting in 15 seconds. Do not move."), "teleport countdown false positive");
        assertTrue(delivery.looksLikeCooldown("You must wait 10 seconds before using this command again."), "real cooldown");
        assertTrue(
            StashKitbotDeliveryPlanner.ignoreForCooldownHandling("[Meteor] [Stash Kitbot] Queued possible TPA cooldown for attempt 1: 1m."),
            "module log lines are ignored for cooldown handling"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.ignoreForCooldownHandling("You whisper to Steve: TPA is cooling down for about 1m."),
            "outgoing whisper echoes are ignored for cooldown handling"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.ignoreForCooldownHandling("Please wait 10s before using this command again."),
            "server cooldown messages are still handled"
        );
    }

    private static void plansReliableDeliveryPositioning() {
        StashKitbotDelivery delivery = new StashKitbotDelivery(null);

        assertFalse(delivery.readyToThrow(9, 0, 5), "too close to throw");
        assertTrue(delivery.readyToThrow(16, 0, 5), "minimum safe throw distance near spot");
        assertTrue(delivery.readyToThrow(25, 100, 5), "preferred distance can throw without exact spot");
        assertTrue(delivery.shouldStepAwayDirectly(25), "direct step while close");
        assertFalse(delivery.shouldStepAwayDirectly(36), "baritone can take over farther away");
    }

    private static void plansResilientDeliveryPositionFallback() {
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 16, 0, 5, false, false, false),
            "normal ready position throws immediately"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.DIRECT_STEP_AWAY,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 9, 100, 5, false, false, false),
            "too-close requester steps away before fallback"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.PATH_TO_SPOT,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 49, 100, 8, false, false, false),
            "visible requester keeps pathing toward preferred spot before timeout"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 49, 100, 8, false, true, false),
            "visible requester falls back to throw when ideal positioning expires"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 9, 100, 5, false, false, true),
            "expired direct step falls back to throw instead of waiting for full delivery timeout"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 9, 100, 5, false, true, false),
            "expired awkward close positioning still delivers instead of getting stuck"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 49, 100, 8, true, false, false),
            "cross-dimension visible requester falls back quickly when pathing cannot settle"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.DIRECT_STEP_AWAY,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(true, 9, 100, 5, true, false, false),
            "too-close cross-dimension requester still steps away before fallback throw"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryPositionDecision.FAIL_HOME,
            StashKitbotDeliveryPlanner.deliveryPositionDecision(false, 100, 0, 5, true, true, false),
            "missing requester returns home with kits"
        );
    }

    private static void plansCrossDimensionTeleportDetection() {
        BlockPos origin = new BlockPos(0, 64, 0);

        assertTrue(
            StashKitbotDeliveryPlanner.teleportArrivalDetected(origin, new BlockPos(9, 64, 0), "minecraft:overworld", "minecraft:overworld"),
            "moved far in same dimension detects teleport arrival"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.teleportArrivalDetected(origin, new BlockPos(1, 64, 1), "minecraft:overworld", "minecraft:the_nether"),
            "dimension change detects teleport arrival even without large coordinate movement"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.dimensionChangedAfterTpa("minecraft:overworld", "minecraft:the_nether"),
            "dimension change is exposed for delivery policy"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.shouldCorrectCrossDimensionFlag(false, "minecraft:overworld", "minecraft:the_nether"),
            "uncorrected cross-dimension flag is repaired after dimension change"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.shouldCorrectCrossDimensionFlag(true, "minecraft:overworld", "minecraft:the_nether"),
            "already-corrected cross-dimension flag is not repaired repeatedly"
        );
        assertEquals(
            40,
            StashKitbotDeliveryPlanner.correctedCrossDimensionSettleTicks(0, 40),
            "missing settle ticks are reset after cross-dimension correction"
        );
        assertEquals(
            12,
            StashKitbotDeliveryPlanner.correctedCrossDimensionSettleTicks(12, 40),
            "existing settle countdown is preserved after cross-dimension correction"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.teleportArrivalDetected(origin, new BlockPos(1, 64, 1), "minecraft:overworld", "minecraft:overworld"),
            "same dimension and nearby position does not detect teleport arrival"
        );
        assertEquals(
            128,
            StashKitbotDeliveryPlanner.effectiveRequesterSearchRadius(32, true),
            "cross-dimension delivery widens requester reacquire radius"
        );
        assertEquals(
            32,
            StashKitbotDeliveryPlanner.effectiveRequesterSearchRadius(32, false),
            "same-dimension delivery keeps configured requester search radius"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.requesterNameMatches("sup3rd00b", "OtherPlayer", "[VIP] OtherPlayer", "OtherPlayer"),
            "missing exact requester does not match a visible nearby player"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.requesterNameMatches("sup3rd00b", "Sup3rD00b", null, null),
            "game profile requester match is case-insensitive"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.requesterNameMatches("sup3rd00b", null, "[VIP] Sup3rD00b", null),
            "display name token can identify loaded requester"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.DIRECT_STEP_AWAY,
            StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(true, 9, 5, false, false),
            "cross-dimension requester too close steps away"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(true, 25, 5, false, false),
            "cross-dimension requester in practical range throws"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.APPROACH_REQUESTER,
            StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(true, 400, 5, false, false),
            "cross-dimension requester far away is approached directly"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.FAIL_HOME,
            StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(true, 400, 5, true, false),
            "cross-dimension requester still far after movement timeout returns home"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.FAIL_HOME,
            StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(false, 0, 5, false, false),
            "missing requester never throws blindly"
        );
    }

    private static void plansRequesterReacquirePolicy() {
        assertTrue(
            StashKitbotDeliveryPlanner.shouldKeepReacquiringRequester(false, false),
            "missing requester waits during reacquire window"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.shouldKeepReacquiringRequester(true, false),
            "found requester stops reacquire wait"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.shouldKeepReacquiringRequester(false, true),
            "expired reacquire window stops waiting"
        );
    }

    private static void plansRequesterSearchReadiness() {
        assertFalse(
            StashKitbotDeliveryPlanner.requesterSearchReady(false, true, true),
            "missing world treats requester as not loaded"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.requesterSearchReady(true, false, true),
            "missing player treats requester as not loaded"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.requesterSearchReady(true, true, false),
            "missing requester name does not search"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.requesterSearchReady(true, true, true),
            "ready client state can search requester"
        );
    }

    private static void plansGatherConfirmation() {
        assertEquals(
            2,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 2),
            "initial inventory contributes to gathered count"
        );
        assertEquals(
            3,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 3),
            "confirmed transfer increases gathered count"
        );
        assertEquals(
            5,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 20),
            "confirmed gathered count caps at request count"
        );
        assertEquals(
            2,
            StashKitbotGatherPlanner.confirmedGathered(5, 2, 1),
            "temporary lower inventory count does not reduce initial fulfillment"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.WAIT,
            StashKitbotGatherPlanner.transferVerifyDecision(2, 5, 2, 2, true, false),
            "transfer verification waits before inventory changes"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTINUE_TAKING,
            StashKitbotGatherPlanner.transferVerifyDecision(3, 5, 2, 3, true, false),
            "verified transfer continues taking when more are needed"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.COMPLETE_REQUEST,
            StashKitbotGatherPlanner.transferVerifyDecision(5, 5, 4, 5, false, false),
            "closed container after successful transfer can complete request"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTAINER_CLOSED,
            StashKitbotGatherPlanner.transferVerifyDecision(3, 5, 2, 3, false, false),
            "closed container before completion skips current source"
        );
        assertEquals(
            StashKitbotGatherPlanner.TransferVerifyDecision.CONTINUE_TAKING,
            StashKitbotGatherPlanner.transferVerifyDecision(2, 5, 2, 2, true, true),
            "transfer verification timeout retries taking from open container"
        );
    }

    private static void plansDeliveryCommandPolicy() {
        assertTrue(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.TPA_REQUEST, false, false),
            "tpa request phase can send first tpa"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.TPA_REQUEST, false, true),
            "tpa request phase blocks duplicate sent tpa"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.TPA_REQUEST, true, false),
            "active tpa cooldown blocks send"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.WAITING_FOR_TPY, false, false),
            "waiting for tpy never sends another tpa"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRequestDecision.START_DELIVERY,
            StashKitbotDeliveryPlanner.tpaRequestDecision(true, true, false),
            "detected teleport starts delivery even while tpa cooldown is active"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRequestDecision.START_DELIVERY,
            StashKitbotDeliveryPlanner.tpaRequestDecision(true, false, true),
            "detected teleport starts delivery even after tpa was already sent"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRequestDecision.WAIT,
            StashKitbotDeliveryPlanner.tpaRequestDecision(false, true, false),
            "no teleport plus active tpa cooldown still waits"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRequestDecision.SEND_TPA,
            StashKitbotDeliveryPlanner.tpaRequestDecision(false, false, false),
            "no teleport and no cooldown sends tpa"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.tpaTimeoutShouldReturn(false, true),
            "tpa timeout returns with kits instead of retrying"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaTimeoutShouldReturn(true, true),
            "detected teleport does not return on timeout branch"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaCooldownShouldApply(true, true),
            "late tpa cooldown after detected teleport is stale"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.tpaCooldownShouldApply(false, true),
            "pre-teleport tpa cooldown still applies"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaCooldownShouldApply(false, false),
            "non-applicable tpa cooldown remains ignored before teleport"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.tpaCooldownMayRearm(KitbotPhase.WAITING_FOR_TPY, true),
            "tpa cooldown may rearm only while waiting for tpy after a sent tpa"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaCooldownMayRearm(KitbotPhase.MOVE_TO_DELIVERY_SPOT, true),
            "tpa cooldown cannot rearm after teleport delivery starts"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaCooldownMayRearm(KitbotPhase.HOME_REQUEST, true),
            "tpa cooldown cannot rearm during post-delivery home handling"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaCooldownMayRearm(KitbotPhase.WAITING_FOR_TPY, false),
            "tpa cooldown cannot rearm after the sent attempt is cleared for retry"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.tpaRequestAccepted("Request sent to: sup3rd00b", "sup3rd00b"),
            "request sent message accepts active requester"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.tpaRequestAccepted("Teleport request sent to Sup3rD00b.", "sup3rd00b"),
            "teleport request sent message is case-insensitive"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.tpaRequestAccepted("Request sent to: OtherPlayer", "sup3rd00b"),
            "request sent to another player is ignored"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.PendingCooldownDecision.WAIT,
            StashKitbotDeliveryPlanner.pendingCooldownDecision(false, false, 1),
            "tpa cooldown candidate waits during grace window"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.PendingCooldownDecision.DISCARD,
            StashKitbotDeliveryPlanner.pendingCooldownDecision(true, false, 1),
            "tpa success discards pending cooldown candidate"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.PendingCooldownDecision.DISCARD,
            StashKitbotDeliveryPlanner.pendingCooldownDecision(false, true, 1),
            "teleport arrival discards pending cooldown candidate"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.PendingCooldownDecision.APPLY,
            StashKitbotDeliveryPlanner.pendingCooldownDecision(false, false, 0),
            "tpa cooldown candidate applies after grace expires"
        );

        assertTrue(
            StashKitbotDeliveryPlanner.cooldownApplies(
                PendingCommand.TPA,
                PendingCommand.TPA,
                true,
                true,
                true,
                KitbotPhase.WAITING_FOR_TPY,
                KitbotPhase.WAITING_FOR_TPY
            ),
            "matching pending tpa cooldown applies while waiting for tpy"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.cooldownApplies(
                PendingCommand.TPA,
                PendingCommand.TPA,
                true,
                true,
                true,
                KitbotPhase.MOVE_TO_DELIVERY_SPOT,
                KitbotPhase.WAITING_FOR_TPY
            ),
            "late tpa cooldown after teleport is stale"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.cooldownApplies(
                PendingCommand.HOME,
                PendingCommand.TPA,
                true,
                true,
                true,
                KitbotPhase.WAITING_FOR_TPY,
                KitbotPhase.WAITING_FOR_TPY
            ),
            "home cooldown cannot satisfy tpa attempt"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.cooldownApplies(
                PendingCommand.TPA,
                PendingCommand.TPA,
                false,
                true,
                true,
                KitbotPhase.WAITING_FOR_TPY,
                KitbotPhase.WAITING_FOR_TPY
            ),
            "expired messenger window rejects cooldown"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.cooldownApplies(
                PendingCommand.TPA,
                PendingCommand.TPA,
                true,
                true,
                false,
                KitbotPhase.WAITING_FOR_TPY,
                KitbotPhase.WAITING_FOR_TPY
            ),
            "target mismatch rejects cooldown"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CooldownAttribution.COMMAND,
            StashKitbotDeliveryPlanner.cooldownAttribution(true, false, true),
            "command-only cooldown applies to pending command"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CooldownAttribution.COMMAND,
            StashKitbotDeliveryPlanner.cooldownAttribution(true, true, true),
            "newer command wins over older pending whisper"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CooldownAttribution.WHISPER,
            StashKitbotDeliveryPlanner.cooldownAttribution(true, true, false),
            "newer whisper cooldown is not misclassified as tpa cooldown"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CooldownAttribution.WHISPER,
            StashKitbotDeliveryPlanner.cooldownAttribution(false, true, false),
            "whisper-only cooldown applies to whisper"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.CooldownAttribution.NONE,
            StashKitbotDeliveryPlanner.cooldownAttribution(false, false, false),
            "cooldown without pending windows is ignored"
        );

        assertTrue(
            StashKitbotDeliveryPlanner.homeSendAllowed(false, false, false, false),
            "home sends when no cooldown or pending home exists"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.homeSendAllowed(false, true, false, false),
            "home does not send twice after already sent"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.homeSendAllowed(false, false, true, false),
            "home does not send while pending cooldown window is active"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.homeSendAllowed(false, false, false, true),
            "home does not send again while confirming"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.pendingCommandExpired(true, false),
            "request pending command expires when messenger window closes"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.pendingCommandExpired(true, true),
            "request pending command remains while messenger window is open"
        );

        DeliveryCommandState commands = new DeliveryCommandState();
        commands.beginPending(PendingCommand.TPA, "Alice", KitbotPhase.WAITING_FOR_TPY);
        assertFalse(commands.tpaSent, "pending tpa is not marked sent before command send succeeds");
        commands.markSent(PendingCommand.TPA);
        assertTrue(commands.tpaSent, "successful command send marks tpa sent");
        commands.clearPending();
        assertFalse(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.TPA_REQUEST, false, commands.tpaSent),
            "completed tpa attempt blocks accidental retry even if phase regresses"
        );
        commands.beginPending(PendingCommand.HOME, "home", KitbotPhase.HOME_CONFIRM);
        assertFalse(
            StashKitbotDeliveryPlanner.tpaSendAllowed(KitbotPhase.HOME_REQUEST, false, commands.tpaSent),
            "post-delivery home command state never sends another tpa"
        );
    }

    private static void plansKillHomeRespawnPolicy() {
        assertTrue(
            StashKitbotDeliveryPlanner.homeCommandRequiresRespawn("/kill"),
            "kill home command requires respawn"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.homeCommandRequiresRespawn("/kill "),
            "trimmed kill home command requires respawn"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.homeCommandRequiresRespawn("/home stash"),
            "home stash command does not require respawn"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.homeCommandRequiresRespawn(""),
            "blank home command uses fallback and does not require respawn"
        );

        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.WAIT,
            StashKitbotDeliveryPlanner.homeConfirmDecision(false, false, false, false, true),
            "normal home waits for confirm timer"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.COMPLETE,
            StashKitbotDeliveryPlanner.homeConfirmDecision(false, true, false, false, true),
            "normal home completes after confirm timer"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.WAIT,
            StashKitbotDeliveryPlanner.homeConfirmDecision(true, true, false, false, true),
            "kill home waits until death is detected"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.REQUEST_RESPAWN,
            StashKitbotDeliveryPlanner.homeConfirmDecision(true, true, true, false, false),
            "kill home requests respawn after death"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.WAIT,
            StashKitbotDeliveryPlanner.homeConfirmDecision(true, true, true, true, false),
            "kill home does not request respawn twice while still dead"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.HomeConfirmDecision.COMPLETE,
            StashKitbotDeliveryPlanner.homeConfirmDecision(true, true, false, true, true),
            "kill home completes after respawn"
        );
    }

    private static void selectsEchestKitsByPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(echestContentCacheFile(), "echest");

        assertTrue(selected.isPresent(), "selected kit present");
        assertEquals(new SelectedKit("echest", "echest", 5), selected.get(), "echest content match ignores shulker name");
    }

    private static void ignoresNamedEchestKitsWithoutPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "echest");

        assertTrue(selected.isEmpty(), "named echest shulkers without pure content metadata are ignored");
    }

    private static void keepsNonEchestKitMatchingNameBased() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "blue");

        assertTrue(selected.isPresent(), "name-based kit selected");
        assertEquals(new SelectedKit("blue echest", "blue echest", 8), selected.get(), "non-echest query keeps name matching");
    }

    private static void buildsEchestSourcePlansFromPureContent() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(echestContentCacheFile(), "echest");

        assertEquals(2, sources.size(), "content echest source count");
        assertEquals(List.of(0), sources.get(0).slots(), "first content source uses unnamed pure echest slot");
        assertEquals(2, sources.get(0).cachedCount(), "first content source count");
        assertEquals(List.of(2), sources.get(1).slots(), "second content source excludes named mixed echest slot");
        assertEquals(3, sources.get(1).cachedCount(), "second content source count");
    }

    private static void normalizesFancyKitNames() {
        String fancyWatchmen = "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽█▓▒░";
        String fancyKit = "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░";

        assertEquals("the watchmen", StashKitNameNormalizer.canonicalAlias(fancyWatchmen), "fancy watchmen canonical alias");
        assertEquals("the watchmen's kit", StashKitNameNormalizer.canonicalAlias(fancyKit), "fancy watchmen kit canonical alias");
        assertEquals("watchmen", StashKitNameNormalizer.alias(fancyWatchmen), "fancy watchmen loose alias");
        assertEquals("watchmen", StashKitNameNormalizer.alias(fancyKit), "fancy watchmen kit loose alias");
        assertEquals("the watchmen's kit", StashKitNameNormalizer.phraseAlias(fancyKit), "fancy watchmen phrase alias");
        assertTrue(StashKitNameNormalizer.matches(fancyKit, "watchmen"), "plain query matches fancy alias");
        assertTrue(StashKitNameNormalizer.matches(fancyKit, "watchmen kit", true), "quoted query matches depossessed phrase alias");
        assertTrue(StashKitNameNormalizer.matchesAliasKey(fancyKit, "watchmen"), "alias key matches loose alias");
        assertTrue(StashKitNameNormalizer.matchesAliasKey(fancyKit, "the watchmen's kit"), "alias key matches canonical alias");
        assertTrue(StashKitNameNormalizer.matches("Blue EChest", "echest"), "plain kit keeps substring matching");
    }

    private static void selectsFancyKitByPlainAlias() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(fancyCacheFile(), "the watchmen's kit", true, Map.of());

        assertTrue(selected.isPresent(), "fancy selected kit present");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), selected.get(), "quoted query selects fancy kit");
    }

    private static void selectsQuotedKitByPhraseAlias() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(phraseCacheFile(), "watchmen kit", true, Map.of());

        assertTrue(selected.isPresent(), "quoted selected kit present");
        assertEquals(new SelectedKit("the watchmen kit", "the watchmen kit", 3), selected.get(), "quoted query keeps kit word selective");

        List<KitSource> sources = planner.buildSources(phraseCacheFile(), selected.get().alias());
        assertEquals(1, sources.size(), "quoted phrase source count");
        assertEquals(List.of(1), sources.getFirst().slots(), "quoted phrase source slots exclude pvp kit");
    }

    private static void resolvesAmbiguousKitSearches() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        StashKitbotStockPlanner.KitResolution ambiguous = planner.resolveKit(fancyCacheFile(), "watchmen", false, Map.of());

        assertTrue(ambiguous.ambiguous(), "plain watchmen query is ambiguous");
        assertEquals(
            List.of(
                new SelectedKit("the watchmen kit", "the watchmen kit", 3),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 4)
            ),
            ambiguous.choices(),
            "ambiguous choices are canonical and sorted"
        );

        StashKitbotStockPlanner.KitResolution selected = planner.resolveKit(fancyCacheFile(), "the watchmen's kit", true, Map.of());
        assertTrue(selected.hasSelection(), "quoted canonical query resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), selected.selected(), "quoted canonical selected kit");

        StashKitbotRequestParser parser = new StashKitbotRequestParser();
        KitCommand prefixed = parser.parseCommand("kit \"the watchmen's kit\" 2");
        StashKitbotStockPlanner.KitResolution prefixedSelected = planner.resolveKit(fancyCacheFile(), prefixed.name(), prefixed.quotedSearch(), Map.of());
        assertTrue(prefixedSelected.hasSelection(), "prefixed quoted canonical query resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 4), prefixedSelected.selected(), "prefixed quoted canonical selected kit");

        StashKitbotStockPlanner.KitResolution variantAmbiguous = planner.resolveKit(watchmenVariantCacheFile(), "watchmen", false, Map.of());
        assertTrue(variantAmbiguous.ambiguous(), "unquoted watchmen variant query stays ambiguous");
        assertEquals(
            List.of(
                new SelectedKit("the watchmen", "the watchmen", 1),
                new SelectedKit("the watchmen's echest's", "the watchmen's echest's", 2),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 10)
            ),
            variantAmbiguous.choices(),
            "unquoted watchmen choices include all variants"
        );

        StashKitbotStockPlanner.KitResolution exactVariant = planner.resolveKit(watchmenVariantCacheFile(), "the watchmen's kit", true, Map.of());
        assertTrue(exactVariant.hasSelection(), "quoted exact watchmen variant resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 10), exactVariant.selected(), "quoted exact watchmen kit aggregates duplicate containers");

        KitCommand prefixedVariant = parser.parseCommand("kit \"the watchmen's kit\" 2");
        StashKitbotStockPlanner.KitResolution prefixedExactVariant = planner.resolveKit(watchmenVariantCacheFile(), prefixedVariant.name(), prefixedVariant.quotedSearch(), Map.of());
        assertTrue(prefixedExactVariant.hasSelection(), "prefixed quoted exact watchmen variant resolves one kit");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 10), prefixedExactVariant.selected(), "prefixed quoted exact watchmen kit aggregates duplicate containers");
    }

    private static void formatsAmbiguousKitChoices() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        String message = planner.ambiguousChoicesMessage(
            "watchmen",
            2,
            List.of(
                new SelectedKit("the watchmen", "the watchmen", 1),
                new SelectedKit("the watchmen's kit", "the watchmen's kit", 1)
            )
        );

        assertEquals(
            "Multiple kits match 'watchmen'. Type one exactly: \"the watchmen\" 2, \"the watchmen's kit\" 2.",
            message,
            "ambiguous choices message"
        );
    }

    private static void listsAvailableKitsByTier() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        KitbotRequesterAccess tier1 = new KitbotRequesterAccess("Alice", "alice", KitbotTier.TIER_1, 0);
        KitbotRequesterAccess tier2 = new KitbotRequesterAccess("Bob", "bob", KitbotTier.TIER_2, 0);

        assertEquals(
            List.of("blue echest", "green kit", "red echest", "spare kit"),
            planner.availableKitNames(cacheFile(), Map.of("Spare Kit", 2), tier1, "red echest"),
            "tier 1 sees all stocked kits from cache and inventory"
        );
        assertEquals(
            List.of("red echest", "spare kit"),
            planner.availableKitNames(cacheFile(), Map.of("Spare Kit", 2), tier2, "red echest, spare kit"),
            "tier 2 sees only stocked whitelisted kits"
        );
        assertEquals(
            List.of(),
            planner.availableKitNames(null, Map.of(), tier2, "echest"),
            "empty stock returns no available kits"
        );
    }

    private static void formatsAvailableKitListMessages() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();

        assertEquals(
            List.of("No kits are currently available for your tier."),
            planner.kitListMessages(List.of()),
            "empty kit list message"
        );
        assertEquals(
            List.of("Available kits: blue echest, red echest"),
            planner.kitListMessages(List.of("blue echest", "red echest"), 80),
            "single kit-list message"
        );
        assertEquals(
            List.of(
                "Available kits 1/2: alpha kit, bravo kit",
                "Available kits 2/2: charlie kit"
            ),
            planner.kitListMessages(List.of("alpha kit", "bravo kit", "charlie kit"), 45),
            "long kit lists are chunked deterministically"
        );
    }

    private static void combinesInventoryAndCachedKitCounts() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(echestContentCacheFile(), "echest", Map.of("echest", 2));

        assertTrue(selected.isPresent(), "combined selected kit present");
        assertEquals(new SelectedKit("echest", "echest", 7), selected.get(), "inventory counts contribute to content echest selection");
    }

    private static void combinesFancyAliasVariants() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(fancyPossessiveCacheFile(), "watchmen", Map.of("The Watchmen's Kit", 5));

        assertTrue(selected.isPresent(), "combined fancy selected kit present");
        assertEquals(new SelectedKit("the watchmen's kit", "the watchmen's kit", 9), selected.get(), "canonical variants combine");
    }

    private static void selectsInventoryOnlyKit() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        Optional<SelectedKit> selected = planner.selectKit(cacheFile(), "spare", Map.of("Spare Kit", 2));

        assertTrue(selected.isPresent(), "inventory-only selected kit present");
        assertEquals(new SelectedKit("spare kit", "spare kit", 2), selected.get(), "inventory-only kit can satisfy selection");
    }

    private static void buildsSourcePlans() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(cacheFile(), "blue echest");

        assertEquals(2, sources.size(), "source count");
        assertEquals(new PositionRecord(10, 64, 10), toPosition(sources.get(0).interactionPos()), "first source pos");
        assertEquals(List.of(0), sources.get(0).slots(), "first source slots");
        assertEquals(3, sources.get(0).cachedCount(), "first source count");
        assertEquals(5, sources.get(1).cachedCount(), "second source count");
    }

    private static void buildsFancyAliasSourcePlans() {
        StashKitbotStockPlanner planner = new StashKitbotStockPlanner();
        List<KitSource> sources = planner.buildSources(fancyCacheFile(), "watchmen");

        assertEquals(2, sources.size(), "fancy source count");
        assertEquals(List.of(0), sources.get(0).slots(), "fancy first source slots");
        assertEquals(4, sources.get(0).cachedCount(), "fancy first source count");
        assertEquals(List.of(2), sources.get(1).slots(), "plain alias variant source slots");
        assertEquals(3, sources.get(1).cachedCount(), "plain alias variant source count");
    }

    private static void choosesClosestKitbotSourceFromCurrentLocation() {
        KitRequest request = kitRequestWithSources(
            source("far", 40, 64, 0),
            source("near", 3, 64, 0)
        );

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            new net.minecraft.util.math.BlockPos(0, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather chooses closest source from current location");
    }

    private static void reranksKitbotSourcesAfterMovement() {
        KitRequest request = kitRequestWithSources(
            source("west", 0, 64, 0),
            source("east", 80, 64, 0)
        );

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(75, 65, 0),
            new net.minecraft.util.math.BlockPos(75, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather re-ranks sources after movement");
    }

    private static void skipsFailedKitbotSourcesForNextClosestSource() {
        KitRequest request = kitRequestWithSources(
            source("failed", 2, 64, 0),
            source("next", 8, 64, 0)
        );
        request.gather.skippedSourceIndexes.add(0);

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            new net.minecraft.util.math.BlockPos(0, 64, 0),
            4.5,
            (source, pos) -> false
        ).orElseThrow();

        assertEquals(1, selected, "kitbot gather skips failed source and selects next closest");
    }

    private static void suppressesFailedKitbotSourceNeighborhoodButAllowsUnrelatedSources() {
        KitRequest request = kitRequestWithSources(
            source("suppressed-a", 10, 64, 10),
            source("suppressed-b", 10, 66, 10),
            source("other", 20, 64, 10)
        );
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        throttle.recordPathFailure(target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10)), playerPos);
        throttle.recordPathFailure(target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10)), playerPos);

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            playerPos,
            4.5,
            (source, pos) -> throttle.suppresses(target(source.containerId(), source.interactionPos()), pos)
        ).orElseThrow();

        assertEquals(2, selected, "kitbot gather skips suppressed neighborhood and chooses unrelated source");
    }

    private static void reconsidersSuppressedKitbotSourceNeighborhoodAfterMovement() {
        KitRequest request = kitRequestWithSources(
            source("suppressed-a", 10, 64, 10),
            source("suppressed-b", 10, 66, 10),
            source("other", 20, 64, 10)
        );
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        throttle.recordPathFailure(target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10)), new net.minecraft.util.math.BlockPos(0, 64, 0));
        throttle.recordPathFailure(target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10)), new net.minecraft.util.math.BlockPos(0, 64, 0));

        int selected = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            new net.minecraft.util.math.Vec3d(13, 65, 0),
            new net.minecraft.util.math.BlockPos(13, 64, 0),
            4.5,
            (source, pos) -> throttle.suppresses(target(source.containerId(), source.interactionPos()), pos)
        ).orElseThrow();

        assertEquals(0, selected, "kitbot gather reconsiders suppressed source neighborhood after movement");
    }

    private static void updatesCachedContainerAndItemIndex() {
        CacheFile updated = StashInventoryCache.withUpdatedContainer(
            cacheFile(),
            container("a", new PositionRecord(10, 64, 10), List.of(shulker(1, "Red EChest", 4))),
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals("2026-06-11T13:00:00Z", updated.updatedAt(), "updated cache timestamp");
        assertEquals(3, updated.containers().size(), "container count preserved");
        assertEquals(List.of(shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "updated source contents");
        assertEquals(18, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index rebuilt without stale taken shulkers");
    }

    private static void depletesCachedSourceSlotAfterConfirmedTransfer() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            cacheFile(),
            "a",
            0,
            "blue echest",
            3,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(List.of(shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "depleted slot is removed from cached container");
        assertEquals(18, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index excludes depleted transferred shulkers");
        assertEquals(List.of(2), new StashKitbotStockPlanner().buildSources(updated, "blue echest").getFirst().slots(), "future source plans no longer include depleted slot");
    }

    private static void decrementsCachedSourceSlotStackAfterPartialTransfer() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            cacheFile(),
            "a",
            0,
            "blue echest",
            1,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(shulker(0, "Blue EChest", 2), updated.containers().getFirst().items().getFirst(), "cached source slot is decremented when only part of the stack transferred");
        assertEquals(20, updated.itemIndex().get("minecraft:shulker_box").totalCount, "item index reflects decremented cached stack");
    }

    private static void keepsCacheWhenDepletedSourceSlotDoesNotMatch() {
        CacheFile existing = cacheFile();
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            existing,
            "a",
            1,
            "blue echest",
            1,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertTrue(existing == updated, "non-matching depleted slot leaves cache unchanged");
        assertEquals(List.of(shulker(0, "Blue EChest", 3), shulker(1, "Red EChest", 4)), updated.containers().getFirst().items(), "non-matching slot contents are preserved");
    }

    private static void depletesPureEchestCachedSourceSlot() {
        CacheFile updated = StashInventoryCache.withDepletedSourceSlot(
            echestContentCacheFile(),
            "echest-a",
            0,
            "echest",
            2,
            Instant.parse("2026-06-11T13:00:00Z")
        );

        assertEquals(List.of(shulker(1, "Blue EChest", 4)), updated.containers().getFirst().items(), "echest alias depletes only pure echest shulkers");
        assertEquals(List.of(2), new StashKitbotStockPlanner().buildSources(updated, "echest").getFirst().slots(), "future echest source plans skip depleted pure echest slot");
    }

    private static void groupsScannerTopShulkersByNormalizedName() {
        StashScanSession session = new StashScanSession();
        session.loadCached(fancyCacheFile());

        StashScanner.InventoryStats stats = StashScannerStats.snapshot(session, true);

        assertEquals(
            List.of(
                new StashScanner.ShulkerNameCount("the watchmen's kit", 4),
                new StashScanner.ShulkerNameCount("the watchmen kit", 3)
            ),
            stats.topShulkers(),
            "scanner top shulkers use canonical normalized names"
        );
    }

    private static void cachesScannerStatsUntilSessionContentChanges() {
        StashScanSession session = new StashScanSession();
        session.loadCached(fancyCacheFile());

        StashScanner.InventoryStats first = StashScannerStats.snapshot(session, true);
        StashScanner.InventoryStats second = StashScannerStats.snapshot(session, true);
        assertTrue(first == second, "scanner stats snapshot is cached while content is unchanged");

        session.markScanned(container(
            "fancy-c",
            new PositionRecord(30, 64, 30),
            List.of(shulker(0, "The Watchmen Kit", 5))
        ));

        StashScanner.InventoryStats updated = StashScannerStats.snapshot(session, true);
        assertFalse(first == updated, "scanner stats snapshot refreshes after scanned content changes");
        assertEquals(
            List.of(new StashScanner.ShulkerNameCount("the watchmen kit", 8), new StashScanner.ShulkerNameCount("the watchmen's kit", 4)),
            updated.topShulkers(),
            "scanner top shulker counts update after content mutation"
        );
    }

    private static void dedupesLoadedContainersById() {
        StashScanSession session = new StashScanSession();
        session.loadCached(new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("same", new PositionRecord(1, 64, 1), List.of(shulker(0, "Old", 1))),
                container("same", new PositionRecord(1, 64, 1), List.of(shulker(0, "New", 2)))
            ),
            Map.of(),
            List.of()
        ));

        assertEquals(1, session.containers().size(), "deduped loaded containers");
        assertEquals("New", session.containers().getFirst().items().getFirst().displayName(), "latest loaded container wins");
    }

    private static void removesStaleContainersWhenSkipped() {
        StashScanSession session = new StashScanSession();
        PositionRecord pos = new PositionRecord(1, 64, 1);
        session.loadCached(new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(container("minecraft:overworld:1,64,1", pos, List.of(shulker(0, "Old", 1)))),
            Map.of(),
            List.of()
        ));

        StashTarget target = new StashTarget(
            "minecraft:overworld:1,64,1",
            "minecraft:shulker_box",
            List.of(new net.minecraft.util.math.BlockPos(1, 64, 1)),
            new net.minecraft.util.math.BlockPos(1, 64, 1),
            27
        );

        assertTrue(session.markSkipped(target, "blocked-opening"), "new skip recorded");
        assertEquals(0, session.containers().size(), "skipped removes stale container");
        assertEquals(1, session.skipped().size(), "skip recorded");
    }

    private static void clearsAlreadyKnownSkippedTarget() {
        StashScanSession session = new StashScanSession();
        StashTarget target = new StashTarget(
            "minecraft:overworld:1,64,1",
            "minecraft:chest",
            List.of(new net.minecraft.util.math.BlockPos(1, 64, 1)),
            new net.minecraft.util.math.BlockPos(1, 64, 1),
            27
        );

        assertTrue(session.markSkipped(target, StashSkipReasons.PATH_TIMEOUT), "first skip recorded");
        session.current(target);
        session.phase(StashScanPhase.PATHING);

        assertFalse(session.markSkipped(target, StashSkipReasons.PATH_TIMEOUT), "duplicate skip not recorded");
        assertNull(session.current(), "duplicate skip clears current target");
        assertEquals(StashScanPhase.IDLE, session.phase(), "duplicate skip returns to idle");
        assertEquals(1, session.skipped().size(), "duplicate skip does not append");
    }

    private static void doesNotQueueCachedSkippedTargets() {
        StashScanSession session = new StashScanSession();
        StashTarget target = target("minecraft:overworld:1,64,1");
        session.loadCached(new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(),
            Map.of(),
            List.of(new StashSkippedContainer(target.id(), target.type(), StashInventoryCache.positions(target.positions()), StashSkipReasons.PATH_TIMEOUT))
        ));

        int queued = session.queueTargets(new java.util.ArrayList<>(List.of(target)), new net.minecraft.util.math.Vec3d(0, 64, 0));
        assertEquals(0, queued, "cached skipped target not requeued");
        assertTrue(session.isCompleted(target.id()), "cached skipped target completed for scan");
    }

    private static void plansScannerNearestTargetOrdering() {
        StashScanSession session = new StashScanSession();
        StashTarget near = target("minecraft:overworld:2,64,1", new net.minecraft.util.math.BlockPos(2, 64, 1));
        StashTarget far = target("minecraft:overworld:30,64,1", new net.minecraft.util.math.BlockPos(30, 64, 1));

        int queued = session.queueTargets(new java.util.ArrayList<>(List.of(far, near)), new net.minecraft.util.math.Vec3d(0, 65, 0));

        assertEquals(2, queued, "simple scanner targets queued");
        assertEquals(near, session.pollNearestTarget(new net.minecraft.util.math.Vec3d(0, 65, 0), new net.minecraft.util.math.BlockPos(0, 64, 0)), "nearest target wins in simple flat stash");
    }

    private static void plansScannerAisleFriendlyOrdering() {
        StashScannerTargetPlanner planner = new StashScannerTargetPlanner();
        StashTarget sameAisle = target("minecraft:overworld:2,64,18", new net.minecraft.util.math.BlockPos(2, 64, 18));
        StashTarget crossAisle = target("minecraft:overworld:8,64,12", new net.minecraft.util.math.BlockPos(8, 64, 12));

        StashTarget selected = planner.chooseNext(
            List.of(crossAisle, sameAisle),
            new net.minecraft.util.math.Vec3d(0, 65, 0),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        ).orElseThrow();

        assertEquals(sameAisle, selected, "same aisle scanner target is preferred before crossing stash rows");
    }

    private static void penalizesScannerTimeoutNeighborhoodsConservatively() {
        StashScannerTargetPlanner planner = new StashScannerTargetPlanner();
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        StashTarget timedOut = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));
        StashTarget nearbyColumn = target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10));
        StashTarget differentColumn = target("minecraft:overworld:18,64,10", new net.minecraft.util.math.BlockPos(18, 64, 10));

        planner.recordPathTimeout(timedOut, playerPos);

        assertTrue(planner.hasActiveTimeoutPenalty(nearbyColumn, playerPos), "nearby same column gets a temporary penalty");
        assertFalse(planner.hasActiveTimeoutPenalty(differentColumn, playerPos), "different column is not penalized");
    }

    private static void reconsidersScannerTimeoutNeighborhoodsAfterMovement() {
        StashScannerTargetPlanner planner = new StashScannerTargetPlanner();
        StashTarget timedOut = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));
        StashTarget nearbyColumn = target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10));

        planner.recordPathTimeout(timedOut, new net.minecraft.util.math.BlockPos(0, 64, 0));

        assertTrue(planner.hasActiveTimeoutPenalty(nearbyColumn, new net.minecraft.util.math.BlockPos(0, 64, 0)), "timeout penalty starts active");
        assertFalse(planner.hasActiveTimeoutPenalty(nearbyColumn, new net.minecraft.util.math.BlockPos(13, 64, 0)), "horizontal movement reconsiders timeout neighborhood");
    }

    private static void scannerPathTimeoutSkipWireBehaviorIsUnchanged() {
        StashScanSession session = new StashScanSession();
        StashTarget target = target("minecraft:overworld:1,64,1");

        session.recordPathTimeout(target, new net.minecraft.util.math.BlockPos(0, 64, 0));

        assertTrue(session.markSkipped(target, StashSkipReasons.PATH_TIMEOUT), "path timeout skip is still recorded explicitly");
        assertEquals(StashSkipReasons.PATH_TIMEOUT, session.skipped().getFirst().reason(), "path timeout wire reason remains unchanged");
        assertTrue(session.isCompleted(target.id()), "timed out target is still completed after skip");
    }

    private static void resetsScannerPathProgressPerTarget() {
        StashScannerPathProgress progress = new StashScannerPathProgress();
        StashTarget first = target("minecraft:overworld:20,64,1", new net.minecraft.util.math.BlockPos(20, 64, 1));
        StashTarget second = target("minecraft:overworld:40,64,1", new net.minecraft.util.math.BlockPos(40, 64, 1));
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        progress.reset(first, playerPos, eyes);
        tickNoProgress(progress, first, playerPos, eyes, StashScannerPathProgress.GRACE_TICKS + 20, true);

        assertEquals(
            StashScannerPathProgress.FastFailDecision.WAIT,
            progress.tick(second, playerPos, eyes, true),
            "new scanner target resets path progress"
        );
    }

    private static void scannerPathProgressAllowsDistanceImprovement() {
        StashScannerPathProgress progress = new StashScannerPathProgress();
        StashTarget target = target("minecraft:overworld:40,64,0", new net.minecraft.util.math.BlockPos(40, 64, 0));
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        progress.reset(target, playerPos, new net.minecraft.util.math.Vec3d(0, 65, 0));

        StashScannerPathProgress.FastFailDecision decision = StashScannerPathProgress.FastFailDecision.WAIT;
        for (int i = 0; i < StashScannerPathProgress.GRACE_TICKS + StashScannerPathProgress.NO_PROGRESS_TIMEOUT_TICKS + 20; i++) {
            decision = progress.tick(target, playerPos, new net.minecraft.util.math.Vec3d(i * 0.25, 65, 0), true);
        }

        assertEquals(StashScannerPathProgress.FastFailDecision.WAIT, decision, "steady distance improvement prevents scanner fast-fail");
    }

    private static void scannerPathProgressAllowsBlockMovement() {
        StashScannerPathProgress progress = new StashScannerPathProgress();
        StashTarget target = target("minecraft:overworld:40,64,0", new net.minecraft.util.math.BlockPos(40, 64, 0));
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);
        progress.reset(target, new net.minecraft.util.math.BlockPos(0, 64, 0), eyes);

        StashScannerPathProgress.FastFailDecision decision = StashScannerPathProgress.FastFailDecision.WAIT;
        for (int i = 0; i < StashScannerPathProgress.GRACE_TICKS + StashScannerPathProgress.NO_PROGRESS_TIMEOUT_TICKS + 20; i++) {
            net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(i, 64, 0);
            decision = progress.tick(target, playerPos, eyes, true);
        }

        assertEquals(StashScannerPathProgress.FastFailDecision.WAIT, decision, "block movement prevents scanner fast-fail");
    }

    private static void scannerPathProgressFastFailsNoBaritonePath() {
        StashScannerPathProgress progress = new StashScannerPathProgress();
        StashTarget target = target("minecraft:overworld:40,64,0", new net.minecraft.util.math.BlockPos(40, 64, 0));
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);
        progress.reset(target, playerPos, eyes);

        StashScannerPathProgress.FastFailDecision decision = tickNoProgress(
            progress,
            target,
            playerPos,
            eyes,
            StashScannerPathProgress.NO_PATH_TIMEOUT_TICKS,
            false
        );

        assertEquals(StashScannerPathProgress.FastFailDecision.FAST_FAIL, decision, "scanner fast-fails quickly when Baritone has no path");
    }

    private static void scannerPathProgressFastFailsNoProgress() {
        StashScannerPathProgress progress = new StashScannerPathProgress();
        StashTarget target = target("minecraft:overworld:40,64,0", new net.minecraft.util.math.BlockPos(40, 64, 0));
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);
        progress.reset(target, playerPos, eyes);

        StashScannerPathProgress.FastFailDecision decision = tickNoProgress(
            progress,
            target,
            playerPos,
            eyes,
            StashScannerPathProgress.GRACE_TICKS + StashScannerPathProgress.NO_PROGRESS_TIMEOUT_TICKS,
            true
        );

        assertEquals(StashScannerPathProgress.FastFailDecision.FAST_FAIL, decision, "scanner fast-fails after sustained no progress");
    }

    private static void scannerFastFailKeepsPathTimeoutSkipSemantics() {
        StashScanSession session = new StashScanSession();
        StashTarget target = target("minecraft:overworld:1,64,1");
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        session.beginPathProgress(target, playerPos, eyes);
        boolean failed = false;
        for (int i = 0; i < StashScannerPathProgress.GRACE_TICKS + StashScannerPathProgress.NO_PROGRESS_TIMEOUT_TICKS; i++) {
            failed = session.tickPathProgressFailed(target, playerPos, eyes, true);
        }
        session.recordPathTimeout(target, playerPos);

        assertTrue(failed, "session path progress reports fast-fail");
        assertTrue(session.markSkipped(target, StashSkipReasons.PATH_TIMEOUT), "fast-failed target records normal path timeout skip");
        assertEquals(StashSkipReasons.PATH_TIMEOUT, session.skipped().getFirst().reason(), "fast-fail skip reason remains path-timeout");
    }

    private static void allowsScannerPathStartBurst() {
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();

        for (int i = 0; i < StashScannerPathThrottle.BURST_LIMIT; i++) {
            assertEquals(StashScannerPathThrottle.PathStartDecision.ALLOWED, throttle.beforePathStart(), "scanner path burst starts allowed");
            throttle.recordPathStart();
        }
    }

    private static void throttlesScannerPathStartsAfterBurst() {
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();

        for (int i = 0; i < StashScannerPathThrottle.BURST_LIMIT; i++) {
            assertEquals(StashScannerPathThrottle.PathStartDecision.ALLOWED, throttle.beforePathStart(), "initial scanner path starts allowed");
            throttle.recordPathStart();
        }

        assertEquals(StashScannerPathThrottle.PathStartDecision.THROTTLED, throttle.beforePathStart(), "scanner path starts throttle after burst");
    }

    private static void restoresScannerPathStartsAfterCooldown() {
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();

        for (int i = 0; i < StashScannerPathThrottle.BURST_LIMIT; i++) {
            assertEquals(StashScannerPathThrottle.PathStartDecision.ALLOWED, throttle.beforePathStart(), "initial scanner path starts allowed");
            throttle.recordPathStart();
        }
        assertEquals(StashScannerPathThrottle.PathStartDecision.THROTTLED, throttle.beforePathStart(), "scanner path throttle begins");
        for (int i = 0; i < StashScannerPathThrottle.COOLDOWN_TICKS + StashScannerPathThrottle.WINDOW_TICKS; i++) {
            throttle.beforePathStart();
        }

        assertEquals(StashScannerPathThrottle.PathStartDecision.ALLOWED, throttle.beforePathStart(), "scanner path throttle recovers after cooldown/window");
    }

    private static void suppressesRepeatedScannerFailureNeighborhood() {
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        StashTarget failed = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));
        StashTarget related = target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10));
        StashTarget unrelated = target("minecraft:overworld:20,64,10", new net.minecraft.util.math.BlockPos(20, 64, 10));

        throttle.recordPathFailure(failed, playerPos);
        assertFalse(throttle.suppresses(related, playerPos), "single scanner failure does not suppress neighborhood");
        throttle.recordPathFailure(related, playerPos);

        assertTrue(throttle.suppresses(related, playerPos), "repeated scanner failures suppress related neighborhood");
        assertFalse(throttle.suppresses(unrelated, playerPos), "scanner suppression does not affect unrelated columns");
    }

    private static void reconsidersScannerSuppressionAfterMovement() {
        StashScannerPathThrottle throttle = new StashScannerPathThrottle();
        StashTarget failed = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));
        StashTarget related = target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10));

        throttle.recordPathFailure(failed, new net.minecraft.util.math.BlockPos(0, 64, 0));
        throttle.recordPathFailure(related, new net.minecraft.util.math.BlockPos(0, 64, 0));

        assertTrue(throttle.suppresses(related, new net.minecraft.util.math.BlockPos(0, 64, 0)), "scanner suppression starts active");
        assertFalse(throttle.suppresses(related, new net.minecraft.util.math.BlockPos(13, 64, 0)), "scanner suppression clears after movement");
    }

    private static void keepsSuppressedScannerTargetsQueuedWhileSelectingOtherTargets() {
        StashScanSession session = new StashScanSession();
        net.minecraft.util.math.BlockPos playerPos = new net.minecraft.util.math.BlockPos(0, 64, 0);
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);
        StashTarget failed = target("minecraft:overworld:10,64,10", new net.minecraft.util.math.BlockPos(10, 64, 10));
        StashTarget suppressed = target("minecraft:overworld:10,66,10", new net.minecraft.util.math.BlockPos(10, 66, 10));
        StashTarget other = target("minecraft:overworld:20,64,10", new net.minecraft.util.math.BlockPos(20, 64, 10));

        session.recordPathTimeout(failed, playerPos);
        session.recordPathTimeout(suppressed, playerPos);
        session.queueTargets(new java.util.ArrayList<>(List.of(suppressed, other)), eyes);

        assertEquals(other, session.pollNearestTarget(eyes, playerPos), "scanner chooses unsuppressed target before suppressed target");
        assertEquals(1, session.queuedCount(), "suppressed scanner target remains queued");
    }

    private static void selectsRollingNearbyReachableScannerTarget() {
        StashScanSession session = new StashScanSession();
        StashTarget reachable = target("minecraft:overworld:2,64,1", new net.minecraft.util.math.BlockPos(2, 64, 1));
        StashTarget fartherReachable = target("minecraft:overworld:3,64,1", new net.minecraft.util.math.BlockPos(3, 64, 1));
        StashTarget outOfRange = target("minecraft:overworld:20,64,1", new net.minecraft.util.math.BlockPos(20, 64, 1));
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        session.queueTargets(new java.util.ArrayList<>(List.of(outOfRange, fartherReachable, reachable)), eyes);

        assertEquals(
            reachable,
            session.pollReachableQueuedTarget(eyes, 4.5, 12).orElseThrow(),
            "rolling nearby scanner selector picks closest reachable queued target"
        );
    }

    private static void ignoresOutOfRangeRollingScannerTargets() {
        StashScanSession session = new StashScanSession();
        StashTarget outOfRange = target("minecraft:overworld:20,64,1", new net.minecraft.util.math.BlockPos(20, 64, 1));
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        session.queueTargets(new java.util.ArrayList<>(List.of(outOfRange)), eyes);

        assertTrue(session.pollReachableQueuedTarget(eyes, 4.5, 12).isEmpty(), "rolling nearby scanner selector ignores out-of-range targets");
        assertEquals(1, session.queuedCount(), "out-of-range target remains queued");
    }

    private static void requeuesInterruptedScannerPathTarget() {
        StashScanSession session = new StashScanSession();
        StashTarget pathTarget = target("minecraft:overworld:20,64,1", new net.minecraft.util.math.BlockPos(20, 64, 1));
        StashTarget reachable = target("minecraft:overworld:2,64,1", new net.minecraft.util.math.BlockPos(2, 64, 1));
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        session.current(pathTarget);
        session.queueTargets(new java.util.ArrayList<>(List.of(reachable)), eyes);
        StashTarget nearby = session.pollReachableQueuedTarget(eyes, 4.5, 12).orElseThrow();
        session.requeueCurrentTarget();
        session.current(nearby);

        assertEquals(reachable, session.current(), "nearby target becomes current scanner target");
        assertEquals(1, session.queuedCount(), "interrupted path target is requeued");
        assertEquals(pathTarget, session.pollNearestTarget(eyes, new net.minecraft.util.math.BlockPos(0, 64, 0)), "requeued path target remains available");
    }

    private static void preservesInterruptedTargetAfterNearbySkip() {
        StashScanSession session = new StashScanSession();
        StashTarget pathTarget = target("minecraft:overworld:20,64,1", new net.minecraft.util.math.BlockPos(20, 64, 1));
        StashTarget staleNearby = target("minecraft:overworld:2,64,1", new net.minecraft.util.math.BlockPos(2, 64, 1));
        net.minecraft.util.math.Vec3d eyes = new net.minecraft.util.math.Vec3d(0, 65, 0);

        session.current(pathTarget);
        session.queueTargets(new java.util.ArrayList<>(List.of(staleNearby)), eyes);
        StashTarget nearby = session.pollReachableQueuedTarget(eyes, 4.5, 12).orElseThrow();
        session.requeueCurrentTarget();
        session.current(nearby);

        assertTrue(session.markSkipped(nearby, StashSkipReasons.CHANGED_OR_MISSING), "nearby stale target uses normal skip semantics");
        assertEquals(1, session.queuedCount(), "interrupted path target remains queued after nearby skip");
        assertEquals(pathTarget, session.pollNearestTarget(eyes, new net.minecraft.util.math.BlockPos(0, 64, 0)), "interrupted target can be retried after nearby skip");
    }

    private static void retriesScannerOpenWhenCallbackNeverFires() {
        StashScannerOpenAttempts attempts = new StashScannerOpenAttempts();
        StashTarget target = target("minecraft:overworld:1,64,1");
        attempts.start(target);
        attempts.markAttemptSent(target);

        StashScannerOpenAttempts.Decision decision = tickOpenAttempts(attempts, StashScannerOpenAttempts.CALLBACK_WAIT_TICKS);

        assertEquals(StashScannerOpenAttempts.Decision.RETRY, decision, "scanner open retries when rotation callback never fires");
    }

    private static void retriesScannerOpenWhenInteractionRejected() {
        StashScannerOpenAttempts attempts = new StashScannerOpenAttempts();
        StashTarget target = target("minecraft:overworld:1,64,1");
        attempts.start(target);
        attempts.markAttemptSent(target);
        attempts.recordInteraction(new StashScannerOpenAttempts.InteractionResult(true, false));

        StashScannerOpenAttempts.Decision decision = tickOpenAttempts(attempts, StashScannerOpenAttempts.RETRY_DELAY_TICKS);

        assertEquals(StashScannerOpenAttempts.Decision.RETRY, decision, "scanner open retries rejected interactions");
    }

    private static void failsScannerOpenAfterRetryBudget() {
        StashScannerOpenAttempts attempts = new StashScannerOpenAttempts();
        StashTarget target = target("minecraft:overworld:1,64,1");
        attempts.start(target);

        StashScannerOpenAttempts.Decision decision = StashScannerOpenAttempts.Decision.WAIT;
        for (int i = 0; i < StashScannerOpenAttempts.MAX_ATTEMPTS; i++) {
            attempts.markAttemptSent(target);
            attempts.recordInteraction(new StashScannerOpenAttempts.InteractionResult(true, false));
            decision = tickOpenAttempts(attempts, StashScannerOpenAttempts.RETRY_DELAY_TICKS);
        }

        assertEquals(StashScannerOpenAttempts.Decision.FAIL, decision, "scanner open fails after retry budget");
    }

    private static void clearsScannerOpenAttemptsAfterCurrentClear() {
        StashScanSession session = new StashScanSession();
        StashTarget target = target("minecraft:overworld:1,64,1");

        session.current(target);
        session.beginOpenAttempts(target);
        session.markOpenAttemptSent(target);
        session.clearCurrent();

        assertEquals(StashScannerOpenAttempts.Decision.RETRY, session.tickOpenAttemptDecision(), "cleared scanner open attempts are ready for a fresh target");
    }

    private static void appliesShulkerScanSkipToggle() {
        assertTrue(StashTargetDiscovery.shouldSkipScanTarget(true, true), "enabled toggle skips placed shulkers");
        assertFalse(StashTargetDiscovery.shouldSkipScanTarget(true, false), "disabled toggle keeps placed shulkers eligible");
        assertFalse(StashTargetDiscovery.shouldSkipScanTarget(false, true), "enabled toggle does not skip non-shulker containers");
    }

    private static StashTarget target(String id) {
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(1, 64, 1);
        return target(id, pos);
    }

    private static StashTarget target(String id, net.minecraft.util.math.BlockPos pos) {
        return new StashTarget(id, "minecraft:chest", List.of(pos), pos, 27);
    }

    private static KitSource source(String id, int x, int y, int z) {
        return new KitSource(id, new net.minecraft.util.math.BlockPos(x, y, z), List.of(0), 1);
    }

    private static KitRequest kitRequestWithSources(KitSource... sources) {
        return new KitRequest(
            "Alice",
            "Blue EChest",
            "blue echest",
            1,
            List.of(sources),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );
    }

    private static StashScannerPathProgress.FastFailDecision tickNoProgress(
        StashScannerPathProgress progress,
        StashTarget target,
        net.minecraft.util.math.BlockPos playerPos,
        net.minecraft.util.math.Vec3d eyes,
        int ticks,
        boolean baritoneHasPath
    ) {
        StashScannerPathProgress.FastFailDecision decision = StashScannerPathProgress.FastFailDecision.WAIT;
        for (int i = 0; i < ticks; i++) {
            decision = progress.tick(target, playerPos, eyes, baritoneHasPath);
        }
        return decision;
    }

    private static StashScannerOpenAttempts.Decision tickOpenAttempts(StashScannerOpenAttempts attempts, int ticks) {
        StashScannerOpenAttempts.Decision decision = StashScannerOpenAttempts.Decision.WAIT;
        for (int i = 0; i < ticks; i++) {
            decision = attempts.tick();
        }
        return decision;
    }

    private static void expiresTimerByElapsedTime() {
        TickTimer timer = new TickTimer();
        timer.reset(1);
        sleep(75);

        assertTrue(timer.tickOrElapsedExpired(), "timer expires by elapsed wall time");
    }

    private static void keepsSkipReasonWireValues() {
        assertEquals("changed-or-missing", StashSkipReasons.CHANGED_OR_MISSING, "changed skip reason");
        assertEquals("path-timeout", StashSkipReasons.PATH_TIMEOUT, "path timeout skip reason");
        assertEquals("unexpected-screen", StashSkipReasons.UNEXPECTED_SCREEN, "unexpected screen skip reason");
        assertEquals("open-timeout", StashSkipReasons.OPEN_TIMEOUT, "open timeout skip reason");
        assertEquals("closed-screen", StashSkipReasons.CLOSED_SCREEN, "closed screen skip reason");
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

    private static CacheFile cacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "Blue EChest", 3),
                    shulker(1, "Red EChest", 4)
                )),
                container("b", new PositionRecord(20, 64, 20), List.of(
                    shulker(2, "Blue EChest", 5),
                    item(3, "minecraft:stone", "Stone", 64)
                )),
                container("c", new PositionRecord(30, 64, 30), List.of(
                    shulker(4, "Green Kit", 9)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    private static CacheFile echestContentCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("echest-a", new PositionRecord(10, 64, 10), List.of(
                    pureEchestShulker(0, "Purple Shulker Box", 2, 54),
                    shulker(1, "Blue EChest", 4)
                )),
                container("echest-b", new PositionRecord(20, 64, 20), List.of(
                    pureEchestShulker(2, "Tools", 3, 81),
                    item(3, "minecraft:stone", "Stone", 64)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    private static CacheFile fancyCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("fancy-a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░", 4)
                )),
                container("fancy-b", new PositionRecord(20, 64, 20), List.of(
                    shulker(2, "The Watchmen Kit", 3)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    private static CacheFile fancyPossessiveCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(container("fancy-a", new PositionRecord(10, 64, 10), List.of(
                shulker(0, "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░", 4)
            ))),
            Map.of(),
            List.of()
        );
    }

    private static CacheFile phraseCacheFile() {
        PositionRecord pos = new PositionRecord(10, 64, 10);
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(container("phrase-a", pos, List.of(
                shulker(0, "Watchmen PvP Kit", 9),
                shulker(1, "The Watchmen Kit", 3)
            ))),
            Map.of(),
            List.of()
        );
    }

    private static CacheFile watchmenVariantCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("watchmen-a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "The Watchmen", 1),
                    shulker(1, "The Watchmen's Kit", 4)
                )),
                container("watchmen-b", new PositionRecord(20, 64, 20), List.of(
                    shulker(0, "The Watchmen's EChest's", 2),
                    shulker(1, "The Watchmen's Kit", 6)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    private static StashCachedContainer container(String id, PositionRecord pos, List<StashCachedItem> items) {
        return new StashCachedContainer(id, "minecraft:chest", 27, List.of(pos), pos, "now", items);
    }

    private static StashCachedItem shulker(int slot, String displayName, int count) {
        return new StashCachedItem(slot, "minecraft:shulker_box", displayName, count, "{}", true, false, 0);
    }

    private static StashCachedItem pureEchestShulker(int slot, String displayName, int count, int containedEnderChests) {
        return new StashCachedItem(slot, "minecraft:shulker_box", displayName, count, "{}", true, true, containedEnderChests);
    }

    private static StashCachedItem item(int slot, String itemId, String displayName, int count) {
        return new StashCachedItem(slot, itemId, displayName, count, "{}", false, false, 0);
    }

    private static PositionRecord toPosition(net.minecraft.util.math.BlockPos pos) {
        return new PositionRecord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static QueuedKitRequest queued(String requester, String kitName, int count) {
        String normalized = StashKitbotAccessPlanner.normalize(requester);
        return new QueuedKitRequest(
            new KitbotRequesterAccess(requester, normalized, KitbotTier.TIER_1, 1200),
            new KitCommand(kitName, count)
        );
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError("%s expected <%s> but got <%s>".formatted(label, expected, actual));
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void assertFalse(boolean value, String label) {
        if (value) throw new AssertionError(label);
    }

    private static void assertNull(Object value, String label) {
        if (value != null) throw new AssertionError("%s expected null but got <%s>".formatted(label, value));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("sleep interrupted", exception);
        }
    }
}
