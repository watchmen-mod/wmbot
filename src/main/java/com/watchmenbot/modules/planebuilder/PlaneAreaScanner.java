package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PlaneAreaScanner {
    private final PlaneRuntimeConfig config;
    private final PlaneClientContext context;
    private final PlaneWorldAccess world;

    PlaneAreaScanner() {
        this(PlaneRuntimeConfig.DEFAULT, new PlaneClientContext(), new PlaneWorldAccess());
    }

    PlaneAreaScanner(PlaneRuntimeConfig config, PlaneClientContext context, PlaneWorldAccess world) {
        this.config = config;
        this.context = context;
        this.world = world;
    }

    boolean insideBuildArea(int x, int z) {
        return config.buildArea().contains(x, z);
    }

    BlockPos nearestPlaceableTarget() {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos playerPos = context.player().getBlockPos();
        PlaneAreaBounds scan = PlaneAreaBounds.scanWindow(config.buildArea(), playerPos.getX(), playerPos.getZ(), config.scanRadius());

        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                BlockPos pos = new BlockPos(x, config.buildY(), z);
                if (world.canPlaceBlock(pos, config.buildBlock())) candidates.add(pos);
            }
        }

        return candidates.stream()
            .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(context.player().getEyePos())))
            .orElse(null);
    }

    BlockPos nearestServiceHole() {
        return nearestServiceHole(Set.of());
    }

    BlockPos nearestServiceHole(Set<BlockPos> excluded) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos playerPos = context.player().getBlockPos();
        PlaneAreaBounds scan = PlaneAreaBounds.scanWindow(config.buildArea(), playerPos.getX(), playerPos.getZ(), config.scanRadius());

        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                BlockPos pos = new BlockPos(x, config.buildY(), z);
                if (!excluded.contains(pos) && isUsableServiceHoleCandidate(pos)) candidates.add(pos);
            }
        }

        return candidates.stream()
            .min(Comparator
                .comparingInt((BlockPos pos) -> serviceHoleCandidatePriority(pos))
                .thenComparingDouble(pos -> pos.getSquaredDistance(context.player().getEyePos()))
            )
            .orElse(null);
    }

    boolean isServiceHoleBlock(BlockPos pos) {
        Block block = world.block(pos);
        return block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
    }

    boolean validServiceSupport(BlockPos support) {
        BlockState state = world.blockState(support);
        return serviceSupportUsable(state.isReplaceable(), world.isSolidBlock(support));
    }

    static boolean serviceSupportUsable(boolean replaceable, boolean solid) {
        return !replaceable && solid;
    }

    private boolean isUsableServiceHoleCandidate(BlockPos pos) {
        if (!isServiceHoleCandidateShape(pos)) return false;

        BlockPos support = pos.down();
        return validServiceSupport(support) || world.isReplaceable(support);
    }

    private int serviceHoleCandidatePriority(BlockPos pos) {
        return validServiceSupport(pos.down()) ? 0 : 1;
    }

    private boolean isServiceHoleCandidateShape(BlockPos pos) {
        if (!isServiceHoleBlock(pos)) return false;
        if (playerIntersectsHoleColumn(pos)) return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                BlockPos rim = pos.add(dx, 0, dz);
                if (!insideBuildArea(rim.getX(), rim.getZ())) return false;
                if (world.isReplaceable(rim)) return false;
            }
        }

        return true;
    }

    private boolean playerIntersectsHoleColumn(BlockPos pos) {
        Box playerBox = context.player().getBoundingBox().expand(0.15, 0.0, 0.15);
        Box holeColumn = new Box(
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            pos.getX() + 1.0,
            pos.getY() + 3.0,
            pos.getZ() + 1.0
        );

        return playerBox.intersects(holeColumn);
    }
}
