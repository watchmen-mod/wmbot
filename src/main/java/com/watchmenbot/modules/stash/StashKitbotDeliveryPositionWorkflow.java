package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

final class StashKitbotDeliveryPositionWorkflow {
    private static final int DIRECT_STEP_FALLBACK_TICKS = 40;
    private static final int CROSS_DIMENSION_POSITIONING_FALLBACK_TICKS = 60;
    private static final int CROSS_DIMENSION_TRACE_INTERVAL_TICKS = 40;
    static final int CROSS_DIMENSION_SETTLE_TICKS = 40;

    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashNavigator navigator;
    private final StashKitbotDelivery delivery;
    private final StashKitbotEvents events;
    private final Callbacks callbacks;

    StashKitbotDeliveryPositionWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashNavigator navigator,
        StashKitbotDelivery delivery,
        StashKitbotEvents events,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.navigator = navigator;
        this.delivery = delivery;
        this.events = events;
        this.callbacks = callbacks;
    }

    void tickReacquireRequester(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);

        correctCrossDimensionFlagIfNeeded(request, "reacquire", true);

        if (request.delivery.crossDimensionSettleTicks > 0) {
            request.delivery.crossDimensionSettleTicks--;
            logCrossDimensionWait(request, settings, "settling");
            return;
        }

        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (startMovingToDeliverySpot(request, requester, settings)) return;

        trackDeliveryMovement(request, null, null);
        delivery.stopWalking();
        navigator.stop();
        logCrossDimensionWait(request, settings, "reacquire_requester");
        if (StashKitbotDeliveryPlanner.shouldKeepReacquiringRequester(requester != null, request.delivery.requesterReacquire.tickExpired())) {
            return;
        }

        callbacks.postTeleportFailure("I teleported, but I could not see you nearby after waiting for the server transfer to finish. Heading home with the kits.");
    }

    void tickMoveToDeliverySpot(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        correctCrossDimensionFlagIfNeeded(request, "move", false);

        boolean movementExpired = request.delivery.deliveryTimeout.tickExpired();
        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (requester == null) {
            if (!movementExpired) {
                beginRequesterReacquire(settings);
                events.info("Requester %s disappeared during delivery positioning; reacquiring.", request.requester);
                return;
            }

            callbacks.postTeleportFailure("I teleported, but I cannot see you nearby. Heading home with the kits.");
            return;
        }

        if (request.delivery.crossDimensionDelivery) {
            tickCrossDimensionMoveToRequester(request, requester, settings, movementExpired);
            return;
        }

        request.delivery.deliverySpot = delivery.deliverySpot(requester, settings.deliveryDistance());
        BlockPos spot = request.delivery.deliverySpot;
        if (spot == null) {
            callbacks.postTeleportFailure("Could not choose a safe delivery spot. Heading home with the kits.");
            return;
        }

        double requesterDistanceSq = delivery.horizontalDistanceSq(requester);
        boolean closeEnoughForDirectStep = delivery.shouldStepAwayDirectly(requesterDistanceSq);
        boolean directStepExpired = closeEnoughForDirectStep && request.delivery.directStepTicks >= DIRECT_STEP_FALLBACK_TICKS;
        request.delivery.positioningTicks++;
        boolean positioningExpired = request.delivery.crossDimensionDelivery
            && request.delivery.positioningTicks >= CROSS_DIMENSION_POSITIONING_FALLBACK_TICKS;
        StashKitbotDeliveryPlanner.DeliveryPositionDecision decision = StashKitbotDeliveryPlanner.deliveryPositionDecision(
            true,
            requesterDistanceSq,
            mc.player.getBlockPos().getSquaredDistance(spot),
            settings.deliveryDistance(),
            positioningExpired,
            movementExpired,
            directStepExpired
        );

        if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW) {
            delivery.stopWalking();
            navigator.stop();
            request.delivery.directStepTicks = 0;
            request.delivery.positioningTicks = 0;
            request.delivery.throwDelay.reset(0);
            request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
            session.phase(KitbotPhase.THROWING);
            events.info("%s %d '%s' shulkers to %s from %.1f blocks away.",
                movementExpired || directStepExpired || positioningExpired ? "Delivery positioning timed out; throwing" : "Ready to throw",
                request.gather.gathered,
                request.kitName,
                request.requester,
                Math.sqrt(requesterDistanceSq)
            );
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.DIRECT_STEP_AWAY) {
            navigator.stop();
            request.delivery.directStepTicks++;
            delivery.walkAwayFrom(requester);
        }
        else if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.PATH_TO_SPOT) {
            StashKitbotDeliveryPlanner.DeliveryStuckDecision stuckDecision = tickDeliveryMovementWatchdog(request, requester, spot, settings);
            if (handleStuckDecision(request, stuckDecision, settings, "delivery positioning")) return;

            request.delivery.directStepTicks = 0;
            delivery.stopWalking();
            navigator.ensureReturnTo(spot);
        }
        else {
            request.delivery.directStepTicks = 0;
            delivery.stopWalking();
            callbacks.postTeleportFailure("Timed out moving into delivery position. Heading home with the kits.");
        }
    }

    boolean startMovingToDeliverySpot(KitRequest request, AbstractClientPlayerEntity requester, StashKitbotDeliveryWorkflow.Settings settings) {
        if (requester == null) return false;
        if (!request.delivery.crossDimensionDelivery && !delivery.isRequesterNearby(requester, effectiveRequesterSearchRadius(request, settings))) return false;

        request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
        request.delivery.directStepTicks = 0;
        request.delivery.positioningTicks = 0;
        request.delivery.deliveryTraceTicks = 0;
        request.delivery.stuckRecoveryAttempts = 0;
        resetDeliveryMovementWatchdog(request);
        request.delivery.deliverySpot = delivery.deliverySpot(requester, settings.deliveryDistance());
        session.phase(KitbotPhase.MOVE_TO_DELIVERY_SPOT);
        return true;
    }

    AbstractClientPlayerEntity findDeliveryTarget(KitRequest request, StashKitbotDeliveryWorkflow.Settings settings) {
        return delivery.findRequester(request.requester, effectiveRequesterSearchRadius(request, settings));
    }

    void logCrossDimensionWait(KitRequest request, StashKitbotDeliveryWorkflow.Settings settings, String decision) {
        if (!settings.traceLogs() || request == null || !isCrossDimensionDeliveryContext(request)) return;
        if (request.delivery.deliveryTraceTicks++ % CROSS_DIMENSION_TRACE_INTERVAL_TICKS != 0) return;

        AbstractClientPlayerEntity requester = delivery.findRequester(request.requester, effectiveRequesterSearchRadius(request, settings));
        events.info("[kitbot trace] cross-dimension delivery phase=%s dimension=%s requesterLoaded=%s decision=%s %s.",
            session.phase().name().toLowerCase(),
            StashClientUtils.dimensionId(mc),
            requester != null,
            decision,
            delivery.loadedPlayerDebug(requester)
        );
    }

    private void beginRequesterReacquire(StashKitbotDeliveryWorkflow.Settings settings) {
        delivery.stopWalking();
        navigator.stop();
        session.activeRequest().delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());
        session.phase(KitbotPhase.REACQUIRE_REQUESTER);
    }

    private int effectiveRequesterSearchRadius(KitRequest request, StashKitbotDeliveryWorkflow.Settings settings) {
        return StashKitbotDeliveryPlanner.effectiveRequesterSearchRadius(
            settings.requesterSearchRadius(),
            request.delivery.crossDimensionDelivery
        );
    }

    private void correctCrossDimensionFlagIfNeeded(KitRequest request, String phaseLabel, boolean resetSettleTicks) {
        String currentDimension = StashClientUtils.dimensionId(mc);
        if (!StashKitbotDeliveryPlanner.shouldCorrectCrossDimensionFlag(
            request.delivery.crossDimensionDelivery,
            request.delivery.preTpaDimension,
            currentDimension
        )) {
            return;
        }

        request.delivery.crossDimensionDelivery = true;
        if (resetSettleTicks) {
            request.delivery.crossDimensionSettleTicks = StashKitbotDeliveryPlanner.correctedCrossDimensionSettleTicks(
                request.delivery.crossDimensionSettleTicks,
                CROSS_DIMENSION_SETTLE_TICKS
            );
        }
        events.info("Corrected cross-dimension delivery flag for %s in %s (detected in %s phase).", request.requester, currentDimension, phaseLabel);
    }

    private void tickCrossDimensionMoveToRequester(
        KitRequest request,
        AbstractClientPlayerEntity requester,
        StashKitbotDeliveryWorkflow.Settings settings,
        boolean movementExpired
    ) {
        double requesterDistanceSq = delivery.horizontalDistanceSq(requester);
        boolean closeEnoughForDirectStep = delivery.shouldStepAwayDirectly(requesterDistanceSq);
        boolean directStepExpired = closeEnoughForDirectStep && request.delivery.directStepTicks >= DIRECT_STEP_FALLBACK_TICKS;
        StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision decision = StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(
            true,
            requesterDistanceSq,
            settings.deliveryDistance(),
            movementExpired,
            directStepExpired
        );
        logCrossDimensionWait(request, settings, "move_to_delivery_spot:" + decision.name().toLowerCase());

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.THROW_NOW) {
            delivery.stopWalking();
            navigator.stop();
            request.delivery.directStepTicks = 0;
            request.delivery.throwDelay.reset(0);
            request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
            session.phase(KitbotPhase.THROWING);
            events.info("Cross-dimension requester loaded; throwing %d '%s' shulkers to %s from %.1f blocks away.",
                request.gather.gathered,
                request.kitName,
                request.requester,
                Math.sqrt(requesterDistanceSq)
            );
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.DIRECT_STEP_AWAY) {
            StashKitbotDeliveryPlanner.DeliveryStuckDecision stuckDecision = tickDeliveryMovementWatchdog(request, requester, null, settings);
            if (handleStuckDecision(request, stuckDecision, settings, "cross-dimension direct step")) return;

            navigator.stop();
            request.delivery.directStepTicks++;
            delivery.walkAwayFrom(requester);
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.APPROACH_REQUESTER) {
            StashKitbotDeliveryPlanner.DeliveryStuckDecision stuckDecision = tickDeliveryMovementWatchdog(request, requester, null, settings);
            if (handleStuckDecision(request, stuckDecision, settings, "cross-dimension approach")) return;

            navigator.stop();
            request.delivery.directStepTicks = 0;
            delivery.walkToward(requester);
            return;
        }

        request.delivery.directStepTicks = 0;
        delivery.stopWalking();
        callbacks.postTeleportFailure("I found you after teleporting, but could not get into delivery range. Heading home with the kits.");
    }

    private StashKitbotDeliveryPlanner.DeliveryStuckDecision tickDeliveryMovementWatchdog(
        KitRequest request,
        AbstractClientPlayerEntity requester,
        BlockPos spot,
        StashKitbotDeliveryWorkflow.Settings settings
    ) {
        boolean progressed = trackDeliveryMovement(request, requester, spot);
        if (progressed) {
            request.delivery.stuckMovementTicks = 0;
            return StashKitbotDeliveryPlanner.DeliveryStuckDecision.TRACK;
        }

        request.delivery.stuckMovementTicks++;
        return StashKitbotDeliveryPlanner.deliveryStuckDecision(
            false,
            request.delivery.stuckMovementTicks,
            StashKitbotDeliveryPlanner.DELIVERY_STUCK_TICKS,
            request.delivery.stuckRecoveryAttempts,
            requester != null
        );
    }

    private boolean handleStuckDecision(
        KitRequest request,
        StashKitbotDeliveryPlanner.DeliveryStuckDecision decision,
        StashKitbotDeliveryWorkflow.Settings settings,
        String context
    ) {
        if (decision == StashKitbotDeliveryPlanner.DeliveryStuckDecision.TRACK) return false;

        delivery.stopWalking();
        navigator.stop();
        request.delivery.stuckMovementTicks = 0;
        resetDeliveryMovementWatchdog(request);

        if (decision == StashKitbotDeliveryPlanner.DeliveryStuckDecision.RESET_MOVEMENT) {
            request.delivery.stuckRecoveryAttempts++;
            request.delivery.directStepTicks = 0;
            request.delivery.positioningTicks = 0;
            traceStuckRecovery(settings, "reset", context, request);
            return false;
        }

        request.delivery.stuckRecoveryAttempts = 0;
        if (decision == StashKitbotDeliveryPlanner.DeliveryStuckDecision.THROW_NOW) {
            request.delivery.directStepTicks = 0;
            request.delivery.positioningTicks = 0;
            request.delivery.throwDelay.reset(0);
            request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
            session.phase(KitbotPhase.THROWING);
            traceStuckRecovery(settings, "throw", context, request);
            events.info("Delivery movement appeared stuck after teleport; throwing %d '%s' shulkers to %s from the current position.",
                request.gather.gathered,
                request.kitName,
                request.requester
            );
            return true;
        }

        beginRequesterReacquire(settings);
        traceStuckRecovery(settings, "reacquire", context, request);
        return true;
    }

    private boolean trackDeliveryMovement(KitRequest request, AbstractClientPlayerEntity requester, BlockPos spot) {
        if (mc.player == null) return true;

        BlockPos currentPos = mc.player.getBlockPos().toImmutable();
        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();
        double currentRequesterDistanceSq = requester == null ? Double.MAX_VALUE : delivery.horizontalDistanceSq(requester);
        BlockPos currentSpot = spot == null ? null : spot.toImmutable();
        boolean progressed = false;

        if (!Double.isNaN(request.delivery.lastMovementWatchdogX) && !Double.isNaN(request.delivery.lastMovementWatchdogZ)) {
            double movementSq = horizontalDistanceSq(
                currentX,
                currentZ,
                request.delivery.lastMovementWatchdogX,
                request.delivery.lastMovementWatchdogZ
            );
            double requesterDistanceImprovementSq = request.delivery.lastMovementWatchdogRequesterDistanceSq - currentRequesterDistanceSq;
            boolean spotChanged = !sameBlockPos(currentSpot, request.delivery.lastMovementWatchdogSpot);
            progressed = StashKitbotDeliveryPlanner.deliveryMovementProgressed(movementSq, requesterDistanceImprovementSq, spotChanged);
        }

        request.delivery.lastMovementWatchdogPos = currentPos;
        request.delivery.lastMovementWatchdogX = currentX;
        request.delivery.lastMovementWatchdogZ = currentZ;
        request.delivery.lastMovementWatchdogRequesterDistanceSq = currentRequesterDistanceSq;
        request.delivery.lastMovementWatchdogSpot = currentSpot;
        return progressed;
    }

    private void resetDeliveryMovementWatchdog(KitRequest request) {
        request.delivery.stuckMovementTicks = 0;
        request.delivery.lastMovementWatchdogPos = null;
        request.delivery.lastMovementWatchdogX = Double.NaN;
        request.delivery.lastMovementWatchdogZ = Double.NaN;
        request.delivery.lastMovementWatchdogRequesterDistanceSq = Double.MAX_VALUE;
        request.delivery.lastMovementWatchdogSpot = null;
    }

    private double horizontalDistanceSq(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private boolean sameBlockPos(BlockPos a, BlockPos b) {
        if (a == null || b == null) return a == b;
        return a.equals(b);
    }

    private void traceStuckRecovery(
        StashKitbotDeliveryWorkflow.Settings settings,
        String action,
        String context,
        KitRequest request
    ) {
        if (!settings.traceLogs()) return;

        events.info("[kitbot trace] delivery movement watchdog action=%s context=%s phase=%s requester=%s.",
            action,
            context,
            session.phase().name().toLowerCase(),
            request.requester
        );
    }

    private boolean isCrossDimensionDeliveryContext(KitRequest request) {
        return request.delivery.crossDimensionDelivery
            || StashKitbotDeliveryPlanner.dimensionChangedAfterTpa(request.delivery.preTpaDimension, StashClientUtils.dimensionId(mc));
    }

    private void lookDown(StashKitbotDeliveryWorkflow.Settings settings) {
        delivery.lookDown(settings.deliveryLookPitch());
    }

    interface Callbacks {
        void postTeleportFailure(String message);
    }
}
