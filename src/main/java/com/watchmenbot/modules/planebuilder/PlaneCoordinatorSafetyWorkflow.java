package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlaneCoordinatorSafetyWorkflow {
    private final PlaneActionGuards guards;
    private final PlaneAreaScanner scanner;
    private final PlaneAutoWalkController autoWalk;
    private final PlaneHoleEscapeController holeEscape;
    private final PlaneBuildLoop buildLoop;
    private final PlaneReplenishWorkflow replenish;
    private final PlaneMeleeDefenseWorkflow meleeDefense;
    private final PlaneBowDefenseWorkflow bowDefense;
    private final PlaneEndermanLookSafety endermanLookSafety;
    private final Callbacks callbacks;

    PlaneCoordinatorSafetyWorkflow(
        PlaneActionGuards guards,
        PlaneAreaScanner scanner,
        PlaneAutoWalkController autoWalk,
        PlaneHoleEscapeController holeEscape,
        PlaneBuildLoop buildLoop,
        PlaneReplenishWorkflow replenish,
        PlaneMeleeDefenseWorkflow meleeDefense,
        PlaneBowDefenseWorkflow bowDefense,
        PlaneEndermanLookSafety endermanLookSafety,
        Callbacks callbacks
    ) {
        this.guards = guards;
        this.scanner = scanner;
        this.autoWalk = autoWalk;
        this.holeEscape = holeEscape;
        this.buildLoop = buildLoop;
        this.replenish = replenish;
        this.meleeDefense = meleeDefense;
        this.bowDefense = bowDefense;
        this.endermanLookSafety = endermanLookSafety;
        this.callbacks = callbacks;
    }

    boolean pauseOutsideBuildArea(BlockPos playerPos) {
        if (scanner.insideBuildArea(playerPos.getX(), playerPos.getZ())) return false;

        autoWalk.reset();
        buildLoop.reset();
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDown();
        callbacks.setPhase(Phase.OUTSIDE_AREA);
        return true;
    }

    boolean continueAutoElytraLanding(BlockPos playerPos) {
        if (!PlaneCoordinatorTickPolicy.shouldContinueAutoElytraLanding(callbacks.currentPhase())) return false;

        Phase landingPhase = autoWalk.landBeforeWorldAction(playerPos, PlaneAutoWalkController.LockoutReason.NONE);
        if (landingPhase == Phase.IDLE) {
            callbacks.setPhase(Phase.IDLE);
            return false;
        }

        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        callbacks.setPhase(landingPhase);
        return true;
    }

    boolean tickSafetyBeforeReplenish(BlockPos playerPos) {
        Phase displayPhase = callbacks.currentPhase();

        if (meleeDefense.hasImmediateThreat()) {
            if (preemptManagedScreenForMeleeThreat()) return true;
            if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.SAFETY)) return true;
            autoWalk.suspend();
            holeEscape.reset();
            bowDefense.reset();
            meleeDefense.tick();
            endermanLookSafety.lookDown();
            tickReplenishDuringSafetyPreemption(displayPhase);
            callbacks.setPhase(Phase.IDLE);
            return true;
        }

        if (bowDefense.hasImmediateThreat(24.0)) {
            if (preemptManagedScreenForThreat()) return true;
            if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.BOW_DEFENSE)) return true;
            autoWalk.suspend();
            holeEscape.reset();
            endermanLookSafety.lookDown();
            tickReplenishDuringSafetyPreemption(displayPhase);
            callbacks.setPhase(Phase.IDLE);
            return true;
        }

        if (PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(displayPhase, false, guards.playerUsingItem())) {
            if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.SAFETY)) return true;
            autoWalk.suspend();
            holeEscape.reset();
            bowDefense.reset();
            endermanLookSafety.lookDown();
            tickReplenishDuringSafetyPreemption(displayPhase);
            return true;
        }

        boolean bowReplenishActive = PlanePhasePolicy.bowDefenseReplenishActive(displayPhase, replenish.active());
        boolean passiveBowDefenseWindow = PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(displayPhase);
        if (passiveBowDefenseWindow) {
            if (preemptManagedScreenForBowSafety(bowReplenishActive)) return true;

            if (PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(displayPhase, bowDefense.hasSafetyOpportunity(bowReplenishActive), false)) {
                if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.BOW_DEFENSE)) return true;
                autoWalk.suspend();
                holeEscape.reset();
                endermanLookSafety.lookDown();
                callbacks.setPhase(Phase.IDLE);
                tickReplenishDuringSafetyPreemption(displayPhase);
                return true;
            }
        }
        else {
            bowDefense.releasePassiveLatch();
        }

        return false;
    }

    boolean tickHoleEscapeBeforeReplenish(BlockPos playerPos) {
        if (!PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(replenish.phase())) return false;

        if (!tickHoleEscapeOwner(playerPos)) return false;

        replenish.pauseMovement();
        return true;
    }

    boolean tickHoleEscapeOwner(BlockPos playerPos) {
        if (!guards.readyForWorldAction()) {
            holeEscape.reset();
            return false;
        }

        Phase escapePhase = holeEscape.tick(playerPos);
        if (escapePhase != Phase.HOLE_ESCAPE) return false;

        autoWalk.reset();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.HOLE_ESCAPE);
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        callbacks.setPhase(escapePhase);
        return true;
    }

    boolean tickMeleeDefenseOwner(PlaneCoordinatorTickPolicy.TickOwner owner, BlockPos playerPos) {
        if (owner != PlaneCoordinatorTickPolicy.TickOwner.MELEE_DEFENSE) return false;

        if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.SAFETY)) return true;
        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.SAFETY);
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        callbacks.setPhase(Phase.IDLE);
        return true;
    }

    boolean tickBowDefenseOwner(PlaneCoordinatorTickPolicy.TickOwner owner, BlockPos playerPos) {
        if (owner != PlaneCoordinatorTickPolicy.TickOwner.BOW_DEFENSE) return false;

        if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.BOW_DEFENSE)) return true;
        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        holeEscape.reset();
        endermanLookSafety.lookDownIfUnsafe();
        callbacks.setPhase(Phase.IDLE);
        return true;
    }

    boolean tickGuardPausedOwner(PlaneCoordinatorTickPolicy.TickOwner owner, BlockPos playerPos) {
        if (owner != PlaneCoordinatorTickPolicy.TickOwner.GUARD_PAUSED) return false;

        if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.GUARD_PAUSED)) return true;
        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.GUARD_PAUSED);
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDown();
        return true;
    }

    private void tickReplenishDuringSafetyPreemption(Phase displayPhase) {
        if (!PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(displayPhase)) return;

        callbacks.syncBowDefenseAfterReplenishTick(replenish.tickResult());
    }

    private boolean preemptManagedScreenForBowSafety(boolean bowReplenishActive) {
        if (!guards.managedScreenOpen()) return false;
        if (!bowDefense.hasSafetyOpportunity(bowReplenishActive)) return false;

        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        holeEscape.reset();
        endermanLookSafety.lookDown();
        if (guards.safeToCloseManagedScreen()) guards.closeManagedScreenForSafety();
        return true;
    }

    private boolean preemptManagedScreenForMeleeThreat() {
        if (!guards.managedScreenOpen()) return false;

        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.SAFETY);
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDown();
        if (guards.safeToCloseManagedScreen()) guards.closeManagedScreenForSafety();
        return true;
    }

    private boolean preemptManagedScreenForThreat() {
        if (!guards.managedScreenOpen()) return false;

        autoWalk.suspend();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        holeEscape.reset();
        endermanLookSafety.lookDown();
        if (guards.safeToCloseManagedScreen()) guards.closeManagedScreenForSafety();
        return true;
    }

    private boolean landAutoElytraForSafety(BlockPos playerPos, PlaneAutoWalkController.LockoutReason reason) {
        Phase landingPhase = autoWalk.landBeforeWorldAction(playerPos, reason);
        if (landingPhase == Phase.IDLE) return false;

        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        callbacks.setPhase(landingPhase);
        return true;
    }

    interface Callbacks {
        Phase currentPhase();

        void setPhase(Phase phase);

        void syncBowDefenseAfterReplenishTick(ReplenishTickResult replenishResult);
    }
}
