package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneReplenishPureTest {
    private PlaneReplenishPureTest() {
    }

    static void run() {
        computesRequiredEnderChestsForReplenishTarget();
        computesAdditionalEnderChestsForReplenishTarget();
        clampsEffectiveReplenishTarget();
        computesSafeReplenishTargetPolicy();
        sharesSafeReplenishTargetAcrossWorkflows();
        tracksPendingEnderChestFarmProgress();
        PlaneReplenishManagedShulkerPureTest.run();
        classifiesServiceHoleStatuses();
        plansReplenishSourceTransitions();
        plansMissingEnderChestSourceRecovery();
        plansEnderChestInventoryRecovery();
        plansEnderChestBreakRecovery();
        tracksEnderChestBreakWatchdog();
        plansEnderChestPlacementTransitions();
        plansServiceHoleCloseTransitions();
        PlaneReplenishCleanupPureTest.run();
        plansServiceHoleReadiness();
        plansMissingObsidianRecovery();
        tracksReplenishActivePhases();
        tracksPhasePolicy();
        preservesActiveReplenishWhileKitbotRefillIsPending();
    }

    private static void computesRequiredEnderChestsForReplenishTarget() {
        assertEquals(1, PlaneInventoryQueries.requiredEnderChestsForTarget(128, 128), "target met still keeps one ender chest as minimum");
        assertEquals(1, PlaneInventoryQueries.requiredEnderChestsForTarget(121, 128), "seven obsidian short needs one ender chest");
        assertEquals(1, PlaneInventoryQueries.requiredEnderChestsForTarget(120, 128), "eight obsidian short needs one ender chest");
        assertEquals(2, PlaneInventoryQueries.requiredEnderChestsForTarget(119, 128), "nine obsidian short needs two ender chests");
        assertEquals(12, PlaneInventoryQueries.requiredEnderChestsForTarget(32, 128), "large shortfall rounds up by eight obsidian per ender chest");
    }

    private static void computesAdditionalEnderChestsForReplenishTarget() {
        assertEquals(0, PlaneInventoryQueries.additionalEnderChestsForTarget(128, 128), "target met needs no additional ender chests");
        assertEquals(1, PlaneInventoryQueries.additionalEnderChestsForTarget(121, 128), "seven obsidian short needs one additional ender chest");
        assertEquals(1, PlaneInventoryQueries.additionalEnderChestsForTarget(120, 128), "eight obsidian short needs one additional ender chest");
        assertEquals(2, PlaneInventoryQueries.additionalEnderChestsForTarget(119, 128), "nine obsidian short needs two additional ender chests");
        assertEquals(12, PlaneInventoryQueries.additionalEnderChestsForTarget(32, 128), "large shortfall rounds up by eight obsidian per ender chest");
    }

    private static void clampsEffectiveReplenishTarget() {
        assertEquals(
            128,
            PlaneInventoryQueries.effectiveReplenishTarget(128, 32, 256),
            "configured target is preserved when capacity has room"
        );
        assertEquals(
            192,
            PlaneInventoryQueries.effectiveReplenishTarget(512, 32, 192),
            "configured target above inventory capacity clamps to capacity"
        );
        assertEquals(
            32,
            PlaneInventoryQueries.effectiveReplenishTarget(16, 32, 256),
            "configured target below replenish minimum clamps to minimum"
        );
        assertEquals(
            96,
            PlaneInventoryQueries.effectiveReplenishTarget(256, 32, 96),
            "capacity includes partially fillable obsidian stacks"
        );
    }

    private static void computesSafeReplenishTargetPolicy() {
        assertEquals(
            128,
            PlaneReplenishTargetPolicy.effectiveTarget(128, false, 32, 256, 384),
            "toggle disabled preserves configured target"
        );
        assertEquals(
            192,
            PlaneReplenishTargetPolicy.effectiveTarget(512, false, 32, 192, 384),
            "toggle disabled preserves existing capacity clamp"
        );
        assertEquals(
            384,
            PlaneReplenishTargetPolicy.effectiveTarget(128, true, 32, 256, 384),
            "toggle enabled ignores configured target and uses safe capacity"
        );
        assertEquals(
            32,
            PlaneReplenishTargetPolicy.effectiveTarget(128, true, 32, 256, 16),
            "safe target never drops below replenish minimum"
        );
        assertEquals(
            128,
            PlaneReplenishTargetPolicy.safeBuildBlockCapacity(64, 64, 1, 64, true, false, false),
            "partial obsidian stack room is counted before empty slots"
        );
        assertEquals(
            192,
            PlaneReplenishTargetPolicy.safeBuildBlockCapacity(64, 0, 2, 64, true, false, false),
            "empty inventory slots count as loose obsidian capacity"
        );
        assertEquals(
            128,
            PlaneReplenishTargetPolicy.safeBuildBlockCapacity(64, 0, 2, 64, false, false, false),
            "missing partial ender chest stack reserves one empty slot"
        );
        assertEquals(
            64,
            PlaneReplenishTargetPolicy.safeBuildBlockCapacity(64, 0, 2, 64, false, true, true),
            "dynamic reservations reduce fillable empty slots"
        );
        assertEquals(
            64,
            PlaneReplenishTargetPolicy.safeBuildBlockCapacity(64, 0, 0, 64, true, false, false),
            "occupied non-obsidian slots do not add capacity"
        );
    }

    private static void sharesSafeReplenishTargetAcrossWorkflows() {
        RecordingTargetInventory inventory = new RecordingTargetInventory(96, 160, 80);

        int manualTarget = PlaneReplenishTargets.effectiveTarget(inventory, 128, false);
        assertEquals(96, manualTarget, "shared target uses manual inventory clamp when toggle is disabled");
        assertFalse(inventory.lastUseAvailableSafeInventorySpace, "manual target passes disabled toggle to inventory");

        int safeTarget = PlaneReplenishTargets.effectiveTarget(inventory, 128, true);
        assertEquals(160, safeTarget, "shared target uses safe inventory capacity when toggle is enabled");
        assertTrue(inventory.lastUseAvailableSafeInventorySpace, "safe target passes enabled toggle to inventory");

        EnderChestFarmProgress farmProgress = new EnderChestFarmProgress();
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.sourcePhase(safeTarget, safeTarget, 0),
            "ender chest farming stops when safe-capacity target is reached"
        );
        assertEquals(
            5,
            farmProgress.additionalEnderChestsNeeded(120, safeTarget),
            "shulker extraction requests only enough ender chests for the shared safe target"
        );
        assertEquals(
            10,
            inventory.requiredEnderChestsForTarget(safeTarget),
            "kitbot refill supply calculation uses the same shared safe target"
        );
    }

    private static void tracksPendingEnderChestFarmProgress() {
        EnderChestFarmProgress progress = new EnderChestFarmProgress();
        progress.recordFarmedEnderChest(64);
        assertEquals(8, progress.pendingFarmedObsidian(), "one farmed ender chest adds eight pending obsidian");
        assertEquals(72, progress.effectiveBuildBlocks(64), "pending obsidian contributes to effective build blocks");

        progress.recordFarmedEnderChest(64);
        assertEquals(16, progress.pendingFarmedObsidian(), "multiple farmed ender chests accumulate pending obsidian");
        assertEquals(80, progress.effectiveBuildBlocks(64), "multiple pending ender chests contribute to effective blocks");

        assertEquals(80, progress.effectiveBuildBlocks(72), "picked up obsidian replaces matching pending obsidian without losing effective progress");
        assertEquals(8, progress.pendingFarmedObsidian(), "inventory pickup reconciles pending obsidian downward");
        assertEquals(1, progress.additionalEnderChestsNeeded(72, 88), "pending obsidian reduces shulker extraction need");

        progress.recordFarmedEnderChest(72);
        assertEquals(0, progress.additionalEnderChestsNeeded(72, 88), "pending obsidian can fully cover target shortfall");
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.sourcePhase(progress.effectiveBuildBlocks(72), 88, 1),
            "target reached by pending obsidian closes instead of placing another ender chest"
        );

        progress.reset();
        assertEquals(0, progress.pendingFarmedObsidian(), "reset clears pending farm progress");
        assertEquals(64, progress.effectiveBuildBlocks(64), "reset returns effective blocks to inventory count");
    }

    private static void classifiesServiceHoleStatuses() {
        assertEquals(
            ServiceHoleContext.Status.MISSING,
            ServiceHoleContext.statusFor(false, false, ServiceHoleContext.HoleBlock.MISSING),
            "unselected service hole is missing"
        );
        assertEquals(
            ServiceHoleContext.Status.INVALID_SUPPORT,
            ServiceHoleContext.statusFor(true, false, ServiceHoleContext.HoleBlock.REPLACEABLE),
            "selected service hole with bad support is invalid"
        );
        assertEquals(
            ServiceHoleContext.Status.READY_BUILD_BLOCK,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.BUILD_BLOCK),
            "supported build block is ready"
        );
        assertEquals(
            ServiceHoleContext.Status.READY_BREAKABLE_CAP,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.BREAKABLE_CAP),
            "supported breakable cap is ready to open"
        );
        assertEquals(
            ServiceHoleContext.Status.READY_REPLACEABLE,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.REPLACEABLE),
            "supported replaceable hole is ready"
        );
        assertEquals(
            ServiceHoleContext.Status.READY_ENDER_CHEST,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.ENDER_CHEST),
            "supported ender chest is ready"
        );
        assertEquals(
            ServiceHoleContext.Status.READY_SHULKER,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.SHULKER),
            "supported shulker is ready"
        );
        assertEquals(
            ServiceHoleContext.Status.BLOCKED,
            ServiceHoleContext.statusFor(true, true, ServiceHoleContext.HoleBlock.BLOCKED),
            "supported unknown block is blocked"
        );

        assertTrue(ServiceHoleContext.Status.READY_REPLACEABLE.readyForWorkflow(), "ready status allows workflow");
        assertTrue(ServiceHoleContext.Status.READY_BREAKABLE_CAP.readyForWorkflow(), "breakable cap allows opening workflow");
        assertTrue(ServiceHoleContext.Status.READY_ENDER_CHEST.openForReplenish(), "ender chest status is open for replenish");
        assertFalse(ServiceHoleContext.Status.READY_BUILD_BLOCK.openForReplenish(), "build block must be opened before replenish");
        assertFalse(ServiceHoleContext.Status.READY_BREAKABLE_CAP.openForReplenish(), "breakable cap must be opened before replenish");
        assertFalse(ServiceHoleContext.Status.INVALID_SUPPORT.readyForWorkflow(), "invalid support blocks workflow");
    }

    private static void plansReplenishSourceTransitions() {
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.sourcePhase(128, 128, 0),
            "target obsidian closes service hole"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.sourcePhase(64, 128, 1),
            "loose ender chest is preferred source"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.sourcePhase(64, 128, 0),
            "missing loose ender chests falls back to shulker"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.sourcePhase(64, 128, 0, true),
            "visible shulker source recovers source selection without loose ender chests"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            PlaneReplenishDecisions.sourcePhase(64, 128, 0, false),
            "missing local supply remains missing during source recovery"
        );
    }

    private static void plansMissingEnderChestSourceRecovery() {
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.missingEnderChestRecoveryPhase(Phase.MISSING_ENDER_CHEST, 1, true),
            "loose ender chest is preferred over shulker-contained supply"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.missingEnderChestRecoveryPhase(Phase.MISSING_ENDER_CHEST, 0, true),
            "visible shulker source recovers missing ender chest"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            PlaneReplenishDecisions.missingEnderChestRecoveryPhase(Phase.MISSING_ENDER_CHEST, 0, false),
            "missing ender chest remains missing when no local supply exists"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.missingEnderChestRecoveryPhase(Phase.MISSING_ENDER_CHEST_SHULKER, 0, true),
            "visible shulker source recovers missing shulker state"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.missingEnderChestRecoveryPhase(
                PlaneReplenishDecisions.shulkerExtractionUnavailable(2, 2),
                0,
                true
            ),
            "no confirmed extraction retries another visible shulker source"
        );
    }

    private static void plansEnderChestInventoryRecovery() {
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.enderChestInventoryPhase(true, false),
            "already usable ender chest keeps placement active without promotion"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.enderChestInventoryPhase(false, true),
            "main inventory ender chest keeps placement active for hotbar recovery"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            PlaneReplenishDecisions.enderChestInventoryPhase(false, false),
            "absent loose ender chest reports missing source"
        );
    }

    private static void plansEnderChestBreakRecovery() {
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.afterEnderChestBreak(ServiceHoleContext.Status.READY_REPLACEABLE, 128, 128),
            "replaceable hole during ender chest breaking closes after target reached"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.afterEnderChestBreak(ServiceHoleContext.Status.READY_REPLACEABLE, 96, 128),
            "replaceable hole during ender chest breaking places another ender chest before target"
        );
        assertEquals(
            Phase.SERVICE_HOLE_BLOCKED,
            PlaneReplenishDecisions.afterEnderChestBreak(ServiceHoleContext.Status.READY_SHULKER, 96, 128),
            "unexpected non-replaceable hole during ender chest breaking is blocked"
        );
    }

    private static void tracksEnderChestBreakWatchdog() {
        BlockPos firstTarget = new BlockPos(1, 319, 1);
        BlockPos secondTarget = new BlockPos(2, 319, 2);
        EnderChestBreakWatchdog watchdog = new EnderChestBreakWatchdog(3);

        assertFalse(watchdog.timeout(firstTarget, 64), "first stale break tick stays active");
        assertFalse(watchdog.timeout(firstTarget, 64), "watchdog does not fire before threshold");
        assertEquals(2, watchdog.staleBreakTicks(), "stale break ticks count repeated target attempts");
        assertFalse(watchdog.timeout(secondTarget, 64), "target change resets stale break watchdog");
        assertEquals(1, watchdog.staleBreakTicks(), "target change starts a fresh stale break count");
        assertFalse(watchdog.timeout(secondTarget, 72), "obsidian progress resets stale break watchdog");
        assertEquals(1, watchdog.staleBreakTicks(), "obsidian progress starts a fresh stale break count");
        assertFalse(watchdog.timeout(secondTarget, 72), "watchdog keeps waiting before timeout after progress");
        assertTrue(watchdog.timeout(secondTarget, 72), "watchdog fires after threshold");
        assertEquals(3, watchdog.staleBreakTicks(), "watchdog exposes timeout tick count for logging");

        watchdog.reset();
        assertEquals(0, watchdog.staleBreakTicks(), "reset clears stale break ticks");
    }

    private static void plansEnderChestPlacementTransitions() {
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.enderChestPlacementPhase(ServiceHoleContext.Status.READY_REPLACEABLE),
            "replaceable service hole can receive an ender chest"
        );
        assertEquals(
            Phase.BREAKING_ENDER_CHEST,
            PlaneReplenishDecisions.enderChestPlacementPhase(ServiceHoleContext.Status.READY_ENDER_CHEST),
            "existing ender chest should be broken"
        );
        assertEquals(
            Phase.ENDER_CHEST_SHULKER_PLACED,
            PlaneReplenishDecisions.enderChestPlacementPhase(ServiceHoleContext.Status.READY_SHULKER),
            "existing shulker resumes shulker opening"
        );
        assertEquals(
            Phase.SERVICE_HOLE_BLOCKED,
            PlaneReplenishDecisions.enderChestPlacementPhase(ServiceHoleContext.Status.INVALID_SUPPORT),
            "invalid service hole blocks ender chest placement"
        );
    }

    private static void plansServiceHoleCloseTransitions() {
        assertEquals(
            Phase.IDLE,
            PlaneReplenishDecisions.closeServiceHolePhase(ServiceHoleContext.Status.READY_BUILD_BLOCK, false),
            "closed build block returns to idle"
        );
        assertEquals(
            Phase.BREAKING_ENDER_CHEST,
            PlaneReplenishDecisions.closeServiceHolePhase(ServiceHoleContext.Status.READY_ENDER_CHEST, false),
            "ender chest must be broken before closing"
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.closeServiceHolePhase(ServiceHoleContext.Status.READY_REPLACEABLE, true),
            "replaceable hole with obsidian can close"
        );
        assertEquals(
            Phase.MISSING_OBSIDIAN,
            PlaneReplenishDecisions.closeServiceHolePhase(ServiceHoleContext.Status.READY_REPLACEABLE, false),
            "replaceable hole without obsidian reports missing obsidian"
        );
        assertEquals(
            Phase.SERVICE_HOLE_BLOCKED,
            PlaneReplenishDecisions.closeServiceHolePhase(ServiceHoleContext.Status.INVALID_SUPPORT, true),
            "invalid support blocks close"
        );
    }

    private static void plansServiceHoleReadiness() {
        assertEquals(
            Phase.SELECTING_SERVICE_HOLE,
            PlaneReplenishDecisions.serviceHoleReadyPhase(ServiceHoleContext.Status.MISSING),
            "missing service hole reselects"
        );
        assertEquals(
            null,
            PlaneReplenishDecisions.serviceHoleReadyPhase(ServiceHoleContext.Status.READY_REPLACEABLE),
            "ready service hole has no unavailable phase"
        );
        assertEquals(
            null,
            PlaneReplenishDecisions.serviceHoleReadyPhase(ServiceHoleContext.Status.READY_BREAKABLE_CAP),
            "breakable capped service hole can proceed to opening"
        );
        assertEquals(
            Phase.SERVICE_HOLE_BLOCKED,
            PlaneReplenishDecisions.serviceHoleReadyPhase(ServiceHoleContext.Status.BLOCKED),
            "blocked service hole stops workflow"
        );
    }

    private static void plansMissingObsidianRecovery() {
        assertEquals(
            Phase.SELECTING_SERVICE_HOLE,
            PlaneReplenishDecisions.missingObsidianRecoveryPhase(31, 32, false),
            "low obsidian restarts replenish from missing obsidian"
        );
        assertEquals(
            Phase.MISSING_OBSIDIAN,
            PlaneReplenishDecisions.missingObsidianRecoveryPhase(0, 32, false),
            "absent obsidian stays in explicit missing phase"
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.missingObsidianRecoveryPhase(128, 32, true),
            "selected service hole retries close recovery"
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.missingObsidianServiceHoleRecoveryPhase(ServiceHoleContext.Status.READY_REPLACEABLE, true),
            "replaceable service hole can close after obsidian becomes prepareable"
        );
        assertEquals(
            Phase.MISSING_OBSIDIAN,
            PlaneReplenishDecisions.missingObsidianServiceHoleRecoveryPhase(ServiceHoleContext.Status.READY_REPLACEABLE, false),
            "replaceable service hole waits until obsidian can be prepared"
        );
    }

    private static void tracksReplenishActivePhases() {
        assertFalse(PlaneReplenishDecisions.active(Phase.IDLE, false), "idle is not active");
        assertTrue(PlaneReplenishDecisions.active(Phase.SELECTING_SERVICE_HOLE, false), "selecting service hole is active");
        assertTrue(PlaneReplenishDecisions.active(Phase.MISSING_OBSIDIAN, false), "missing obsidian stays active for recovery");
        assertTrue(PlaneReplenishDecisions.active(Phase.MISSING_OBSIDIAN, true), "missing obsidian after service-hole selection remains active");
        assertTrue(PlaneReplenishDecisions.active(Phase.WAITING_FOR_KITBOT_REFILL, true), "kitbot wait keeps replenish ownership");
        assertTrue(PlaneReplenishDecisions.active(Phase.PICKING_UP_REPLENISH_DROPS, false), "cleanup keeps replenish ownership");
        assertTrue(PlaneReplenishDecisions.active(Phase.MOVING_TO_TRASH_EDGE, false), "moving to trash edge keeps replenish ownership");
        assertTrue(PlaneReplenishDecisions.active(Phase.DROPPING_TRASH_OFF_EDGE, false), "dropping trash off edge keeps replenish ownership");
        assertTrue(PlaneReplenishDecisions.active(Phase.WAITING_FOR_TRASH_TO_FALL, false), "waiting for trash to fall keeps replenish ownership");
    }

    private static void tracksPhasePolicy() {
        assertFalse(PlanePhasePolicy.replenishActive(Phase.IDLE, false), "policy keeps idle inactive");
        assertTrue(PlanePhasePolicy.replenishActive(Phase.SELECTING_SERVICE_HOLE, false), "policy keeps service-hole selection active");
        assertTrue(PlanePhasePolicy.replenishActive(Phase.MISSING_OBSIDIAN, false), "policy keeps missing obsidian active before service-hole selection");
        assertTrue(PlanePhasePolicy.replenishActive(Phase.MISSING_OBSIDIAN, true), "policy treats missing obsidian active after service-hole selection");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.MISSING_OBSIDIAN), "policy keeps bow defense during missing obsidian recovery");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.SELECTING_SERVICE_HOLE), "policy keeps bow defense during service-hole selection");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.SERVICE_HOLE_OPEN), "policy keeps bow defense while service hole is open");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.SELECTING_REPLENISH_SOURCE), "policy keeps bow defense during passive source selection");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.SERVICE_HOLE_BLOCKED), "policy keeps bow defense during blocked service-hole wait");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.MISSING_ENDER_CHEST), "policy keeps bow defense during missing ender chest wait");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.MISSING_ENDER_CHEST_SHULKER), "policy keeps bow defense during missing shulker wait");
        assertFalse(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.PLACING_ENDER_CHEST), "policy pauses bow defense during ender chest placement");
        assertFalse(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.PLACING_ENDER_CHEST_SHULKER), "policy pauses bow defense during ender chest shulker placement");
        assertFalse(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.BREAKING_ENDER_CHEST), "policy pauses bow defense during ender chest breaking");
        assertFalse(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.BREAKING_ENDER_CHEST_SHULKER), "policy pauses bow defense during ender chest shulker breaking");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.WAITING_FOR_KITBOT_REFILL), "policy keeps bow defense during kitbot wait");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.PICKING_UP_KITBOT_REFILL), "policy keeps bow defense during kitbot pickup movement");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER), "policy keeps bow defense during missing shulker pickup movement");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.PICKING_UP_REPLENISH_DROPS), "policy keeps bow defense during cleanup pickup movement");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.MOVING_TO_TRASH_EDGE), "policy keeps bow defense during trash edge movement");
        assertFalse(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.DROPPING_TRASH_OFF_EDGE), "policy pauses bow defense during edge trash disposal");
        assertTrue(PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(Phase.WAITING_FOR_TRASH_TO_FALL), "policy keeps bow defense during trash fall wait");
        assertFalse(PlanePhasePolicy.bowDefenseReplenishActive(Phase.WAITING_FOR_KITBOT_REFILL, true), "policy releases bow defense during kitbot wait");
        assertFalse(PlanePhasePolicy.bowDefenseReplenishActive(Phase.PICKING_UP_KITBOT_REFILL, true), "policy releases bow defense during kitbot pickup movement");
        assertTrue(PlanePhasePolicy.bowDefenseReplenishActive(Phase.PLACING_ENDER_CHEST, true), "policy keeps replenish active during ender chest placement");
        assertTrue(PlanePhasePolicy.bowDefenseReplenishActive(Phase.PLACING_ENDER_CHEST_SHULKER, true), "policy keeps replenish active during shulker placement");
        assertTrue(PlanePhasePolicy.bowDefenseReplenishActive(Phase.BREAKING_ENDER_CHEST, true), "policy keeps replenish active during ender chest breaking");
        assertTrue(PlanePhasePolicy.bowDefenseReplenishActive(Phase.BREAKING_ENDER_CHEST_SHULKER, true), "policy keeps replenish active during shulker breaking");
        assertFalse(PlanePhasePolicy.bowDefenseReplenishActive(Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER, true), "policy releases bow defense during missing shulker pickup movement");
        assertTrue(PlanePhasePolicy.bowDefenseReplenishActive(Phase.DROPPING_TRASH_OFF_EDGE, true), "policy keeps replenish active during trash disposal");
        assertFalse(PlanePhasePolicy.bowDefenseReplenishActive(Phase.WAITING_FOR_TRASH_TO_FALL, true), "policy releases bow defense during trash fall wait");
    }

    private static void preservesActiveReplenishWhileKitbotRefillIsPending() {
        assertFalse(
            PlaneReplenishWorkflow.pendingKitbotRefillOwnsPhase(Phase.PLACING_ENDER_CHEST),
            "pending kitbot refill does not own ender chest placement actions"
        );
        assertFalse(
            PlaneReplenishWorkflow.pendingKitbotRefillOwnsPhase(Phase.PICKING_UP_REPLENISH_DROPS),
            "pending kitbot refill does not own replenish cleanup pickup"
        );
        assertFalse(
            PlaneReplenishWorkflow.pendingKitbotRefillOwnsPhase(Phase.MOVING_TO_TRASH_EDGE),
            "pending kitbot refill does not own trash edge navigation"
        );
        assertTrue(
            PlaneReplenishWorkflow.pendingKitbotRefillOwnsPhase(Phase.WAITING_FOR_KITBOT_REFILL),
            "pending kitbot refill keeps the wait phase active"
        );
        assertTrue(
            PlaneReplenishWorkflow.pendingKitbotRefillOwnsPhase(Phase.PICKING_UP_KITBOT_REFILL),
            "pending kitbot refill allows confirmed delivery pickup"
        );
    }

    private static final class RecordingTargetInventory implements PlaneInventoryAccess {
        private final int manualTarget;
        private final int safeTarget;
        private final int currentBuildBlocks;
        private boolean lastUseAvailableSafeInventorySpace;

        private RecordingTargetInventory(int manualTarget, int safeTarget, int currentBuildBlocks) {
            this.manualTarget = manualTarget;
            this.safeTarget = safeTarget;
            this.currentBuildBlocks = currentBuildBlocks;
        }

        @Override
        public int countBuildBlock() {
            return currentBuildBlocks;
        }

        @Override
        public int effectiveReplenishTarget(int configuredTarget, boolean useAvailableSafeInventorySpace) {
            lastUseAvailableSafeInventorySpace = useAvailableSafeInventorySpace;
            return useAvailableSafeInventorySpace ? safeTarget : manualTarget;
        }

        @Override
        public int requiredEnderChestsForTarget(int targetBuildBlocks) {
            return PlaneInventoryQueries.requiredEnderChestsForTarget(currentBuildBlocks, targetBuildBlocks);
        }

        @Override
        public int countLooseEnderChests() {
            throw unsupported();
        }

        @Override
        public boolean hasInventorySpaceForEnderChest() {
            throw unsupported();
        }

        @Override
        public FindItemResult findHotbarBuildBlock() {
            throw unsupported();
        }

        @Override
        public FindItemResult findEnderChest() {
            throw unsupported();
        }

        @Override
        public FindItemResult findHotbarBow() {
            throw unsupported();
        }

        @Override
        public FindItemResult prepareUsableBow() {
            throw unsupported();
        }

        @Override
        public boolean hasArrows() {
            throw unsupported();
        }

        @Override
        public FindItemResult findEnderChestShulkerInHotbar() {
            throw unsupported();
        }

        @Override
        public boolean hasEnderChestShulkerInMainInventory() {
            throw unsupported();
        }

        @Override
        public int findMainInventoryBuildBlockSlot() {
            throw unsupported();
        }

        @Override
        public int findMainInventoryEnderChestShulkerSlot() {
            throw unsupported();
        }

        @Override
        public int findMainInventoryPickaxeSlot() {
            throw unsupported();
        }

        @Override
        public int findMainInventoryBowSlot() {
            throw unsupported();
        }

        @Override
        public boolean hasAnyEnderChestShulker() {
            throw unsupported();
        }

        @Override
        public EnderChestShulkerSourceScan scanEnderChestShulkerSources() {
            throw unsupported();
        }

        @Override
        public int findOpenShulkerEnderChestSlot(ScreenHandler handler) {
            throw unsupported();
        }

        @Override
        public boolean findResultMatchesBuildBlock(FindItemResult result) {
            throw unsupported();
        }

        @Override
        public boolean findResultMatchesEnderChestShulker(FindItemResult result) {
            throw unsupported();
        }

        @Override
        public boolean findResultMatchesBlock(FindItemResult result, Block block) {
            throw unsupported();
        }

        @Override
        public boolean isBuildBlockStack(ItemStack stack) {
            throw unsupported();
        }

        @Override
        public boolean isBlockStack(ItemStack stack, Block block) {
            throw unsupported();
        }

        @Override
        public boolean isShulkerWithEnderChests(ItemStack stack) {
            throw unsupported();
        }

        @Override
        public boolean isShulkerBoxStack(ItemStack stack) {
            throw unsupported();
        }

        @Override
        public boolean isEnderChestSupplyStack(ItemStack stack) {
            throw unsupported();
        }

        @Override
        public FindItemResult findHotbarPickaxe() {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("unused test inventory method");
        }
    }

}
