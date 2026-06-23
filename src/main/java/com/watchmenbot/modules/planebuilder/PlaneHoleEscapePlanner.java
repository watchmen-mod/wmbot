package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

import java.util.List;

final class PlaneHoleEscapePlanner {
    private static final List<Offset> CARDINAL_OFFSETS = List.of(
        new Offset(0, -1),
        new Offset(1, 0),
        new Offset(0, 1),
        new Offset(-1, 0)
    );
    private static final List<Offset> DIAGONAL_OFFSETS = List.of(
        new Offset(1, -1),
        new Offset(1, 1),
        new Offset(-1, 1),
        new Offset(-1, -1)
    );

    private final PlaneAreaBounds buildArea;
    private final BlockView world;

    PlaneHoleEscapePlanner(PlaneRuntimeConfig config, BlockView world) {
        this(config.buildArea(), world);
    }

    PlaneHoleEscapePlanner(PlaneAreaBounds buildArea, BlockView world) {
        this.buildArea = buildArea;
        this.world = world;
    }

    BlockPos escapeTarget(BlockPos playerPos) {
        if (!trapped(playerPos)) return null;

        BlockPos target = firstValidTarget(playerPos, CARDINAL_OFFSETS);
        return target == null ? firstValidTarget(playerPos, DIAGONAL_OFFSETS) : target;
    }

    private boolean trapped(BlockPos playerPos) {
        if (!buildArea.contains(playerPos.getX(), playerPos.getZ())) return false;
        if (!world.passable(playerPos) || !world.passable(playerPos.up())) return false;
        if (!world.solid(playerPos.down())) return false;

        for (Offset offset : CARDINAL_OFFSETS) {
            if (world.passable(playerPos.add(offset.x(), 0, offset.z()))) return false;
        }

        return true;
    }

    private BlockPos firstValidTarget(BlockPos playerPos, List<Offset> offsets) {
        for (Offset offset : offsets) {
            BlockPos target = playerPos.add(offset.x(), 1, offset.z());
            if (validStandingTarget(target)) return target;
        }

        return null;
    }

    private boolean validStandingTarget(BlockPos target) {
        return buildArea.contains(target.getX(), target.getZ())
            && world.solid(target.down())
            && world.passable(target)
            && world.passable(target.up());
    }

    interface BlockView {
        boolean passable(BlockPos pos);

        boolean solid(BlockPos pos);
    }

    private record Offset(int x, int z) {
    }
}
