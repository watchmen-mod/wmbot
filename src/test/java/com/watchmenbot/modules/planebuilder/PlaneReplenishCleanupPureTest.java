package com.watchmenbot.modules.planebuilder;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneReplenishCleanupPureTest {
    private PlaneReplenishCleanupPureTest() {
    }

    static void run() {
        plansReplenishDropCleanupTransitions();
        plansGenericDroppedItemPickupFallback();
        prioritizesManagedShulkerPickupBeforeGenericCleanup();
        exitsPickupWhenCleanupDropCannotFit();
        matchesReplenishCleanupStacks();
        plansCleanupDropPickupCapacity();
        plansEnderChestPickupCapacityWithReservedShulkerSlot();
        matchesTrashStacks();
        tracksTrashDropWait();
        plansTrashFallWaitDecisions();
        timesOutPersistentTrashDropWait();
        tracksTrashCleanupCycleExhaustion();
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
        PlaneTestPickupNavigator navigator = new PlaneTestPickupNavigator();
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
        PlaneTestPickupNavigator timeoutNavigator = new PlaneTestPickupNavigator();
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

    private static void prioritizesManagedShulkerPickupBeforeGenericCleanup() {
        Object shulkerDrop = new Object();
        Object obsidianDrop = new Object();
        PlaneTestPickupNavigator shulkerNavigator = new PlaneTestPickupNavigator();
        PlaneTestPickupNavigator obsidianNavigator = new PlaneTestPickupNavigator();
        PlaneDroppedItemPickupWorkflow<Object> shulkerPickup = new PlaneDroppedItemPickupWorkflow<>(
            () -> shulkerDrop,
            item -> item == shulkerDrop,
            item -> true,
            shulkerNavigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.PICKING_UP_REPLENISH_DROPS,
            0,
            10,
            true
        );
        PlaneDroppedItemPickupWorkflow<Object> obsidianCleanup = new PlaneDroppedItemPickupWorkflow<>(
            () -> obsidianDrop,
            item -> item == obsidianDrop,
            item -> true,
            obsidianNavigator,
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.MOVING_TO_TRASH_EDGE,
            0,
            10
        );

        Phase next = shulkerPickup.hasTarget() ? shulkerPickup.tick() : obsidianCleanup.tick();

        assertEquals(
            Phase.PICKING_UP_REPLENISH_DROPS,
            next,
            "managed shulker pickup stays in cleanup phase"
        );
        assertEquals(1, shulkerNavigator.pathTicks, "managed shulker pickup paths first");
        assertEquals(0, obsidianNavigator.pathTicks, "generic obsidian cleanup waits while managed shulker is available");
    }

    private static void exitsPickupWhenCleanupDropCannotFit() {
        Object target = new Object();
        PlaneTestPickupNavigator pickupableNavigator = new PlaneTestPickupNavigator();
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

        PlaneTestPickupNavigator fullInventoryNavigator = new PlaneTestPickupNavigator();
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

    private static void plansEnderChestPickupCapacityWithReservedShulkerSlot() {
        assertTrue(
            PlaneInventoryQueries.enderChestPickupPreservesShulkerSlot(true, 1),
            "partial ender chest stack accepts pickup while preserving one shulker slot"
        );
        assertTrue(
            PlaneInventoryQueries.enderChestPickupPreservesShulkerSlot(false, 2),
            "two empty slots allow one ender chest pickup and one reserved shulker slot"
        );
        assertFalse(
            PlaneInventoryQueries.enderChestPickupPreservesShulkerSlot(false, 1),
            "only empty slot is reserved for the shulker drop"
        );
        assertFalse(
            PlaneInventoryQueries.enderChestPickupPreservesShulkerSlot(true, 0),
            "full inventory cannot preserve a shulker pickup slot"
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
}
