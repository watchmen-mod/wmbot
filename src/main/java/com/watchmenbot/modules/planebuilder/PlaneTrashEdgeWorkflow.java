package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlaneTrashEdgeWorkflow {
    private static final double ARRIVAL_DISTANCE_SQ = 0.65 * 0.65;

    private final PlaneInventory inventory;
    private final PlaneActionGuards guards;
    private final PlaneClientContext context;
    private final PlaneBuilderSettings.Replenish replenishSettings;
    private final PlaneTrashEdgePlanner planner;
    private final PlaneTrashEdgeNavigator navigator;
    private final PlaneActionExecutor actionExecutor;
    private final PlaneDroppedItemScanner droppedTrashScanner;
    private final PlaneTrashDropWait dropWait = new PlaneTrashDropWait();
    private final PlaneTrashCleanupCycle cleanupCycle = new PlaneTrashCleanupCycle();
    private final PlaneTrashEdgeMovementWatchdog movementWatchdog = new PlaneTrashEdgeMovementWatchdog();

    private PlaneTrashEdgePlanner.Target target;

    PlaneTrashEdgeWorkflow(
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneWorldAccess world,
        PlaneClientContext context,
        PlaneRuntimeConfig config,
        PlaneBuilderSettings.Replenish replenishSettings
    ) {
        this(inventory, guards, world, context, config, replenishSettings, new PlaneEndermanLookSafety());
    }

    PlaneTrashEdgeWorkflow(
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneWorldAccess world,
        PlaneClientContext context,
        PlaneRuntimeConfig config,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this(
            inventory,
            guards,
            context,
            replenishSettings,
            new PlaneTrashEdgePlanner(
                config,
                new PlaneTrashEdgePlanner.BlockView() {
                    @Override
                    public boolean buildBlock(BlockPos pos) {
                        return world.isBlock(pos, config.buildBlock());
                    }

                    @Override
                    public boolean replaceable(BlockPos pos) {
                        return world.isReplaceable(pos);
                    }
                }
            ),
            new PlaneTrashEdgeNavigator(endermanLookSafety),
            new PlaneActionExecutor(config, endermanLookSafety),
            new PlaneDroppedItemScanner(config.scanRadius(), item -> PlaneItemClassifier.isTrashStack(item.getStack()))
        );
    }

    PlaneTrashEdgeWorkflow(
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneClientContext context,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneTrashEdgePlanner planner,
        PlaneTrashEdgeNavigator navigator,
        PlaneActionExecutor actionExecutor,
        PlaneDroppedItemScanner droppedTrashScanner
    ) {
        this.inventory = inventory;
        this.guards = guards;
        this.context = context;
        this.replenishSettings = replenishSettings;
        this.planner = planner;
        this.navigator = navigator;
        this.actionExecutor = actionExecutor;
        this.droppedTrashScanner = droppedTrashScanner;
    }

    void reset() {
        target = null;
        movementWatchdog.reset();
        dropWait.reset();
        navigator.stop();
    }

    void resetAll() {
        cleanupCycle.begin();
        reset();
    }

    void beginCleanupCycle() {
        cleanupCycle.begin();
        reset();
    }

    void pauseMovement() {
        navigator.stop();
    }

    Phase moveToEdge() {
        if (!cleanupCycle.canStartTrashEdgeCleanup()) return Phase.IDLE;
        if (!enabled() || !inventory.hasTrashItems()) {
            reset();
            return Phase.IDLE;
        }

        if (target == null) {
            target = planner.select(context.player().getBlockPos());
            movementWatchdog.reset();
            if (target == null) {
                reset();
                return Phase.IDLE;
            }
        }

        if (arrivedAtTarget()) {
            navigator.stop();
            movementWatchdog.reset();
            return Phase.DROPPING_TRASH_OFF_EDGE;
        }

        if (movementWatchdog.tickTimedOut()) {
            target = null;
            cleanupCycle.markExhausted();
            navigator.stop();
            return Phase.IDLE;
        }

        navigator.walkTo(Vec3d.ofCenter(target.standing()));
        return Phase.MOVING_TO_TRASH_EDGE;
    }

    Phase dropOffEdge() {
        navigator.stop();
        if (target == null) return Phase.MOVING_TO_TRASH_EDGE;
        if (!inventory.hasTrashItems()) {
            dropWait.start();
            return Phase.WAITING_FOR_TRASH_TO_FALL;
        }
        if (!guards.readyForHotbarMutation()) return Phase.DROPPING_TRASH_OFF_EDGE;
        if (!inventory.prepareNextTrashStackForDrop()) {
            reset();
            return Phase.IDLE;
        }

        actionExecutor.rotate(PlaneTrashEdgePlanner.dropTarget(target), inventory::dropSelectedTrashStack);
        return Phase.DROPPING_TRASH_OFF_EDGE;
    }

    Phase waitForTrashToFall() {
        navigator.stop();
        boolean inventoryHasTrashItems = inventory.hasTrashItems();
        PlaneTrashDropWait.Result result = inventoryHasTrashItems
            ? PlaneTrashDropWait.Result.WAITING
            : dropWait.tick(!droppedTrashScanner.matchingDrops().isEmpty());
        PlaneTrashEdgeDecisions.FallWaitDecision decision = PlaneTrashEdgeDecisions.fallWait(inventoryHasTrashItems, result);

        if (decision.phase() == Phase.WAITING_FOR_TRASH_TO_FALL) return decision.phase();

        if (decision.exhaustCleanupCycle()) cleanupCycle.markExhausted();
        reset();
        return decision.phase();
    }

    private boolean enabled() {
        return replenishSettings.trashHoleCleanup().get();
    }

    private boolean arrivedAtTarget() {
        Vec3d player = context.player().getPos();
        Vec3d center = Vec3d.ofCenter(target.standing());
        double dx = player.x - center.x;
        double dz = player.z - center.z;
        return dx * dx + dz * dz <= ARRIVAL_DISTANCE_SQ;
    }
}
