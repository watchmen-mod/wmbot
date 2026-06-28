package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertFalse;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;

final class StashKitbotDeliveryPlannerPureTest {
    private StashKitbotDeliveryPlannerPureTest() {
    }

    static void run() {
        parsesCooldownDurations();
        ignoresTeleportCountdownAsCooldown();
        plansReliableDeliveryPositioning();
        plansResilientDeliveryPositionFallback();
        plansCrossDimensionTeleportDetection();
        plansRequesterReacquirePolicy();
        plansRequesterSearchReadiness();
        plansDeliveryMovementWatchdog();
        plansDeliveryCommandPolicy();
        plansKillHomeRespawnPolicy();
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

    private static void plansDeliveryMovementWatchdog() {
        assertTrue(
            StashKitbotDeliveryPlanner.deliveryMovementProgressed(1.0, 0.0, false),
            "meaningful player movement resets delivery stuck tracking"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.deliveryMovementProgressed(0.0, 2.0, false),
            "requester distance improvement resets delivery stuck tracking"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.deliveryMovementProgressed(0.0, -2.0, false),
            "requester distance regression does not reset delivery stuck tracking"
        );
        assertTrue(
            StashKitbotDeliveryPlanner.deliveryMovementProgressed(0.0, 0.0, true),
            "delivery spot change resets delivery stuck tracking"
        );
        assertFalse(
            StashKitbotDeliveryPlanner.deliveryMovementProgressed(0.0, 0.0, false),
            "no movement, distance change, or spot change counts as no progress"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryStuckDecision.TRACK,
            StashKitbotDeliveryPlanner.deliveryStuckDecision(true, 80, 80, 0, true),
            "progress keeps watchdog tracking even at threshold"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryStuckDecision.TRACK,
            StashKitbotDeliveryPlanner.deliveryStuckDecision(false, 79, 80, 0, true),
            "watchdog waits until stuck threshold"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryStuckDecision.RESET_MOVEMENT,
            StashKitbotDeliveryPlanner.deliveryStuckDecision(false, 80, 80, 0, true),
            "first stuck detection resets movement"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryStuckDecision.THROW_NOW,
            StashKitbotDeliveryPlanner.deliveryStuckDecision(false, 80, 80, 1, true),
            "repeated stuck with visible requester falls back to throwing"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.DeliveryStuckDecision.REACQUIRE_REQUESTER,
            StashKitbotDeliveryPlanner.deliveryStuckDecision(false, 80, 80, 1, false),
            "repeated stuck without visible requester reacquires"
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
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRetryDecision.WAIT,
            StashKitbotDeliveryPlanner.tpaRetryDecision(true, false, false, 1, 4),
            "detected teleport suppresses tpa retry"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRetryDecision.WAIT,
            StashKitbotDeliveryPlanner.tpaRetryDecision(false, true, false, 1, 4),
            "pending cooldown suppresses tpa retry"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRetryDecision.WAIT,
            StashKitbotDeliveryPlanner.tpaRetryDecision(false, false, true, 1, 4),
            "active retry delay waits before resending tpa"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRetryDecision.RESEND_TPA,
            StashKitbotDeliveryPlanner.tpaRetryDecision(false, false, false, 1, 4),
            "uncooldowned wait can resend tpa before cap"
        );
        assertEquals(
            StashKitbotDeliveryPlanner.TpaRetryDecision.FAIL_HOME,
            StashKitbotDeliveryPlanner.tpaRetryDecision(false, false, false, 4, 4),
            "tpa retry cap fails home cleanly"
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
}
