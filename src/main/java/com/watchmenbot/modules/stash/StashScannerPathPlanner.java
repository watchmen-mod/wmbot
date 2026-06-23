package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class StashScannerPathPlanner {
    private StashScannerPathPlanner() {
    }

    static Optional<BlockPos> bestStandingPos(MinecraftClient mc, StashTarget target, int interactionRange) {
        if (!StashClientUtils.canUse(mc) || target == null) return Optional.empty();

        int radius = Math.max(2, interactionRange);
        double usableRangeSq = Math.pow(interactionRange + 1.25, 2);
        BlockPos playerPos = mc.player.getBlockPos();
        Set<BlockPos> targetPositions = Set.copyOf(target.positions());
        List<BlockPos> candidates = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        for (BlockPos targetPos : target.positions()) {
            for (int dy = -radius; dy <= Math.min(2, radius); dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;

                        BlockPos candidate = new BlockPos(targetPos.getX() + dx, targetPos.getY() + dy, targetPos.getZ() + dz);
                        if (!seen.add(candidate) || targetPositions.contains(candidate)) continue;
                        if (!canReachAnyTarget(candidate, target, usableRangeSq)) continue;
                        if (!isWalkable(mc, candidate)) continue;

                        candidates.add(candidate.toImmutable());
                    }
                }
            }
        }

        return candidates.stream().min(Comparator.comparingDouble(candidate -> standingPosScore(candidate, target, playerPos)));
    }

    private static boolean canReachAnyTarget(BlockPos standingPos, StashTarget target, double usableRangeSq) {
        Vec3d eyes = Vec3d.ofCenter(standingPos).add(0.0, 1.0, 0.0);
        for (BlockPos targetPos : target.positions()) {
            if (eyes.squaredDistanceTo(Vec3d.ofCenter(targetPos)) <= usableRangeSq) return true;
        }

        return false;
    }

    private static boolean isWalkable(MinecraftClient mc, BlockPos pos) {
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
            && mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty()
            && !mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty();
    }

    private static double standingPosScore(BlockPos candidate, StashTarget target, BlockPos playerPos) {
        double score = playerPos.getSquaredDistance(candidate);
        score += Math.abs(candidate.getY() - playerPos.getY()) * 12.0;
        score += Math.abs(candidate.getY() - target.interactionPos().getY()) * 4.0;
        return score;
    }
}
