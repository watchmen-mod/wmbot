package com.watchmenbot.modules.planebuilder;

final class PlaneReplenishTargets {
    private PlaneReplenishTargets() {
    }

    static int effectiveTarget(PlaneInventoryAccess inventory, PlaneBuilderSettings.Replenish settings) {
        return effectiveTarget(
            inventory,
            settings.targetObsidian().get(),
            settings.useAvailableSafeInventorySpace().get()
        );
    }

    static int effectiveTarget(
        PlaneInventoryAccess inventory,
        int configuredTarget,
        boolean useAvailableSafeInventorySpace
    ) {
        return inventory.effectiveReplenishTarget(configuredTarget, useAvailableSafeInventorySpace);
    }
}
