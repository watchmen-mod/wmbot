package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

final class PlaneAutoElytraHazards {
    private PlaneAutoElytraHazards() {
    }

    static boolean isHazard(Block block) {
        return hazardKind(
            block == Blocks.LAVA,
            block == Blocks.FIRE,
            block == Blocks.SOUL_FIRE,
            block == Blocks.MAGMA_BLOCK,
            block == Blocks.CACTUS,
            block == Blocks.SWEET_BERRY_BUSH,
            block == Blocks.WITHER_ROSE,
            block == Blocks.CAMPFIRE,
            block == Blocks.SOUL_CAMPFIRE,
            block == Blocks.POWDER_SNOW,
            block == Blocks.END_PORTAL,
            block == Blocks.END_PORTAL_FRAME,
            block == Blocks.NETHER_PORTAL
        );
    }

    static boolean hazardKind(
        boolean lava,
        boolean fire,
        boolean soulFire,
        boolean magmaBlock,
        boolean cactus,
        boolean sweetBerryBush,
        boolean witherRose,
        boolean campfire,
        boolean soulCampfire,
        boolean powderSnow,
        boolean endPortal,
        boolean endPortalFrame,
        boolean netherPortal
    ) {
        return lava
            || fire
            || soulFire
            || magmaBlock
            || cactus
            || sweetBerryBush
            || witherRose
            || campfire
            || soulCampfire
            || powderSnow
            || endPortal
            || endPortalFrame
            || netherPortal;
    }
}
