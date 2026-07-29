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
        if (!preparedSwordCanDefend(sword)) return false;

        InvUtils.swap(sword.slot(), false);
        return true;
    }

    boolean hasSafetyOpportunity() {
        if (!guards.clientReady() || !hasImmediateThreat()) return false;

        return swordAvailable(inventory.findHotbarSword(), inventory.findMainInventorySwordSlot());
    }

    boolean hasImmediateThreat() {
        return targeting.nearestCloseMeleeThreat() != null;
    }

    static boolean preparedSwordCanDefend(FindItemResult sword) {
        return sword != null && sword.isHotbar();
    }

    static boolean swordAvailable(FindItemResult hotbarSword, int mainInventorySwordSlot) {
        return preparedSwordCanDefend(hotbarSword) || mainInventorySwordSlot >= 0;
    }
}
