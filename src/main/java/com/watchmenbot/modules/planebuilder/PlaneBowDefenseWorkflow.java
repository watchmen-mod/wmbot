package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

final class PlaneBowDefenseWorkflow {
    private static final int MAX_AIM_WAIT_TICKS = 30;
    private static final int LOG_THROTTLE_TICKS = 20;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneBuilderSettings.BowDefense settings;
    private final PlaneActionGuards guards;
    private final PlaneInventory inventory;
    private final PlaneBowTargeting targeting = new PlaneBowTargeting();
    private final PlaneBowShotSimulator shotSimulator = new PlaneBowShotSimulator();
    private final PlaneBowModuleSession moduleSession = new PlaneBowModuleSession();
    private final PlaneBowUseActions useActions = new PlaneBowUseActions();
    private final WorkflowLogger logger;

    private State state = State.IDLE;
    private int chargeTicks;
    private int aimWaitTicks;
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

        if (passiveReplenishWindow && !moduleSession.startPassiveLatch(settings.range().get())) {
            stop();
            return new BowDefenseTickResult(false);
        }
        if (!passiveReplenishWindow) moduleSession.releasePassiveLatch();

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

        if (!start(bow, target, passiveReplenishWindow)) {
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
        moduleSession.releasePassiveLatch();
    }

    private boolean start(FindItemResult bow, Entity target, boolean passiveReplenishWindow) {
        if (!moduleSession.start(bow, settings.range().get(), passiveReplenishWindow)) return false;

        chargeTicks = 0;
        aimWaitTicks = 0;
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
        if (!targeting.safeBowTarget(target, settings.range().get())) {
            logInfo("Bow defense cancelled because the locked target is no longer safe.");
            stopShot(moduleSession.passiveLatched());
            return false;
        }

        useActions.holdUse();
        chargeTicks++;
        if (chargeTicks < settings.chargeTicks().get()) return true;

        boolean directHit = shotSimulator.simulatedFirstHitIsTarget(target);
        if (PlaneBowDefenseDecisions.shouldRelease(chargeTicks, settings.chargeTicks().get(), directHit)) {
            logInfo("Bow defense releasing direct-hit shot at %s.", target.getName().getString());
            useActions.release();
            stopShot(moduleSession.passiveLatched());
            return true;
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
        lockedTargetId = -1;
        state = State.IDLE;
    }

    private enum State {
        IDLE,
        CHARGING
    }
}
