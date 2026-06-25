package com.watchmenbot.modules.stash;

import java.util.List;
import java.util.Map;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertFalse;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertNull;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.container;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.fancyCacheFile;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.shulker;
import static com.watchmenbot.modules.stash.StashKitbotTestFixtures.target;

final class StashKitbotScannerPureTest {
    private StashKitbotScannerPureTest() {
    }

    static void run() {
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
}
