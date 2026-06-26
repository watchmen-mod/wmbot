package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.entity.Entity;

final class PlaneBowDefenseWorkflow {
    private static final int MAX_AIM_WAIT_TICKS = 30;
    private static final int REQUIRED_STABLE_DIRECT_HIT_TICKS = 2;
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

    private State state = State.IDLE;
    private int chargeTicks;
    private int aimWaitTicks;
    private int stableDirectHitTicks;
    private int lockedTargetId = -1;
    private int logThrottleTicks;

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

        if (state != State.IDLE) {
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

    void reset() {
        stop();
        logThrottleTicks = 0;
    }

    void releasePassiveLatch() {
        moduleSession.stopShot();
    }

    private boolean start(FindItemResult bow, Entity target) {
        if (!moduleSession.start(bow)) return false;

        chargeTicks = 0;
        aimWaitTicks = 0;
        stableDirectHitTicks = 0;
        lockedTargetId = target.getId();

        useActions.holdUse();
        state = State.CHARGING;
        logInfo("Bow defense charging target %s.", target.getName().getString());
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

        Entity target = targeting.lockedTarget(lockedTargetId);
        PlaneBowTargeting.BowTargetStatus targetStatus = targeting.bowTargetStatus(target, settings.range().get());
        if (targetStatus != PlaneBowTargeting.BowTargetStatus.READY) {
            stableDirectHitTicks = 0;
            logInfo("Bow defense cancelled: %s.", targetStatus.logReason());
            stopShot(moduleSession.passiveLatched());
            return false;
        }

        useActions.holdUse();
        chargeTicks++;
        if (!aimController.aimAt(target, chargeTicks)) {
            stableDirectHitTicks = 0;
            logInfo("Bow defense waiting: no ballistic aim solution for %s.", target.getName().getString());
        }
        if (chargeTicks < settings.chargeTicks().get()) return true;

        PlaneBowShotSimulator.ShotPrediction shotPrediction = shotSimulator.simulatedFirstHit(target);
        stableDirectHitTicks = shotPrediction == PlaneBowShotSimulator.ShotPrediction.DIRECT_TARGET
            ? stableDirectHitTicks + 1
            : 0;
        if (PlaneBowDefenseDecisions.shouldRelease(
            chargeTicks,
            settings.chargeTicks().get(),
            stableDirectHitTicks,
            REQUIRED_STABLE_DIRECT_HIT_TICKS
        )) {
            logInfo("Bow defense releasing direct-hit shot at %s.", target.getName().getString());
            useActions.release();
            stopShot(moduleSession.passiveLatched());
            return true;
        }
        if (shotPrediction != PlaneBowShotSimulator.ShotPrediction.DIRECT_TARGET) {
            logInfo("Bow defense waiting: %s.", shotPrediction.logReason());
        }

        aimWaitTicks++;
        if (PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(aimWaitTicks, MAX_AIM_WAIT_TICKS)) {
            String targetName = target.getName().getString();
            stopShot(moduleSession.passiveLatched());
            logInfo("Bow defense cancelled shot at %s after aim timeout.", targetName);
            return false;
        }

        return true;
    }

    private void stop() {
        if (state == State.IDLE && !moduleSession.active()) return;

        useActions.clearUse();
        moduleSession.stopAll();
        chargeTicks = 0;
        aimWaitTicks = 0;
        stableDirectHitTicks = 0;
        lockedTargetId = -1;
        state = State.IDLE;
    }

    private void logInfo(String message, Object... args) {
        if (logThrottleTicks > 0) return;

        logger.info(message, args);
        logThrottleTicks = LOG_THROTTLE_TICKS;
    }

    private void stopShot(boolean keepPassiveLatch) {
        if (state == State.IDLE && !moduleSession.active()) return;

        useActions.clearUse();
        if (keepPassiveLatch) moduleSession.stopShot();
        else moduleSession.stopAll();
        chargeTicks = 0;
        aimWaitTicks = 0;
        stableDirectHitTicks = 0;
        lockedTargetId = -1;
        state = State.IDLE;
    }

    private enum State {
        IDLE,
        CHARGING
    }
}
