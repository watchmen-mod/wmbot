package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

final class PlaneMeleeDefenseWorkflow {
    private final PlaneActionGuards guards;
    private final PlaneInventory inventory;
    private final PlaneBowTargeting targeting = new PlaneBowTargeting();

    PlaneMeleeDefenseWorkflow(PlaneActionGuards guards, PlaneInventory inventory) {
        this.guards = guards;
        this.inventory = inventory;
    }

    boolean tick() {
        if (!guards.readyForHotbarMutation()) return false;
        if (targeting.nearestCloseMeleeThreat() == null) return false;

        FindItemResult sword = inventory.prepareUsableSword();
        return sword != null && sword.isHotbar();
    }
}
