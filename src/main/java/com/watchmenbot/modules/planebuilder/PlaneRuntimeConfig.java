package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;

record PlaneRuntimeConfig(PlaneBuildConfig build, Block buildBlock) {
    static final PlaneRuntimeConfig DEFAULT = new PlaneRuntimeConfig(
        PlaneBuilderSettings.BUILD_CONFIG,
        null
    );

    @Override
    public Block buildBlock() {
        return buildBlock == null ? PlaneBuilderSettings.buildBlock() : buildBlock;
    }

    int buildY() {
        return build.buildY();
    }

    int minX() {
        return build.minX();
    }

    int maxX() {
        return build.maxX();
    }

    int minZ() {
        return build.minZ();
    }

    int maxZ() {
        return build.maxZ();
    }

    int scanRadius() {
        return build.scanRadius();
    }

    int autoWalkLaneSpacing() {
        return build.autoWalkLaneSpacing();
    }

    int rotationPriority() {
        return build.rotationPriority();
    }

    int replenishMinBuildBlocks() {
        return build.replenishMinBuildBlocks();
    }

    int replenishTargetBuildBlocks() {
        return build.replenishTargetBuildBlocks();
    }

    PlaneAreaBounds buildArea() {
        return new PlaneAreaBounds(minX(), maxX(), minZ(), maxZ());
    }
}
