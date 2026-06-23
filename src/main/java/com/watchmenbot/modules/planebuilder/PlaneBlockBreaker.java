package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import net.minecraft.util.math.BlockPos;

final class PlaneBlockBreaker {
    private final CompanionModuleManager companionModules;
    private final PlaneActionExecutor actions;

    PlaneBlockBreaker(CompanionModuleManager companionModules) {
        this(companionModules, new PlaneEndermanLookSafety());
    }

    PlaneBlockBreaker(CompanionModuleManager companionModules, PlaneEndermanLookSafety endermanLookSafety) {
        this.companionModules = companionModules;
        actions = new PlaneActionExecutor(PlaneRuntimeConfig.DEFAULT, endermanLookSafety);
    }

    void breakBlock(BlockPos pos) {
        actions.breakBlock(pos);
    }

    void clearInstantRebreakTarget() {
        companionModules.clearInstantRebreakTarget();
    }

    void breakBlockWithInstantRebreakSuspended(BlockPos pos) {
            companionModules.suspend(InstantRebreak.class);
        try {
            actions.breakBlock(pos);
        }
        finally {
            companionModules.resume(InstantRebreak.class);
        }
    }
}
