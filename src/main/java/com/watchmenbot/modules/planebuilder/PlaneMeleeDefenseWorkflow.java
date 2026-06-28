package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;

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
        if (!hasImmediateThreat()) return false;

        FindItemResult sword = inventory.prepareUsableSword();
        if (sword != null && sword.isHotbar()) InvUtils.swap(sword.slot(), false);
        return true;
    }

    boolean hasImmediateThreat() {
        return targeting.nearestCloseMeleeThreat() != null;
    }
}
