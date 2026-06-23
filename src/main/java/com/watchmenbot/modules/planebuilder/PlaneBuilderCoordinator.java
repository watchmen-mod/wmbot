package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.util.math.BlockPos;

final class PlaneBuilderCoordinator {
    private final PlaneClientContext context;
    private final PlaneActionGuards guards;
    private final PlaneAreaScanner scanner;
    private final PlanePlacementStatsTracker stats;
    private final PlaneAutoWalkController autoWalk;
    private final PlaneHoleEscapeController holeEscape;
    private final PlaneBuildLoop buildLoop;
    private final PlaneReplenishWorkflow replenish;
    private final PlaneBowDefenseWorkflow bowDefense;
    private final PlaneEndermanLookSafety endermanLookSafety;

    private Phase phase = Phase.IDLE;

    PlaneBuilderCoordinator(
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.BowDefense bowDefenseSettings,
        PlaneBuilderSettings.AutoWalk autoWalkSettings,
        PlaneBuilderSettings.HoleEscape holeEscapeSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneInventoryView.PickaxeSafetyConfig pickaxeSafetyConfig,
        PlaneBuilderSettings.EndermanLookSafety endermanLookSafetySettings
    ) {
        this(
            companionModules,
            replenishSettings,
            bowDefenseSettings,
            autoWalkSettings,
            holeEscapeSettings,
            kitbotRefillSettings,
            pickaxeSafetyConfig,
            endermanLookSafetySettings,
            PlaneRuntimeConfig.DEFAULT,
            new PlaneClientContext(),
            PlaneWorkflowLoggers.NOOP
        );
    }

    PlaneBuilderCoordinator(
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.BowDefense bowDefenseSettings,
        PlaneBuilderSettings.AutoWalk autoWalkSettings,
        PlaneBuilderSettings.HoleEscape holeEscapeSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneInventoryView.PickaxeSafetyConfig pickaxeSafetyConfig,
        PlaneBuilderSettings.EndermanLookSafety endermanLookSafetySettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context
    ) {
        this(
            companionModules,
            replenishSettings,
            bowDefenseSettings,
            autoWalkSettings,
            holeEscapeSettings,
            kitbotRefillSettings,
            pickaxeSafetyConfig,
            endermanLookSafetySettings,
            config,
            context,
            PlaneWorkflowLoggers.NOOP
        );
    }

    PlaneBuilderCoordinator(
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.BowDefense bowDefenseSettings,
        PlaneBuilderSettings.AutoWalk autoWalkSettings,
        PlaneBuilderSettings.HoleEscape holeEscapeSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneInventoryView.PickaxeSafetyConfig pickaxeSafetyConfig,
        PlaneBuilderSettings.EndermanLookSafety endermanLookSafetySettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        WorkflowLogger logger
    ) {
        this(PlaneBuilderCoordinatorComponents.create(
            companionModules,
            replenishSettings,
            bowDefenseSettings,
            autoWalkSettings,
            holeEscapeSettings,
            kitbotRefillSettings,
            pickaxeSafetyConfig,
            endermanLookSafetySettings,
            config,
            context,
            logger
        ));
    }

    PlaneBuilderCoordinator(PlaneBuilderCoordinatorComponents components) {
        context = components.context();
        guards = components.guards();
        scanner = components.scanner();
        stats = components.stats();
        autoWalk = components.autoWalk();
        holeEscape = components.holeEscape();
        buildLoop = components.buildLoop();
        replenish = components.replenish();
        bowDefense = components.bowDefense();
        endermanLookSafety = components.endermanLookSafety();
    }

    void reset() {
        phase = Phase.IDLE;
        buildLoop.reset();
        autoWalk.reset();
        holeEscape.reset();
        replenish.reset();
        bowDefense.reset();
        stats.reset();
    }

    Phase phase() {
        if (phase == Phase.HOLE_ESCAPE) return phase;
        return replenish.active() ? replenish.phase() : phase;
    }

    void startStatsSession(long nowMillis) {
        stats.startSession(nowMillis);
    }

    PlaneBuilderStats.Snapshot statsSnapshot(long nowMillis) {
        return stats.snapshot(nowMillis);
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult handleMessage(String message) {
        return replenish.handleMessage(message);
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return replenish.ignoredTeleportPrompt(message);
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult consumeTeleportAcceptResult() {
        return replenish.consumeTeleportAcceptResult();
    }

    void tick() {
        if (!context.worldReady()) return;

        flushQueuedTeleportAccept();
        confirmPlacements();

        BlockPos playerPos = context.player().getBlockPos();
        if (pauseOutsideBuildArea(playerPos)) return;
        if (continueAutoElytraLanding(playerPos)) return;

        if (replenish.active() && tickSafetyBeforeReplenish(playerPos)) return;

        PlaneCoordinatorTickPolicy.TickOwner owner = PlaneCoordinatorTickPolicy.owner(replenish.active(), false, true);
        if (owner == PlaneCoordinatorTickPolicy.TickOwner.REPLENISH) {
            if (tickHoleEscapeBeforeReplenish(playerPos)) return;
            tickReplenishOwner();
            return;
        }

        if (tickHoleEscapeOwner(playerPos)) return;

        BowDefenseTickResult bowDefenseResult = bowDefense.tickResult(false);
        owner = PlaneCoordinatorTickPolicy.owner(false, bowDefenseResult.active(), guards.readyForWorldAction());
        if (tickBowDefenseOwner(owner, playerPos)) return;
        if (tickGuardPausedOwner(owner, playerPos)) return;

        tickBuildLoopOwner(playerPos);
    }

    private void flushQueuedTeleportAccept() {
        if (replenish.hasQueuedTeleportAccept()) replenish.tickQueuedTeleportAccept();
    }

    private void confirmPlacements() {
        stats.confirmPendingPlacements(System.currentTimeMillis());
    }

    private boolean pauseOutsideBuildArea(BlockPos playerPos) {
        if (scanner.insideBuildArea(playerPos.getX(), playerPos.getZ())) return false;

        autoWalk.reset();
        buildLoop.reset();
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDown();
        phase = Phase.OUTSIDE_AREA;
        return true;
    }

    private boolean continueAutoElytraLanding(BlockPos playerPos) {
        if (!PlaneCoordinatorTickPolicy.shouldContinueAutoElytraLanding(phase())) return false;

        Phase landingPhase = autoWalk.landBeforeWorldAction(playerPos, PlaneAutoWalkController.LockoutReason.NONE);
        if (landingPhase == Phase.IDLE) {
            phase = Phase.IDLE;
            return false;
        }

        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        phase = landingPhase;
        return true;
    }

    private boolean tickSafetyBeforeReplenish(BlockPos playerPos) {
        Phase displayPhase = phase();

        if (PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(displayPhase, false, guards.playerUsingItem())) {
            if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.SAFETY)) return true;
            autoWalk.pause();
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
                autoWalk.pause();
                holeEscape.reset();
                endermanLookSafety.lookDown();
                phase = Phase.IDLE;
                tickReplenishDuringSafetyPreemption(displayPhase);
                return true;
            }
        }
        else {
            bowDefense.releasePassiveLatch();
        }

        return false;
    }

    private void tickReplenishDuringSafetyPreemption(Phase displayPhase) {
        if (!PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(displayPhase)) return;

        syncBowDefenseAfterReplenishTick(replenish.tickResult());
    }

    private boolean preemptManagedScreenForBowSafety(boolean bowReplenishActive) {
        if (!guards.managedScreenOpen()) return false;
        if (!bowDefense.hasSafetyOpportunity(bowReplenishActive)) return false;

        autoWalk.pause();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        holeEscape.reset();
        endermanLookSafety.lookDown();
        if (guards.safeToCloseManagedScreen()) guards.closeManagedScreenForSafety();
        return true;
    }

    private void tickReplenishOwner() {
        autoWalk.reset();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.REPLENISH);
        holeEscape.reset();
        endermanLookSafety.lookDownIfUnsafe();
        syncBowDefenseAfterReplenishTick(replenish.tickResult());
    }

    private void syncBowDefenseAfterReplenishTick(ReplenishTickResult replenishResult) {
        phase = replenishResult.phase();
        if (PlaneCoordinatorTickPolicy.shouldResetBowDefenseAfterReplenishTick(replenishResult)) {
            bowDefense.reset();
            return;
        }

        boolean bowReplenishActive = PlanePhasePolicy.bowDefenseReplenishActive(phase, replenish.active());
        boolean passiveBowDefenseWindow = PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase);
        bowDefense.tickResult(bowReplenishActive, passiveBowDefenseWindow);
    }

    private boolean tickHoleEscapeBeforeReplenish(BlockPos playerPos) {
        if (!PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(replenish.phase())) return false;

        if (!tickHoleEscapeOwner(playerPos)) return false;

        replenish.pauseMovement();
        return true;
    }

    private boolean tickHoleEscapeOwner(BlockPos playerPos) {
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
        phase = escapePhase;
        return true;
    }

    private boolean tickBowDefenseOwner(PlaneCoordinatorTickPolicy.TickOwner owner, BlockPos playerPos) {
        if (owner != PlaneCoordinatorTickPolicy.TickOwner.BOW_DEFENSE) return false;

        if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.BOW_DEFENSE)) return true;
        autoWalk.pause();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        holeEscape.reset();
        endermanLookSafety.lookDownIfUnsafe();
        phase = Phase.IDLE;
        return true;
    }

    private boolean tickGuardPausedOwner(PlaneCoordinatorTickPolicy.TickOwner owner, BlockPos playerPos) {
        if (owner != PlaneCoordinatorTickPolicy.TickOwner.GUARD_PAUSED) return false;

        if (landAutoElytraForSafety(playerPos, PlaneAutoWalkController.LockoutReason.GUARD_PAUSED)) return true;
        autoWalk.pause();
        autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.GUARD_PAUSED);
        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDown();
        return true;
    }

    private void tickBuildLoopOwner(BlockPos playerPos) {
        BuildTickResult buildResult = buildLoop.tick(playerPos);
        if (buildResult.startReplenish()) {
            replenish.begin();
            autoWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.REPLENISH);
            bowDefense.reset();
            endermanLookSafety.lookDownIfUnsafe();
            phase = replenish.tickResult().phase();
            return;
        }
        if (buildResult.resetBowDefense()) bowDefense.reset();
        if (buildResult.phase() == Phase.IDLE) endermanLookSafety.lookDown();
        phase = buildResult.phase();
    }

    private boolean landAutoElytraForSafety(BlockPos playerPos, PlaneAutoWalkController.LockoutReason reason) {
        Phase landingPhase = autoWalk.landBeforeWorldAction(playerPos, reason);
        if (landingPhase == Phase.IDLE) return false;

        holeEscape.reset();
        bowDefense.reset();
        endermanLookSafety.lookDownIfUnsafe();
        phase = landingPhase;
        return true;
    }
}
