package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.util.math.BlockPos;

final class PlaneWorldActions {
    private final PlaneInventory inventory;
    private final PlanePlacement placement;
    private final PlaneBlockBreaker breaker;
    private final PlaneActionExecutor actions;

    PlaneWorldActions(PlaneInventory inventory, PlanePlacement placement, PlaneBlockBreaker breaker) {
        this(inventory, placement, breaker, new PlaneEndermanLookSafety());
    }

    PlaneWorldActions(
        PlaneInventory inventory,
        PlanePlacement placement,
        PlaneBlockBreaker breaker,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this.inventory = inventory;
        this.placement = placement;
        this.breaker = breaker;
        actions = new PlaneActionExecutor(PlaneRuntimeConfig.DEFAULT, endermanLookSafety);
    }

    Phase placeBuildBlockOrMissing(BlockPos target, Phase nextPhase) {
        return placeBuildBlockOrMissing(target, inventory.prepareUsableBuildBlock(), nextPhase);
    }

    Phase placeBuildBlockOrMissing(BlockPos target, FindItemResult buildBlock, Phase nextPhase) {
        if (!inventory.findResultMatchesBuildBlock(buildBlock)) {
            return Phase.MISSING_OBSIDIAN;
        }

        placement.placeObsidian(target, buildBlock);
        return nextPhase;
    }

    Phase breakWithPickaxe(BlockPos target, Phase nextPhase, boolean suspendInstantRebreak) {
        FindItemResult pickaxe = inventory.prepareUsablePickaxe();
        if (pickaxe == null || !pickaxe.isHotbar()) {
            return Phase.MISSING_PICKAXE;
        }

        actions.swapToHotbarSlot(pickaxe.slot());
        if (suspendInstantRebreak) {
            breaker.breakBlockWithInstantRebreakSuspended(target);
        }
        else {
            breaker.breakBlock(target);
        }

        return nextPhase;
    }

    void clearInstantRebreakTarget() {
        breaker.clearInstantRebreakTarget();
    }
}
