package com.watchmenbot.modules.planebuilder;

record PlaneBuildConfig(
    int buildY,
    int minX,
    int maxX,
    int minZ,
    int maxZ,
    int scanRadius,
    int autoWalkLaneSpacing,
    int rotationPriority,
    int replenishMinBuildBlocks,
    int replenishTargetBuildBlocks
) {
    static final PlaneBuildConfig DEFAULT = new PlaneBuildConfig(
        319,
        -10000,
        10000,
        -10000,
        10000,
        4,
        8,
        -50,
        32,
        128
    );
}
