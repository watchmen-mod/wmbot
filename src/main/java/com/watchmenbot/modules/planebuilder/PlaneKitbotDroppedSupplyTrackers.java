package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;

import java.util.Set;
import java.util.UUID;

final class PlaneKitbotDroppedSupplyTrackers {
    private PlaneKitbotDroppedSupplyTrackers() {
    }

    static PlaneKitbotDroppedSupplyTracker from(PlaneDroppedItemPickupWorkflow<ItemEntity> pickupWorkflow) {
        if (pickupWorkflow == null) return none();

        return new PlaneKitbotDroppedSupplyTracker() {
            @Override
            public void captureBaseline() {
            }

            @Override
            public boolean hasNewDeliveryDrop() {
                return pickupWorkflow.hasTarget();
            }

            @Override
            public Phase tickPickup() {
                return pickupWorkflow.tick();
            }

            @Override
            public void reset() {
                pickupWorkflow.reset();
            }
        };
    }

    static PlaneKitbotDroppedSupplyTracker from(PlaneKitbotDroppedSupplyDetector detector, int noTargetGraceTicks) {
        return new PlaneKitbotDroppedSupplyTracker() {
            private Set<UUID> baselineIds = Set.of();
            private final PlaneDroppedItemPickupWorkflow<ItemEntity> pickupWorkflow = new PlaneDroppedItemPickupWorkflow<>(
                this::nearestNewDeliveryDrop,
                detector::matchesSupply,
                new PlaneItemPickupNavigator(),
                Phase.PICKING_UP_KITBOT_REFILL,
                Phase.WAITING_FOR_KITBOT_REFILL,
                noTargetGraceTicks
            );

            @Override
            public void captureBaseline() {
                baselineIds = detector.droppedSupplyIds();
                pickupWorkflow.reset();
            }

            @Override
            public boolean hasNewDeliveryDrop() {
                return nearestNewDeliveryDrop() != null;
            }

            @Override
            public Phase tickPickup() {
                return pickupWorkflow.tick();
            }

            @Override
            public void reset() {
                baselineIds = Set.of();
                pickupWorkflow.reset();
            }

            private ItemEntity nearestNewDeliveryDrop() {
                return detector.nearestDroppedSupplyExcluding(baselineIds);
            }
        };
    }

    static PlaneKitbotDroppedSupplyTracker none() {
        return new PlaneKitbotDroppedSupplyTracker() {
            @Override
            public void captureBaseline() {
            }

            @Override
            public boolean hasNewDeliveryDrop() {
                return false;
            }

            @Override
            public Phase tickPickup() {
                return Phase.WAITING_FOR_KITBOT_REFILL;
            }

            @Override
            public void reset() {
            }
        };
    }
}
