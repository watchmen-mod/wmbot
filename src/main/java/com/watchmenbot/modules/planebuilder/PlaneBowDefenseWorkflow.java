package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

final class PlaneBowDefenseWorkflow {
    private static final int MAX_AIM_WAIT_TICKS = 30;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneBuilderSettings.BowDefense settings;
    private final PlaneActionGuards guards;
    private final PlaneInventory inventory;
    private final PlaneBowTargeting targeting = new PlaneBowTargeting();
    private final PlaneBowShotSimulator shotSimulator = new PlaneBowShotSimulator();
    private final PlaneBowModuleSession moduleSession = new PlaneBowModuleSession();
    private final PlaneBowUseActions useActions = new PlaneBowUseActions();

    private State state = State.IDLE;
    private int chargeTicks;
    private int aimWaitTicks;
    private int lockedTargetId = -1;

    PlaneBowDefenseWorkflow(
        PlaneBuilderSettings.BowDefense settings,
        PlaneActionGuards guards,
        PlaneInventory inventory
    ) {
        this.settings = settings;
        this.guards = guards;
        this.inventory = inventory;
    }

    boolean tick(boolean replenishActive) {
        return tickResult(replenishActive).active();
    }

    BowDefenseTickResult tickResult(boolean replenishActive) {
        return tickResult(replenishActive, false);
    }

    BowDefenseTickResult tickResult(boolean replenishActive, boolean passiveReplenishWindow) {
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
    }

    void releasePassiveLatch() {
        moduleSession.releasePassiveLatch();
    }

    private boolean start(FindItemResult bow, Entity target, boolean passiveReplenishWindow) {
        if (!moduleSession.start(bow, settings.range().get(), passiveReplenishWindow)) return false;

        useActions.holdUse();
        chargeTicks = 0;
        aimWaitTicks = 0;
        lockedTargetId = target.getId();
        state = State.CHARGING;
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
            stopShot(moduleSession.passiveLatched());
            return false;
        }

        useActions.holdUse();
        chargeTicks++;
        if (chargeTicks < settings.chargeTicks().get()) return true;

        boolean directHit = shotSimulator.simulatedFirstHitIsTarget(target);
        if (PlaneBowDefenseDecisions.shouldRelease(chargeTicks, settings.chargeTicks().get(), directHit)) {
            useActions.release();
            stopShot(moduleSession.passiveLatched());
            return true;
        }

        aimWaitTicks++;
        if (PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(aimWaitTicks, MAX_AIM_WAIT_TICKS)) {
            stopShot(moduleSession.passiveLatched());
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
