package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneReplenishManagedShulkerPureTest {
    private PlaneReplenishManagedShulkerPureTest() {
    }

    static void run() {
        selectsBestEnderChestShulkerHotbarSlot();
        detectsConfirmedShulkerExtraction();
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
        PlaneTestPickupNavigator dropNavigator = new PlaneTestPickupNavigator();
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

        PlaneTestPickupNavigator missingNavigator = new PlaneTestPickupNavigator();
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
        PlaneTestPickupNavigator dropNavigator = new PlaneTestPickupNavigator();
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

        PlaneTestPickupNavigator missingNavigator = new PlaneTestPickupNavigator();
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

        PlaneTestPickupNavigator timeoutNavigator = new PlaneTestPickupNavigator();
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
        PlaneTestPickupNavigator navigator = new PlaneTestPickupNavigator();
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
        PlaneTestPickupNavigator navigator = new PlaneTestPickupNavigator();
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
}
