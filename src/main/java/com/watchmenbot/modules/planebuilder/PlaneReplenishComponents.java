package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

record PlaneReplenishComponents(
    PlaneInventoryAccess inventory,
    PlaneClientContext context,
    ServiceHoleContext serviceHole,
    ServiceHoleWorkflow serviceHoles,
    EnderChestFarmWorkflow enderChestFarm,
    EnderChestShulkerExtractor shulkerExtractor,
    PlaneKitbotRefillWorkflow kitbotRefill,
    ServiceHoleExitWorkflow serviceHoleExit,
    PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup,
    PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerRecovery,
    PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerCleanup,
    PlaneDroppedItemPickupWorkflow<ItemEntity> missingShulkerPickup,
    ManagedEnderChestShulkerState managedShulker,
    PlaneTrashEdgeWorkflow trashCleanup,
    PlaneRuntimeConfig config,
    WorkflowLogger logger
) {
    static PlaneReplenishComponents create(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings
    ) {
        return create(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, PlaneRuntimeConfig.DEFAULT);
    }

    static PlaneReplenishComponents create(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneAreaScanner scanner,
        PlaneActionGuards guards,
        CompanionModuleManager companionModules,
        PlaneBuilderSettings.Replenish replenishSettings,
        PlaneBuilderSettings.KitbotRefill kitbotRefillSettings,
        PlaneRuntimeConfig config
    ) {
        return create(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, new PlaneClientContext());
    }

    static PlaneReplenishComponents create(
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
        return create(inventory, placement, scanner, guards, companionModules, replenishSettings, kitbotRefillSettings, config, context, new PlaneEndermanLookSafety());
    }

    static PlaneReplenishComponents create(
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
        return create(
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

    static PlaneReplenishComponents create(
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
        PlaneBlockBreaker breaker = new PlaneBlockBreaker(companionModules, endermanLookSafety);
        PlaneWorldActions actions = new PlaneWorldActions(inventory, placement, breaker, endermanLookSafety);
        PlaneWorldAccess world = new PlaneWorldAccess(context);
        EnderChestSupplyInventory supply = new EnderChestSupplyInventory(inventory);
        ServiceHoleContext serviceHole = new ServiceHoleContext(scanner, config, context);
        ServiceHoleWorkflow serviceHoles = new ServiceHoleWorkflow(serviceHole, inventory, guards, actions, logger);
        EnderChestFarmProgress farmProgress = new EnderChestFarmProgress();
        ManagedEnderChestShulkerState managedShulker = new ManagedEnderChestShulkerState();
        PlaneReplenishDropDetector dropDetector = new PlaneReplenishDropDetector();
        PlaneMovementSafetyPolicy movementSafety = new PlaneMovementSafetyPolicy(config, (int) Math.ceil(PlanePickupSettings.KITBOT_REFILL_SCAN_RADIUS));
        PlaneDroppedItemSafety droppedItemSafety = new PlaneDroppedItemSafety(movementSafety, logger);
        ServiceHoleExitWorkflow serviceHoleExit = new ServiceHoleExitWorkflow(
            new ServiceHoleExitPlanner(
                config,
                new ServiceHoleExitPlanner.BlockView() {
                    @Override
                    public boolean passable(BlockPos pos) {
                        return world.isReplaceable(pos);
                    }

                    @Override
                    public boolean solid(BlockPos pos) {
                        return world.isSolidBlock(pos);
                    }
                }
            ),
            new PlaneHoleEscapeNavigator()
        );
        EnderChestFarmWorkflow enderChestFarm = new EnderChestFarmWorkflow(
            inventory,
            placement,
            guards,
            serviceHole,
            serviceHoles,
            actions,
            replenishSettings,
            logger,
            farmProgress,
            managedShulker::reservesInventorySlot,
            dropDetector::nearbyObsidianDropCount
        );
        EnderChestShulkerExtractor shulkerExtractor = new EnderChestShulkerExtractor(
            inventory,
            placement,
            guards,
            actions,
            replenishSettings,
            endermanLookSafety,
            logger,
            farmProgress,
            managedShulker::reservesInventorySlot,
            dropDetector::nearbyObsidianDropCount
        );
        PlaneKitbotRefillWorkflow kitbotRefill = new PlaneKitbotRefillWorkflow(
            kitbotRefillSettings,
            supply,
            inventory,
            replenishSettings,
            managedShulker::reservesInventorySlot
        );
        PlaneDroppedItemPickupWorkflow<ItemEntity> dropCleanup = new PlaneDroppedItemPickupWorkflow<>(
            dropDetector::nearestCleanupDrop,
            dropDetector::matchesCleanupDrop,
            item -> inventory.hasInventorySpaceForCleanupDrop(item.getStack()),
            droppedItemSafety::safe,
            droppedItemSafety::logRejected,
            new PlaneItemPickupNavigator(endermanLookSafety),
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.MOVING_TO_TRASH_EDGE,
            PlanePickupSettings.REPLENISH_CLEANUP_GRACE_TICKS,
            PlanePickupSettings.REPLENISH_CLEANUP_MAX_TARGET_TICKS
        );
        PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerRecovery = new PlaneDroppedItemPickupWorkflow<>(
            dropDetector::nearestShulkerDrop,
            dropDetector::matchesShulkerDrop,
            item -> inventory.hasInventorySpaceForCleanupDrop(item.getStack()),
            droppedItemSafety::safe,
            droppedItemSafety::logRejected,
            new PlaneItemPickupNavigator(endermanLookSafety),
            Phase.BREAKING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            PlanePickupSettings.REPLENISH_CLEANUP_GRACE_TICKS,
            PlanePickupSettings.MANAGED_SHULKER_RECOVERY_MAX_TARGET_TICKS,
            true
        );
        PlaneDroppedItemPickupWorkflow<ItemEntity> managedShulkerCleanup = new PlaneDroppedItemPickupWorkflow<>(
            dropDetector::nearestShulkerDrop,
            dropDetector::matchesShulkerDrop,
            item -> inventory.hasInventorySpaceForCleanupDrop(item.getStack()),
            droppedItemSafety::safe,
            droppedItemSafety::logRejected,
            new PlaneItemPickupNavigator(endermanLookSafety),
            Phase.PICKING_UP_REPLENISH_DROPS,
            Phase.PICKING_UP_REPLENISH_DROPS,
            PlanePickupSettings.REPLENISH_CLEANUP_GRACE_TICKS,
            PlanePickupSettings.MANAGED_SHULKER_RECOVERY_MAX_TARGET_TICKS,
            true
        );
        PlaneDroppedItemPickupWorkflow<ItemEntity> missingShulkerPickup = new PlaneDroppedItemPickupWorkflow<>(
            dropDetector::nearestShulkerDrop,
            dropDetector::matchesShulkerDrop,
            item -> inventory.hasInventorySpaceForCleanupDrop(item.getStack()),
            droppedItemSafety::safe,
            droppedItemSafety::logRejected,
            new PlaneItemPickupNavigator(endermanLookSafety),
            Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
            Phase.MISSING_ENDER_CHEST_SHULKER,
            PlanePickupSettings.REPLENISH_CLEANUP_GRACE_TICKS,
            PlanePickupSettings.MANAGED_SHULKER_RECOVERY_MAX_TARGET_TICKS,
            true
        );
        PlaneTrashEdgeWorkflow trashCleanup = new PlaneTrashEdgeWorkflow(
            inventory,
            guards,
            world,
            context,
            config,
            replenishSettings,
            endermanLookSafety
        );

        return new PlaneReplenishComponents(
            inventory,
            context,
            serviceHole,
            serviceHoles,
            enderChestFarm,
            shulkerExtractor,
            kitbotRefill,
            serviceHoleExit,
            dropCleanup,
            managedShulkerRecovery,
            managedShulkerCleanup,
            missingShulkerPickup,
            managedShulker,
            trashCleanup,
            config,
            logger
        );
    }
}
