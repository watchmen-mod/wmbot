package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

final class PlaneMovementSafetyPolicy {
    static final int DEFAULT_MAX_GOAL_DISTANCE = 32;

    private final PlaneRuntimeConfig config;
    private final int maxGoalDistance;

    PlaneMovementSafetyPolicy() {
        this(PlaneRuntimeConfig.DEFAULT, DEFAULT_MAX_GOAL_DISTANCE);
    }

    PlaneMovementSafetyPolicy(PlaneRuntimeConfig config, int maxGoalDistance) {
        this.config = config == null ? PlaneRuntimeConfig.DEFAULT : config;
        this.maxGoalDistance = Math.max(1, maxGoalDistance);
    }

    Decision validatePlatformGoal(BlockPos playerPos, BlockPos goal) {
        if (goal == null) return Decision.reject(RejectReason.MISSING_GOAL);
        if (!config.buildArea().contains(goal.getX(), goal.getZ())) return Decision.reject(RejectReason.OUTSIDE_BUILD_AREA);
        if (goal.getY() < config.buildY()) return Decision.reject(RejectReason.BELOW_PLATFORM);
        if (playerPos != null && horizontalDistanceSquared(playerPos, goal) > (long) maxGoalDistance * maxGoalDistance) {
            return Decision.reject(RejectReason.TOO_FAR);
        }

        return Decision.accept();
    }

    Decision validateStandingBlock(BlockPos playerPos, BlockPos goal, BlockState state) {
        Decision goalDecision = validatePlatformGoal(playerPos, goal);
        if (!goalDecision.accepted()) return goalDecision;
        if (isSnowHazard(state)) return Decision.reject(RejectReason.SNOW_HAZARD);

        return Decision.accept();
    }

    static boolean isSnowHazard(BlockState state) {
        return state != null && isSnowHazard(state.getBlock());
    }

    static boolean isSnowHazard(Block block) {
        return block == Blocks.SNOW
            || block == Blocks.SNOW_BLOCK
            || block == Blocks.POWDER_SNOW;
    }

    static boolean isSnowHazardId(String blockId) {
        return "minecraft:snow".equals(blockId)
            || "minecraft:snow_block".equals(blockId)
            || "minecraft:powder_snow".equals(blockId);
    }

    private static long horizontalDistanceSquared(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    record Decision(boolean accepted, RejectReason reason) {
        static Decision accept() {
            return new Decision(true, RejectReason.NONE);
        }

        static Decision reject(RejectReason reason) {
            return new Decision(false, reason);
        }
    }

    enum RejectReason {
        NONE,
        MISSING_GOAL,
        OUTSIDE_BUILD_AREA,
        BELOW_PLATFORM,
        TOO_FAR,
        SNOW_HAZARD
    }
}
