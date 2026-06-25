package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;

import java.util.Map;
import java.util.Set;

final class PlaneReplenishWorkflow {
    private final ServiceHoleContext serviceHole;
    private final PlaneInventoryAccess inventory;
    private final ServiceHoleWorkflow serviceHoles;
    private final EnderChestFarmWorkflow enderChestFarm;
    private final PlaneReplenishKitbotRefillPhaseWorkflow refillPhases;
    private final PlaneReplenishManagedShulkerWorkflow managedShulkers;
    private final PlaneReplenishCleanupWorkflow cleanup;
    private final PlaneRuntimeConfig config;
    private final Map<Phase, PlaneReplenishTransition> transitions;

    private Phase phase = Phase.IDLE;

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            PlaneRuntimeConfig.DEFAULT
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config
    ) {
        this(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, new PlaneClientContext());
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            config,
            context,
            new PlaneEndermanLookSafety()
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this(
            inventory,
            placement,
            scanner,
            guards,
            companionModules,
            replenishSettings,
            kitbotRefillSettings,
            config,
            context,
            endermanLookSafety,
            PlaneWorkflowLoggers.NOOP
        );
    }

    PlaneReplenishWorkflow(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneEndermanLookSafety endermanLookSafety,
        WorkflowLogger logger
    ) {
        this(PlaneReplenishComponents.create(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, context, endermanLookSafety, logger));
    }

    PlaneReplenishWorkflow(PlaneReplenishComponents components) {
        inventory = components.inventory();
        PlaneClientContext context = components.context();
        serviceHole = components.serviceHole();
        serviceHoles = components.serviceHoles();
        enderChestFarm = components.enderChestFarm();
        PlaneKitbotRefillWorkflow kitbotRefill = components.kitbotRefill();
        refillPhases = new PlaneReplenishKitbotRefillPhaseWorkflow(serviceHoles, kitbotRefill);
        cleanup = new PlaneReplenishCleanupWorkflow(components.dropCleanup(), components.trashCleanup());
        config = components.config();
        managedShulkers = new PlaneReplenishManagedShulkerWorkflow(
            context,
            inventory,
            serviceHole,
            serviceHoles,
            enderChestFarm,
            components.shulkerExtractor(),
            kitbotRefill,
            components.serviceHoleExit(),
            components.managedShulkerRecovery(),
            components.missingShulkerPickup(),
            components.logger(),
            this::phase
        );
        transitions = PlaneReplenishTransitionTable.create(
            serviceHoles,
            enderChestFarm,
            this::closeServiceHole,
            managedShulkers::placeEnderChestShulker,
            managedShulkers::openEnderChestShulker,
            managedShulkers::takeEnderChestsFromShulker,
            managedShulkers::breakEnderChestShulker,
            refillPhases::closeServiceHoleForKitbotRefill,
            refillPhases::waitForKitbotRefill,
            refillPhases::pickUpKitbotRefill,
            managedShulkers::pickUpMissingEnderChestShulker,
            cleanup::pickUpReplenishDrops,
            cleanup::moveToTrashEdge,
            cleanup::dropTrashOffEdge,
            cleanup::waitForTrashToFall,
            this::recoverMissingEnderChest,
            this::recoverMissingObsidian
        );
    }

    void reset() {
        phase = Phase.IDLE;
        serviceHole.reset();
        enderChestFarm.resetFarmProgress();
        managedShulkers.reset();
        refillPhases.reset();
        cleanup.reset();
    }

    void pauseMovement() {
        cleanup.pauseMovement();
    }

    Phase phase() {
        return phase;
    }

    boolean active() {
        return PlaneReplenishDecisions.active(phase, serviceHole.selected())
            || (refillPhases.pending() && refillPhases.pendingOwnsPhase(phase));
    }

    boolean allowsBowDefenseDuringReplenish() {
        return PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase);
    }

    boolean hasQueuedTeleportAccept() {
        return refillPhases.hasQueuedTeleportAccept();
    }

    void tickQueuedTeleportAccept() {
        refillPhases.tickQueuedTeleportAccept();
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult handleMessage(String message) {
        return refillPhases.handleMessage(message);
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return refillPhases.ignoredTeleportPrompt(message);
    }

    PlaneKitbotTeleportAcceptWorkflow.AcceptResult consumeTeleportAcceptResult() {
        return refillPhases.consumeTeleportAcceptResult();
    }

    void begin() {
        serviceHole.reset();
        enderChestFarm.resetFarmProgress();
        refillPhases.beginReplenishCycle();
        cleanup.beginCleanupCycle();
        phase = Phase.SELECTING_SERVICE_HOLE;
    }

    Phase tick() {
        return tickResult().phase();
    }

    ReplenishTickResult tickResult() {
        Phase previousPhase = phase;

        PlaneReplenishTransition transition = transitions.get(phase);
        phase = transition == null ? phase : transition.next();
        if (previousPhase == Phase.BREAKING_ENDER_CHEST && phase != Phase.BREAKING_ENDER_CHEST) {
            enderChestFarm.reset();
        }
        if (previousPhase != Phase.SELECTING_SERVICE_HOLE && phase == Phase.SELECTING_SERVICE_HOLE) {
            enderChestFarm.resetFarmProgress();
        }
        return new ReplenishTickResult(phase, allowsBowDefenseDuringReplenish());
    }

    static Set<Phase> transitionPhases() {
        return PlaneReplenishTransitionTable.transitionPhases();
    }

    static boolean pendingKitbotRefillOwnsPhase(Phase phase) {
        return PlaneReplenishKitbotRefillPhaseWorkflow.ownsPhase(phase);
    }

    private Phase closeServiceHole() {
        managedShulkers.reset();
        Phase next = PlaneReplenishDecisions.afterNormalServiceHoleClose(serviceHoles.close());
        if (next == Phase.PICKING_UP_REPLENISH_DROPS) cleanup.beginCleanupCycle();
        return next;
    }

    private Phase recoverMissingObsidian() {
        Phase recovery = PlaneReplenishDecisions.missingObsidianRecoveryPhase(
            inventory.countBuildBlock(),
            config.replenishMinBuildBlocks(),
            serviceHole.selected()
        );
        if (recovery == Phase.CLOSING_SERVICE_HOLE) return closeServiceHole();

        return recovery;
    }

    private Phase recoverMissingEnderChest() {
        return managedShulkers.handleMissingSupply(enderChestFarm.recoverMissingEnderChest());
    }
}
