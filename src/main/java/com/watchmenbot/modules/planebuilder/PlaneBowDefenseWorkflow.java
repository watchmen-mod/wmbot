package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.entity.Entity;

import java.util.Optional;

final class PlaneBowDefenseWorkflow {
    private static final int MAX_AIM_SETTLE_TICKS = 50;
    private static final int RECOVER_TICKS = 5;
    private static final int LOG_THROTTLE_TICKS = 20;

    private final PlaneBuilderSettings.BowDefense settings;
    private final PlaneActionGuards guards;
    private final PlaneInventory inventory;
    private final PlaneBowTargeting targeting = new PlaneBowTargeting();
    private final PlaneBowAimController aimController;
    private final PlaneBowShotSimulator shotSimulator = new PlaneBowShotSimulator();
    private final PlaneBowModuleSession moduleSession = new PlaneBowModuleSession();
    private final PlaneBowUseActions useActions = new PlaneBowUseActions();
    private final WorkflowLogger logger;

    private BowState state = BowState.IDLE;
    private int stateTicks;
    private int useTicks;
    private int lastUseTicks;
    private int unchangedUseTicks;
    private int aimSettleTicks;
    private int stableDirectHitTicks;
    private int fallbackAimTicks;
    private int lockedTargetId = -1;
    private int logThrottleTicks;
    private boolean releaseQueued;
    private PlaneBowAimController.Aim lastAim;

    PlaneBowDefenseWorkflow(
        PlaneBuilderSettings.BowDefense settings,
        PlaneActionGuards guards,
        PlaneInventory inventory
    ) {
        this(settings, guards, inventory, new PlaneEndermanLookSafety(), PlaneWorkflowLoggers.NOOP);
    }

    PlaneBowDefenseWorkflow(
        PlaneBuilderSettings.BowDefense settings,
        PlaneActionGuards guards,
        PlaneInventory inventory,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger
    ) {
        this(settings, guards, inventory, PlaneRuntimeConfig.DEFAULT, endermanLookSafety, logger);
    }

    PlaneBowDefenseWorkflow(
        PlaneBuilderSettings.BowDefense settings,
        PlaneActionGuards guards,
        PlaneInventory inventory,
        PlaneRuntimeConfig config,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger
    ) {
        this.settings = settings;
        this.guards = guards;
        this.inventory = inventory;
        this.aimController = new PlaneBowAimController(config);
        this.logger = logger;
    }

    boolean tick(boolean replenishActive) {
        return tickResult(replenishActive).active();
    }

    BowDefenseTickResult tickResult(boolean replenishActive) {
        return tickResult(replenishActive, false);
    }

    BowDefenseTickResult tickResult(boolean replenishActive, boolean passiveReplenishWindow) {
        if (logThrottleTicks > 0) logThrottleTicks--;

        if (state != BowState.IDLE) {
            return new BowDefenseTickResult(tickActiveShot(replenishActive));
        }

        if (!guards.clientReady()) {
            stopShot(passiveReplenishWindow);
            return new BowDefenseTickResult(false);
        }

        Entity target = targeting.nearestSafeBowTarget(settings.range().get());
        FindItemResult bow = target == null ? inventory.findHotbarBow() : inventory.prepareUsableBow();
        boolean canRun = PlaneBowDefenseDecisions.canRun(
            settings.enabled().get(),
            replenishActive,
            guards.readyForBowStart(),
            bow != null && bow.isHotbar(),
            inventory.hasArrows(),
            target != null
        );

        if (!canRun) {
            stopShot(passiveReplenishWindow);
            return new BowDefenseTickResult(false);
        }

        if (!start(bow, target)) {
            stopShot(passiveReplenishWindow);
            return new BowDefenseTickResult(false);
        }

        return new BowDefenseTickResult(true);
    }

    boolean hasSafetyOpportunity(boolean replenishActive) {
        if (!guards.clientReady()) return false;

        Entity target = targeting.nearestSafeBowTarget(settings.range().get());
        return PlaneBowDefenseDecisions.canRun(
            settings.enabled().get(),
            replenishActive,
            true,
            inventory.findHotbarBow() != null || inventory.findMainInventoryBowSlot() >= 0,
            inventory.hasArrows(),
            target != null
        );
    }

    boolean hasImmediateThreat(double range) {
        return guards.clientReady() && targeting.nearbyHostileThreat(range);
    }

    void reset() {
        stop();
        logThrottleTicks = 0;
    }

    void releasePassiveLatch() {
        moduleSession.stopShot();
    }

    private boolean start(FindItemResult bow, Entity target) {
        if (!moduleSession.start(bow)) return false;

        resetShotCounters();
        lockedTargetId = target.getId();
        useActions.holdUse();
        if (!useActions.mainHandBow()) {
            recover("Bow defense could not select bow: selectedSlot=%d mainHand=%s.", useActions.selectedSlot(), useActions.mainHandItemName());
            return true;
        }

        transition(BowState.START_DRAW);
        logInfo("Bow defense selected bow and starting draw on target %s.", target.getName().getString());
        return true;
    }

    private boolean tickActiveShot(boolean replenishActive) {
        boolean canContinue = PlaneBowDefenseDecisions.canContinue(
            guards.readyForBowContinue(),
            replenishActive
        );
        if (!canContinue) {
            stopShot(moduleSession.passiveLatched());
            return false;
        }
        if (releaseQueued) {
            useActions.holdUse();
            return true;
        }
        if (state == BowState.RECOVER) return tickRecover();

        Entity target = targeting.lockedTarget(lockedTargetId);
        PlaneBowTargeting.BowTargetStatus targetStatus = targeting.bowTargetStatus(target, settings.range().get());
        if (targetStatus != PlaneBowTargeting.BowTargetStatus.READY) {
            recover("Bow defense cancelled: %s useTicks=%d selectedSlot=%d mainHand=%s.",
                targetStatus.logReason(),
                useTicks,
                useActions.selectedSlot(),
                useActions.mainHandItemName()
            );
            return false;
        }

        return switch (state) {
            case START_DRAW -> tickStartDraw(target);
            case DRAWING -> tickDrawing(target);
            case AIM_SETTLE -> tickAimSettle(target);
            case RELEASE -> tickRelease();
            case IDLE, RECOVER -> false;
        };
    }

    private boolean tickStartDraw(Entity target) {
        useActions.holdUse();
        stateTicks++;
        refreshUseTicks();

        if (!useActions.mainHandBow()) {
            recover("Bow defense draw start failed: bow not in main hand selectedSlot=%d mainHand=%s using=%s useTicks=%d.",
                useActions.selectedSlot(),
                useActions.mainHandItemName(),
                useActions.usingItem(),
                useTicks
            );
            return true;
        }

        if (PlaneBowFiringPolicy.drawStarted(useActions.usingItem(), useTicks)) {
            transition(BowState.DRAWING);
            logInfo("Bow defense draw confirmed: target=%s useTicks=%d selectedSlot=%d.", target.getName().getString(), useTicks, useActions.selectedSlot());
            return true;
        }

        if (PlaneBowFiringPolicy.drawStartTimedOut(stateTicks)) {
            recover("Bow defense draw did not start: target=%s selectedSlot=%d mainHand=%s using=%s useTicks=%d.",
                target.getName().getString(),
                useActions.selectedSlot(),
                useActions.mainHandItemName(),
                useActions.usingItem(),
                useTicks
            );
        }

        return true;
    }

    private boolean tickDrawing(Entity target) {
        useActions.holdUse();
        stateTicks++;
        refreshUseTicks();

        if (!useActions.mainHandBow()) {
            recover("Bow defense draw failed: bow left main hand selectedSlot=%d mainHand=%s using=%s useTicks=%d.",
                useActions.selectedSlot(),
                useActions.mainHandItemName(),
                useActions.usingItem(),
                useTicks
            );
            return true;
        }

        if (PlaneBowFiringPolicy.drawStalled(useActions.usingItem(), useTicks, lastUseTicks, unchangedUseTicks)) {
            recover("Bow defense draw stalled: selectedSlot=%d mainHand=%s using=%s useTicks=%d lastUseTicks=%d unchangedTicks=%d.",
                useActions.selectedSlot(),
                useActions.mainHandItemName(),
                useActions.usingItem(),
                useTicks,
                lastUseTicks,
                unchangedUseTicks
            );
            return true;
        }

        aimController.aimAt(target, useTicks);
        if (PlaneBowFiringPolicy.enoughDraw(useTicks)) {
            transition(BowState.AIM_SETTLE);
            logInfo("Bow defense full draw confirmed: target=%s useTicks=%d distance=%.1f.", target.getName().getString(), useTicks, target.distanceTo(useActions.player()));
        }

        return true;
    }

    private boolean tickAimSettle(Entity target) {
        useActions.holdUse();
        stateTicks++;
        refreshUseTicks();

        if (PlaneBowFiringPolicy.drawStalled(useActions.usingItem(), useTicks, lastUseTicks, unchangedUseTicks)) {
            recover("Bow defense draw stalled during aim settle: target=%s useTicks=%d lastUseTicks=%d unchangedTicks=%d.",
                target.getName().getString(),
                useTicks,
                lastUseTicks,
                unchangedUseTicks
            );
            return true;
        }

        Optional<PlaneBowAimController.Aim> aim = aimController.aim(target, useTicks);
        if (aim.isEmpty()) {
            resetAimConfidence();
            logInfo("Bow defense waiting: no stable ballistic aim solution for %s at useTicks=%d.", target.getName().getString(), useTicks);
            return aimSettleTimedOut(target);
        }

        PlaneBowAimController.Aim currentAim = aim.get();
        boolean stableAim = PlaneBowFiringPolicy.aimStable(lastAim, currentAim);
        aimSettleTicks = stableAim ? aimSettleTicks + 1 : 1;
        lastAim = currentAim;
        aimController.rotate(currentAim, null);

        PlaneBowShotSimulator.ShotPrediction shotPrediction = shotSimulator.simulatedFirstHit(target);
        stableDirectHitTicks = shotPrediction == PlaneBowShotSimulator.ShotPrediction.DIRECT_TARGET
            ? stableDirectHitTicks + 1
            : 0;
        double targetVelocitySquared = target.getVelocity().lengthSquared();
        fallbackAimTicks = shotPrediction == PlaneBowShotSimulator.ShotPrediction.UNAVAILABLE && stableAim
            ? fallbackAimTicks + 1
            : 0;

        if (PlaneBowFiringPolicy.shouldReleaseDirect(useTicks, aimSettleTicks, stableDirectHitTicks)) {
            return queueReleaseAfterAimedRotation(
                target,
                currentAim,
                "Bow defense releasing confirmed direct-hit shot at %s useTicks=%d distance=%.1f settleTicks=%d.",
                target.getName().getString(),
                useTicks,
                target.distanceTo(useActions.player()),
                aimSettleTicks
            );
        }
        if (PlaneBowFiringPolicy.shouldReleaseFallback(useTicks, aimSettleTicks, fallbackAimTicks, targetVelocitySquared)) {
            return queueReleaseAfterAimedRotation(
                target,
                currentAim,
                "Bow defense releasing simulator-unavailable fallback shot at %s useTicks=%d distance=%.1f settleTicks=%d targetSpeedSq=%.4f.",
                target.getName().getString(),
                useTicks,
                target.distanceTo(useActions.player()),
                aimSettleTicks,
                targetVelocitySquared
            );
        }

        if (shotPrediction == PlaneBowShotSimulator.ShotPrediction.UNAVAILABLE) {
            logInfo("Bow defense settling fallback aim: target=%s useTicks=%d settleTicks=%d fallbackTicks=%d targetSpeedSq=%.4f.",
                target.getName().getString(),
                useTicks,
                aimSettleTicks,
                fallbackAimTicks,
                targetVelocitySquared
            );
        }
        else if (shotPrediction != PlaneBowShotSimulator.ShotPrediction.DIRECT_TARGET) {
            logInfo("Bow defense waiting: %s useTicks=%d settleTicks=%d.", shotPrediction.logReason(), useTicks, aimSettleTicks);
        }

        return aimSettleTimedOut(target);
    }

    private boolean tickRelease() {
        useActions.holdUse();
        return true;
    }

    private boolean tickRecover() {
        stateTicks++;
        if (stateTicks >= RECOVER_TICKS) {
            resetToIdle();
            return false;
        }

        return true;
    }

    private boolean aimSettleTimedOut(Entity target) {
        if (stateTicks < MAX_AIM_SETTLE_TICKS) return true;

        recover("Bow defense cancelled shot after aim-settle timeout: target=%s useTicks=%d settleTicks=%d directTicks=%d fallbackTicks=%d.",
            target.getName().getString(),
            useTicks,
            aimSettleTicks,
            stableDirectHitTicks,
            fallbackAimTicks
        );
        return true;
    }

    private void stop() {
        if (state == BowState.IDLE && !moduleSession.active()) return;

        useActions.clearUse();
        moduleSession.stopAll();
        resetToIdle();
    }

    private void logInfo(String message, Object... args) {
        if (logThrottleTicks > 0) return;

        logger.info(message, args);
        logThrottleTicks = LOG_THROTTLE_TICKS;
    }

    private void stopShot(boolean keepPassiveLatch) {
        if (state == BowState.IDLE && !moduleSession.active()) return;

        useActions.clearUse();
        if (keepPassiveLatch) moduleSession.stopShot();
        else moduleSession.stopAll();
        resetToIdle();
    }

    private boolean queueReleaseAfterAimedRotation(Entity target, PlaneBowAimController.Aim aim, String message, Object... args) {
        int expectedTargetId = target.getId();
        releaseQueued = true;
        transition(BowState.RELEASE);
        if (!aimController.rotate(aim, () -> releaseQueuedShot(expectedTargetId, message, args))) {
            releaseQueued = false;
            transition(BowState.AIM_SETTLE);
            return true;
        }

        useActions.holdUse();
        return true;
    }

    private void releaseQueuedShot(int expectedTargetId, String message, Object... args) {
        if (state != BowState.RELEASE || lockedTargetId != expectedTargetId || !releaseQueued) return;

        logInfo(message, args);
        useActions.release();
        stopShot(moduleSession.passiveLatched());
    }

    private void transition(BowState nextState) {
        state = nextState;
        stateTicks = 0;
    }

    private void refreshUseTicks() {
        int previous = useTicks;
        int current = useActions.useTicks();
        useTicks = current;
        if (current <= previous) unchangedUseTicks++;
        else unchangedUseTicks = 0;
        lastUseTicks = previous;
    }

    private void resetShotCounters() {
        stateTicks = 0;
        useTicks = 0;
        lastUseTicks = 0;
        unchangedUseTicks = 0;
        aimSettleTicks = 0;
        stableDirectHitTicks = 0;
        fallbackAimTicks = 0;
        lockedTargetId = -1;
        releaseQueued = false;
        lastAim = null;
    }

    private void resetAimConfidence() {
        aimSettleTicks = 0;
        stableDirectHitTicks = 0;
        fallbackAimTicks = 0;
        lastAim = null;
    }

    private void resetToIdle() {
        resetShotCounters();
        state = BowState.IDLE;
    }

    private void recover(String message, Object... args) {
        logger.warning(message, args);
        useActions.clearUse();
        moduleSession.stopAll();
        releaseQueued = false;
        resetAimConfidence();
        transition(BowState.RECOVER);
    }

    private enum BowState {
        IDLE,
        START_DRAW,
        DRAWING,
        AIM_SETTLE,
        RELEASE,
        RECOVER
    }
}
