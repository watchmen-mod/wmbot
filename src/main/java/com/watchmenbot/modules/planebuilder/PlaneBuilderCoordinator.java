package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.util.math.BlockPos;

final class PlaneBuilderCoordinator {
    private final PlaneClientContext context;
    private final PlaneActionGuards guards;
    private final PlanePlacementStatsTracker stats;
    private final PlaneAutoWalkController autoWalk;
    private final PlaneHoleEscapeController holeEscape;
    private final PlaneBuildLoop buildLoop;
    private final PlaneReplenishWorkflow replenish;
    private final PlaneMeleeDefenseWorkflow meleeDefense;
    private final PlaneBowDefenseWorkflow bowDefense;
    private final PlaneCoordinatorSafetyWorkflow safety;
    private final PlaneCoordinatorOwnerWorkflow owners;

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
        PlaneAreaScanner scanner = components.scanner();
        stats = components.stats();
        autoWalk = components.autoWalk();
        holeEscape = components.holeEscape();
        buildLoop = components.buildLoop();
        replenish = components.replenish();
        meleeDefense = components.meleeDefense();
        bowDefense = components.bowDefense();
        PlaneEndermanLookSafety endermanLookSafety = components.endermanLookSafety();
        owners = new PlaneCoordinatorOwnerWorkflow(
            autoWalk,
            holeEscape,
            buildLoop,
            replenish,
            bowDefense,
            endermanLookSafety,
            new PlaneCoordinatorOwnerWorkflow.Callbacks() {
                @Override
                public Phase currentPhase() {
                    return phase;
                }

                @Override
                public void setPhase(Phase phase) {
                    PlaneBuilderCoordinator.this.phase = phase;
                }
            }
        );
        safety = new PlaneCoordinatorSafetyWorkflow(
            guards,
            scanner,
            autoWalk,
            holeEscape,
            buildLoop,
            replenish,
            bowDefense,
            endermanLookSafety,
            new PlaneCoordinatorSafetyWorkflow.Callbacks() {
                @Override
                public Phase currentPhase() {
                    return phase();
                }

                @Override
                public void setPhase(Phase phase) {
                    PlaneBuilderCoordinator.this.phase = phase;
                }

                @Override
                public void syncBowDefenseAfterReplenishTick(ReplenishTickResult replenishResult) {
                    owners.syncBowDefenseAfterReplenishTick(replenishResult);
                }
            }
        );
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
        if (safety.pauseOutsideBuildArea(playerPos)) return;
        if (safety.continueAutoElytraLanding(playerPos)) return;

        if (replenish.active() && safety.tickSafetyBeforeReplenish(playerPos)) return;

        PlaneCoordinatorTickPolicy.TickOwner owner = PlaneCoordinatorTickPolicy.owner(replenish.active(), false, true);
        if (owner == PlaneCoordinatorTickPolicy.TickOwner.REPLENISH) {
            if (safety.tickHoleEscapeBeforeReplenish(playerPos)) return;
            owners.tickReplenishOwner();
            return;
        }

        if (safety.tickHoleEscapeOwner(playerPos)) return;

        meleeDefense.tick();

        BowDefenseTickResult bowDefenseResult = bowDefense.tickResult(false);
        owner = PlaneCoordinatorTickPolicy.owner(false, bowDefenseResult.active(), guards.readyForWorldAction());
        if (safety.tickBowDefenseOwner(owner, playerPos)) return;
        if (safety.tickGuardPausedOwner(owner, playerPos)) return;

        owners.tickBuildLoopOwner(playerPos);
    }

    private void flushQueuedTeleportAccept() {
        if (replenish.hasQueuedTeleportAccept()) replenish.tickQueuedTeleportAccept();
    }

    private void confirmPlacements() {
        stats.confirmPendingPlacements(System.currentTimeMillis());
    }
}
