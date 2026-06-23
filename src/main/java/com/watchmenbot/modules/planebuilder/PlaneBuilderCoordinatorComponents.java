package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;

record PlaneBuilderCoordinatorComponents(
    PlaneClientContext context,
    PlaneActionGuards guards,
    PlaneAreaScanner scanner,
    PlanePlacementStatsTracker stats,
    PlaneAutoWalkController autoWalk,
    PlaneHoleEscapeController holeEscape,
    PlaneBuildLoop buildLoop,
    PlaneReplenishWorkflow replenish,
    PlaneBowDefenseWorkflow bowDefense,
    PlaneEndermanLookSafety endermanLookSafety
) {
    static PlaneBuilderCoordinatorComponents create(
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
        PlaneEndermanLookSafety endermanLookSafety = new PlaneEndermanLookSafety(endermanLookSafetySettings, context);
        PlaneActionGuards guards = new PlaneActionGuards();
        PlaneInventory inventory = new PlaneInventory(guards, config, context, pickaxeSafetyConfig);
        PlaneWorldAccess world = new PlaneWorldAccess(context);
        PlaneAutoElytraScanner autoElytraScanner = new PlaneAutoElytraScanner(config, new PlaneAutoElytraWorld(world));
        PlanePlacement placement = new PlanePlacement(inventory, guards, config, context, world, endermanLookSafety);
        PlaneAreaScanner scanner = new PlaneAreaScanner(config, context, world);
        PlanePlacementStatsTracker stats = new PlanePlacementStatsTracker(context, new PlaneBuilderStats(), config);
        PlaneAutoWalkController autoWalk = new PlaneAutoWalkController(autoWalkSettings, config, endermanLookSafety, autoElytraScanner);
        PlaneHoleEscapeController holeEscape = new PlaneHoleEscapeController(holeEscapeSettings, config, world);
        PlaneBuildLoop buildLoop = new PlaneBuildLoop(inventory, scanner, placement, stats, autoWalk, config);
        PlaneReplenishWorkflow replenish = new PlaneReplenishWorkflow(
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
            logger
        );
        PlaneBowDefenseWorkflow bowDefense = new PlaneBowDefenseWorkflow(bowDefenseSettings, guards, inventory);

        return new PlaneBuilderCoordinatorComponents(
            context,
            guards,
            scanner,
            stats,
            autoWalk,
            holeEscape,
            buildLoop,
            replenish,
            bowDefense,
            endermanLookSafety
        );
    }
}
