package com.watchmenbot.modules.planebuilder;

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
        tracksPendingEnderChestFarmProgress();
        selectsBestEnderChestShulkerHotbarSlot();
        detectsConfirmedShulkerExtraction();
        classifiesServiceHoleStatuses();
        plansReplenishSourceTransitions();
        plansMissingEnderChestSourceRecovery();
        plansEnderChestInventoryRecovery();
        plansEnderChestBreakRecovery();
        tracksEnderChestBreakWatchdog();
        plansEnderChestPlacementTransitions();
        plansServiceHoleCloseTransitions();
        plansReplenishDropCleanupTransitions();
        plansGenericDroppedItemPickupFallback();
        exitsPickupWhenCleanupDropCannotFit();
        matchesReplenishCleanupStacks();
        plansCleanupDropPickupCapacity();
        matchesTrashStacks();
        plansServiceHoleReadiness();
        plansMissingObsidianRecovery();
        tracksTrashDropWait();
        plansTrashFallWaitDecisions();
        timesOutPersistentTrashDropWait();
        tracksTrashCleanupCycleExhaustion();
        tracksManagedEnderChestShulkerState();
        tracksManagedShulkerFailedOpenRecovery();
        recoversFreshlyBrokenManagedShulkerDrop();
        recoversMissingEnderChestShulkerDrop();
        timesOutStaleManagedShulkerRecoveryDrop();
        resetsManagedShulkerRecoveryTimeoutWhenTargetChanges();
        classifiesEnderChestShulkerSources();
        debouncesMissingEnderChestShulkerSource();
        plansShulkerExtractionFallbacks();
        tracksShulkerExtractionSession();
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

    private static void selectsBestEnderChestShulkerHotbarSlot() {
        assertEquals(
            -1,
            PlaneInventoryQueries.bestEnderChestShulkerHotbarSlot(new int[] {0, 0, 0}),
            "missing ender chest shulker reports no hotbar slot"
        );
        assertEquals(
            2,
            PlaneInventoryQueries.bestEnderChestShulkerHotbarSlot(new int[] {0, 8, 3, 12}),
            "hotbar selection prefers shulker with fewest ender chests"
        );
        assertEquals(
            1,
            PlaneInventoryQueries.bestEnderChestShulkerHotbarSlot(new int[] {0, 4, 4, 7}),
            "hotbar selection keeps earliest slot on tied ender chest counts"
        );
    }

    private static void detectsConfirmedShulkerExtraction() {
        assertFalse(
            PlaneInventoryQueries.shulkerExtractionComplete(0, 0, 1),
            "missing extraction does not finish"
        );
        assertTrue(
            PlaneInventoryQueries.shulkerExtractionComplete(0, 1, 1),
            "first extracted ender chest finishes"
        );
        assertFalse(
            PlaneInventoryQueries.shulkerExtractionComplete(1, 1, 1),
            "pre-existing ender chest does not count as extracted"
        );
        assertTrue(
            PlaneInventoryQueries.shulkerExtractionComplete(1, 2, 2),
            "extracted ender chest reaching need finishes"
        );
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
        assertTrue(ServiceHoleContext.Status.READY_ENDER_CHEST.openForReplenish(), "ender chest status is open for replenish");
        assertFalse(ServiceHoleContext.Status.READY_BUILD_BLOCK.openForReplenish(), "build block must be opened before replenish");
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

    private static void plansReplenishDropCleanupTransitions() {
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            PlaneReplenishDecisions.afterNormalServiceHoleClose(Phase.IDLE),
            "normal service hole close starts cleanup before idle"
        );
        assertEquals(
            Phase.CLOSING_SERVICE_HOLE,
            PlaneReplenishDecisions.afterNormalServiceHoleClose(Phase.CLOSING_SERVICE_HOLE),
            "unfinished service hole close stays in close phase"
        );
        assertEquals(
            Phase.MOVING_TO_TRASH_EDGE,
            PlaneReplenishDecisions.cleanupPickupPhase(false),
            "cleanup without matching drops starts edge trash disposal"
        );
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            PlaneReplenishDecisions.cleanupPickupPhase(true),
            "cleanup with matching drops keeps picking up"
        );
    }

    private static void plansGenericDroppedItemPickupFallback() {
        FakePickupNavigator navigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> workflow = new PlaneDroppedItemPickupWorkflow<>(
            () -> null,
            item -> false,
            navigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.IDLE,
            1
        );

        assertFalse(workflow.hasTarget(), "missing pickup target reports no target");
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            workflow.tick(),
            "pickup workflow stays active during no-target grace"
        );
        assertEquals(
            Phase.IDLE,
            workflow.tick(),
            "pickup workflow returns fallback after grace expires"
        );

        Object persistentTarget = new Object();
        FakePickupNavigator timeoutNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> timeoutWorkflow = new PlaneDroppedItemPickupWorkflow<>(
            () -> persistentTarget,
            item -> item == persistentTarget,
            item -> true,
            timeoutNavigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.MOVING_TO_TRASH_EDGE,
            0,
            2
        );
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            timeoutWorkflow.tick(),
            "persistent pickup target starts pathing before timeout"
        );
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            timeoutWorkflow.tick(),
            "persistent pickup target keeps pathing until max target ticks"
        );
        assertEquals(
            Phase.MOVING_TO_TRASH_EDGE,
            timeoutWorkflow.tick(),
            "persistent pickup target falls back after max target ticks"
        );
        assertEquals(2, timeoutNavigator.pathTicks, "timeout target paths only before fallback");
        assertEquals(1, timeoutNavigator.stopTicks, "timeout fallback stops navigator");
    }

    private static void exitsPickupWhenCleanupDropCannotFit() {
        Object target = new Object();
        FakePickupNavigator pickupableNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> pickupable = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> true,
            pickupableNavigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.MOVING_TO_TRASH_EDGE,
            1
        );

        assertTrue(pickupable.hasTarget(), "matching pickupable target is acquired");
        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            pickupable.tick(),
            "matching pickupable target keeps cleanup active"
        );
        assertEquals(1, pickupableNavigator.pathTicks, "pickupable target starts pathing");
        assertEquals(0, pickupableNavigator.stopTicks, "pickupable target does not stop navigator");

        FakePickupNavigator fullInventoryNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> fullInventory = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> false,
            fullInventoryNavigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.MOVING_TO_TRASH_EDGE,
            1
        );

        assertEquals(
            Phase.MOVING_TO_TRASH_EDGE,
            fullInventory.tick(),
            "unpickupable matching target falls back immediately"
        );
        assertEquals(0, fullInventoryNavigator.pathTicks, "unpickupable target does not path");
        assertEquals(1, fullInventoryNavigator.stopTicks, "unpickupable target stops navigator");
        assertFalse(fullInventory.hasTarget(), "unpickupable target is cleared after fallback");
    }

    private static void matchesReplenishCleanupStacks() {
        assertTrue(
            PlaneItemClassifier.cleanupDropKind(true, false),
            "obsidian cleanup kind matches"
        );
        assertTrue(
            PlaneItemClassifier.cleanupDropKind(false, true),
            "shulker cleanup kind matches"
        );
        assertTrue(
            PlaneItemClassifier.cleanupDropKind(true, true),
            "obsidian shulker cleanup kind matches"
        );
        assertFalse(
            PlaneItemClassifier.cleanupDropKind(false, false),
            "unrelated cleanup kind does not match"
        );
    }

    private static void plansCleanupDropPickupCapacity() {
        assertTrue(
            PlaneInventoryQueries.cleanupDropPickupable(true, false, true, false),
            "empty slot accepts obsidian cleanup drop"
        );
        assertTrue(
            PlaneInventoryQueries.cleanupDropPickupable(false, true, true, false),
            "empty slot accepts shulker cleanup drop"
        );
        assertTrue(
            PlaneInventoryQueries.cleanupDropPickupable(true, false, false, true),
            "partial obsidian stack accepts obsidian cleanup drop"
        );
        assertFalse(
            PlaneInventoryQueries.cleanupDropPickupable(true, false, false, false),
            "full inventory without partial obsidian rejects obsidian cleanup drop"
        );
        assertFalse(
            PlaneInventoryQueries.cleanupDropPickupable(false, true, false, true),
            "full inventory rejects shulker cleanup drop"
        );
        assertFalse(
            PlaneInventoryQueries.cleanupDropPickupable(false, false, true, true),
            "unrelated drop is not treated as pickupable cleanup"
        );
    }

    private static void matchesTrashStacks() {
        assertTrue(PlaneItemClassifier.trashAllowedKind(true, false, false), "mob junk is an allowed trash kind");
        assertTrue(PlaneItemClassifier.trashAllowedKind(false, true, false), "ingots are an allowed trash kind");
        assertTrue(PlaneItemClassifier.trashAllowedKind(false, false, true), "mob equipment is an allowed trash kind");
        assertFalse(PlaneItemClassifier.trashAllowedKind(false, false, false), "unlisted items are not allowed trash kinds");
        assertTrue(PlaneItemClassifier.trashKind(true, false, false, false, false, false, false), "mob junk is trash");
        assertTrue(PlaneItemClassifier.trashKind(true, false, false, true, true, false, false), "empty shulkers are trash");
        assertFalse(PlaneItemClassifier.trashKind(false, false, false, false, false, false, false), "unlisted items are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, true, false, false, false, false, false), "build blocks are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, false, true, false, false, false, false), "ender chest supplies are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, false, true, true, false, false, false), "ender chest supply shulkers are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, false, false, true, false, false, false), "non-empty shulkers are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, false, false, false, false, true, false), "arrows are preserved");
        assertFalse(PlaneItemClassifier.trashKind(true, false, false, false, false, false, true), "ender pearls are preserved");
        assertTrue(PlaneItemClassifier.cryingObsidianTrashKind(true), "crying obsidian is trash");
        assertTrue(PlaneItemClassifier.mobEquipmentTrashKind(true, false, false, false), "iron equipment is mob equipment trash");
        assertTrue(PlaneItemClassifier.mobEquipmentTrashKind(false, true, false, false), "gold equipment is mob equipment trash");
        assertTrue(PlaneItemClassifier.mobEquipmentTrashKind(false, false, true, false), "chainmail armor is mob equipment trash");
        assertTrue(PlaneItemClassifier.mobEquipmentTrashKind(false, false, false, true), "leather armor is mob equipment trash");
        assertFalse(PlaneItemClassifier.mobEquipmentTrashKind(false, false, false, false), "other equipment is preserved");
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

    private static void tracksTrashDropWait() {
        PlaneTrashDropWait wait = new PlaneTrashDropWait(10);
        wait.start();

        assertEquals(
            PlaneTrashDropWait.Result.WAITING,
            wait.tick(true),
            "nearby trash drop keeps fall wait active"
        );
        for (int tick = 1; tick < 10; tick++) {
            assertEquals(
                PlaneTrashDropWait.Result.WAITING,
                wait.tick(false),
                "trash fall clear grace tick " + tick + " keeps waiting"
            );
        }
        assertEquals(
            PlaneTrashDropWait.Result.CLEARED,
            wait.tick(false),
            "trash fall wait reports clear after clear grace"
        );
    }

    private static void timesOutPersistentTrashDropWait() {
        PlaneTrashDropWait wait = new PlaneTrashDropWait(10, 4);
        wait.start();

        for (int tick = 1; tick < 4; tick++) {
            assertEquals(
                PlaneTrashDropWait.Result.WAITING,
                wait.tick(true),
                "persistent nearby trash drop keeps fall wait active before timeout tick " + tick
            );
        }
        assertEquals(
            PlaneTrashDropWait.Result.TIMED_OUT,
            wait.tick(true),
            "persistent nearby trash drop reports timeout"
        );
    }

    private static void plansTrashFallWaitDecisions() {
        PlaneTrashEdgeDecisions.FallWaitDecision reacquired = PlaneTrashEdgeDecisions.fallWait(
            true,
            PlaneTrashDropWait.Result.WAITING
        );
        assertEquals(Phase.IDLE, reacquired.phase(), "reacquired trash during fall wait stops cleanup");
        assertTrue(reacquired.exhaustCleanupCycle(), "reacquired trash exhausts current cleanup cycle");

        PlaneTrashEdgeDecisions.FallWaitDecision waiting = PlaneTrashEdgeDecisions.fallWait(
            false,
            PlaneTrashDropWait.Result.WAITING
        );
        assertEquals(Phase.WAITING_FOR_TRASH_TO_FALL, waiting.phase(), "visible trash drop keeps fall wait active");
        assertFalse(waiting.exhaustCleanupCycle(), "active fall wait does not exhaust cleanup");

        PlaneTrashEdgeDecisions.FallWaitDecision cleared = PlaneTrashEdgeDecisions.fallWait(
            false,
            PlaneTrashDropWait.Result.CLEARED
        );
        assertEquals(Phase.IDLE, cleared.phase(), "cleared trash drop ends fall wait");
        assertFalse(cleared.exhaustCleanupCycle(), "cleared fall wait does not exhaust cleanup");

        PlaneTrashEdgeDecisions.FallWaitDecision timedOut = PlaneTrashEdgeDecisions.fallWait(
            false,
            PlaneTrashDropWait.Result.TIMED_OUT
        );
        assertEquals(Phase.IDLE, timedOut.phase(), "timed-out trash drop ends fall wait");
        assertTrue(timedOut.exhaustCleanupCycle(), "timed-out fall wait exhausts cleanup");
    }

    private static void tracksTrashCleanupCycleExhaustion() {
        PlaneTrashCleanupCycle cycle = new PlaneTrashCleanupCycle();

        assertTrue(cycle.canStartTrashEdgeCleanup(), "fresh cleanup cycle allows trash edge cleanup");
        cycle.markExhausted();
        assertFalse(cycle.canStartTrashEdgeCleanup(), "timed-out cleanup cycle blocks immediate trash edge restart");
        cycle.begin();
        assertTrue(cycle.canStartTrashEdgeCleanup(), "new cleanup cycle clears trash edge exhaustion");
    }

    private static void tracksManagedEnderChestShulkerState() {
        ManagedEnderChestShulkerState state = new ManagedEnderChestShulkerState();
        BlockPos hole = new BlockPos(1, 2, 3);
        assertFalse(state.suppressesRefill(hole, ServiceHoleContext.Status.READY_SHULKER), "untracked shulker does not suppress refill");

        state.markPlaced(hole);
        assertTrue(state.placedAt(hole, ServiceHoleContext.Status.READY_SHULKER), "managed placed shulker is active in selected service hole");
        assertFalse(state.placedAt(hole, ServiceHoleContext.Status.READY_REPLACEABLE), "replaceable hole is not placed shulker state");
        assertTrue(state.suppressesRefill(hole, ServiceHoleContext.Status.READY_SHULKER), "managed placed shulker suppresses refill");

        state.markPostBreakRecovery();
        assertFalse(state.placedAt(hole, ServiceHoleContext.Status.READY_SHULKER), "post-break recovery clears placed shulker block");
        assertTrue(state.postBreakRecovery(), "post-break recovery is tracked");
        assertTrue(state.suppressesRefill(hole, ServiceHoleContext.Status.READY_REPLACEABLE), "post-break recovery suppresses refill");

        state.clearPostBreakRecovery();
        assertFalse(state.suppressesRefill(hole, ServiceHoleContext.Status.READY_REPLACEABLE), "cleared post-break recovery allows refill decisions");
    }

    private static void tracksManagedShulkerFailedOpenRecovery() {
        ManagedEnderChestShulkerState failed = new ManagedEnderChestShulkerState();
        BlockPos hole = new BlockPos(4, 5, 6);
        failed.markPlaced(hole);
        failed.markPostBreakRecovery();

        assertTrue(failed.postBreakRecovery(), "failed open starts post-break recovery");
        assertTrue(failed.failedBeforeOpenRecovery(), "shulker disappearing before open is recorded as failed-open recovery");
        assertFalse(failed.openedOrExtracted(), "failed-open recovery has no confirmed extraction progress");
        assertEquals(1, failed.failedOpenAttempts(), "failed-open recovery increments retry counter");

        ManagedEnderChestShulkerState opened = new ManagedEnderChestShulkerState();
        opened.markPlaced(hole);
        opened.markOpenedOrExtracted();
        opened.markPostBreakRecovery();
        assertFalse(opened.failedBeforeOpenRecovery(), "opened shulker recovery is not treated as failed-open");
        assertTrue(opened.openedOrExtracted(), "opened shulker records extraction progress");
    }

    private static void recoversFreshlyBrokenManagedShulkerDrop() {
        Object target = new Object();
        FakePickupNavigator dropNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> withDrop = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> true,
            dropNavigator,
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1
        );

        assertTrue(withDrop.hasTarget(), "fresh managed shulker drop is detected");
        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            withDrop.tick(),
            "fresh managed shulker drop keeps recovery active"
        );
        assertEquals(1, dropNavigator.pathTicks, "fresh managed shulker drop starts pickup pathing");

        FakePickupNavigator missingNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> missingDrop = new PlaneDroppedItemPickupWorkflow<>(
            () -> null,
            item -> false,
            item -> true,
            missingNavigator,
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1
        );
        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            missingDrop.tick(),
            "post-break recovery waits through no-target grace"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            missingDrop.tick(),
            "post-break recovery allows missing supply after grace expires"
        );
    }

    private static void recoversMissingEnderChestShulkerDrop() {
        Object target = new Object();
        FakePickupNavigator dropNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> withDrop = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> true,
            dropNavigator,
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1,
            2
        );

        assertTrue(withDrop.hasTarget(), "missing supply shulker pickup detects nearby shulker drop");
        assertEquals(
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            withDrop.tick(),
            "nearby shulker drop starts missing-supply pickup"
        );
        assertEquals(1, dropNavigator.pathTicks, "missing-supply shulker pickup starts pathing");

        FakePickupNavigator missingNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> missingDrop = new PlaneDroppedItemPickupWorkflow<>(
            () -> null,
            item -> false,
            item -> true,
            missingNavigator,
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            0
        );
        assertFalse(missingDrop.hasTarget(), "missing supply shulker pickup ignores absent drops");
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            missingDrop.tick(),
            "missing supply path can fall back when no shulker drop is selected"
        );

        FakePickupNavigator timeoutNavigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> staleDrop = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> true,
            timeoutNavigator,
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1,
            1
        );
        assertEquals(
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            staleDrop.tick(),
            "missing supply shulker pickup gets one active tick before timeout"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            staleDrop.tick(),
            "stale missing supply shulker pickup falls back after max target ticks"
        );
        assertEquals(1, timeoutNavigator.stopTicks, "stale missing supply shulker pickup stops navigation");
    }

    private static void timesOutStaleManagedShulkerRecoveryDrop() {
        Object target = new Object();
        FakePickupNavigator navigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> workflow = new PlaneDroppedItemPickupWorkflow<>(
            () -> target,
            item -> item == target,
            item -> true,
            navigator,
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1,
            1
        );

        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            workflow.tick(),
            "fresh managed shulker recovery target gets one active pickup tick"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            workflow.tick(),
            "stale managed shulker recovery target falls back after max target ticks"
        );
        assertEquals(1, navigator.pathTicks, "stale target is only pathed during the active recovery tick");
        assertEquals(1, navigator.stopTicks, "stale target timeout stops pickup navigation");
    }

    private static void resetsManagedShulkerRecoveryTimeoutWhenTargetChanges() {
        Object firstTarget = new Object();
        Object secondTarget = new Object();
        Object[] target = {firstTarget};
        FakePickupNavigator navigator = new FakePickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> workflow = new PlaneDroppedItemPickupWorkflow<>(
            () -> target[0],
            item -> item == target[0],
            item -> true,
            navigator,
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            1,
            1
        );

        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            workflow.tick(),
            "first managed shulker recovery target starts the timeout"
        );
        target[0] = secondTarget;
        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            workflow.tick(),
            "changed managed shulker recovery target resets the timeout"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            workflow.tick(),
            "unchanged replacement target still times out after its own max target ticks"
        );
        assertEquals(2, navigator.pathTicks, "both distinct targets receive an active pickup tick");
        assertEquals(1, navigator.stopTicks, "only the stale replacement timeout stops navigation");
    }

    private static void plansShulkerExtractionFallbacks() {
        assertEquals(
            Phase.PLACING_ENDER_CHEST,
            PlaneReplenishDecisions.afterShulkerRemoved(1),
            "loose ender chest after shulker break resumes ender chest placement"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            PlaneReplenishDecisions.afterShulkerRemoved(0),
            "no loose ender chests after shulker break reports missing source"
        );
        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.shulkerExtractionUnavailable(2, 3),
            "partial extraction should still break placed shulker"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            PlaneReplenishDecisions.shulkerExtractionUnavailable(2, 2),
            "no confirmed extraction reports missing ender chest"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.unavailableShulkerSource(true),
            "available shulker source keeps placement phase active"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            PlaneReplenishDecisions.unavailableShulkerSource(false),
            "missing shulker source reports missing shulker"
        );
    }

    private static void classifiesEnderChestShulkerSources() {
        EnderChestShulkerSourceScan hotbar = new EnderChestShulkerSourceScan(2, 1, -1, false, false, 1, 8);
        assertTrue(hotbar.hasHotbarSource(), "hotbar shulker source is usable");
        assertTrue(hotbar.hasVisibleSource(), "hotbar shulker source is visible");
        assertFalse(hotbar.hasMainInventorySource(), "hotbar-only shulker source is not promotable");

        EnderChestShulkerSourceScan main = new EnderChestShulkerSourceScan(-1, 0, 12, false, false, 1, 16);
        assertFalse(main.hasHotbarSource(), "main inventory shulker source is not immediately usable");
        assertTrue(main.hasMainInventorySource(), "main inventory shulker source is promotable");
        assertTrue(main.hasVisibleSource(), "main inventory shulker source prevents missing source");

        EnderChestShulkerSourceScan offhand = new EnderChestShulkerSourceScan(-1, 0, -1, true, false, 1, 4);
        assertTrue(offhand.hasVisibleSource(), "offhand shulker source prevents missing source");

        EnderChestShulkerSourceScan cursor = new EnderChestShulkerSourceScan(-1, 0, -1, false, true, 1, 4);
        assertTrue(cursor.hasVisibleSource(), "cursor shulker source prevents missing source");

        assertFalse(EnderChestShulkerSourceScan.EMPTY.hasVisibleSource(), "empty scan has no visible source");
    }

    private static void debouncesMissingEnderChestShulkerSource() {
        EnderChestShulkerSourceMisses misses = new EnderChestShulkerSourceMisses(3);
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            misses.phase(EnderChestShulkerSourceScan.EMPTY, false),
            "client-not-ready miss stays in shulker placement"
        );
        assertEquals(0, misses.consecutiveReadyMisses(), "client-not-ready miss does not increment counter");
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            misses.phase(EnderChestShulkerSourceScan.EMPTY, true),
            "first ready miss stays in shulker placement"
        );
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            misses.phase(EnderChestShulkerSourceScan.EMPTY, true),
            "second ready miss stays in shulker placement"
        );
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            misses.phase(EnderChestShulkerSourceScan.EMPTY, true),
            "repeated ready misses report missing shulker source"
        );

        EnderChestShulkerSourceScan source = new EnderChestShulkerSourceScan(-1, 0, 12, false, false, 1, 16);
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            misses.phase(source, true),
            "visible source returns to shulker placement"
        );
        assertEquals(0, misses.consecutiveReadyMisses(), "visible source resets missing counter");
    }

    private static void tracksShulkerExtractionSession() {
        ShulkerExtractionSession session = new ShulkerExtractionSession();
        session.ensureBaseline(2);
        session.ensureBaseline(4);
        assertEquals(2, session.baseline(), "baseline is only captured once");
        assertFalse(session.complete(2, 3), "baseline alone is not complete");
        assertTrue(session.complete(3, 3), "confirmed extraction to needed count is complete");
        assertEquals(
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            session.unavailablePhase(3),
            "confirmed partial extraction breaks shulker"
        );
        assertEquals(2, session.baseline(), "partial extraction keeps baseline until shulker cleanup");
        assertEquals(
            Phase.MISSING_ENDER_CHEST,
            session.unavailablePhase(2),
            "no extraction reports missing ender chest"
        );
        assertEquals(-1, session.baseline(), "missing ender chest resets baseline");
        session.ensureBaseline(5);
        assertEquals(
            Phase.PLACING_ENDER_CHEST_SHULKER,
            session.missingSourcePhase(true),
            "available shulker source keeps placement phase active"
        );
        assertEquals(5, session.baseline(), "available shulker source does not reset extraction baseline");
        assertEquals(
            Phase.MISSING_ENDER_CHEST_SHULKER,
            session.missingSourcePhase(false),
            "missing shulker source resets and reports missing shulker"
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

    private static final class FakePickupNavigator implements PlaneDroppedItemPickupWorkflow.Navigator<Object> {
        private int pathTicks;
        private int stopTicks;

        @Override
        public void pathTo(Object target) {
            pathTicks++;
        }

        @Override
        public void stop() {
            stopTicks++;
        }
    }
}
