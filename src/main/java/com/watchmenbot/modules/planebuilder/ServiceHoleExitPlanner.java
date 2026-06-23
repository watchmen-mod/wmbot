package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

import java.util.List;

final class ServiceHoleExitPlanner {
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

    ServiceHoleExitPlanner(PlaneRuntimeConfig config, BlockView world) {
        this(config.buildArea(), world);
    }

    ServiceHoleExitPlanner(PlaneAreaBounds buildArea, BlockView world) {
        this.buildArea = buildArea;
        this.world = world;
    }

    BlockPos exitTarget(BlockPos playerPos, BlockPos serviceHole) {
        if (!insideServiceHole(playerPos, serviceHole)) return null;

        BlockPos target = firstValidTarget(serviceHole, CARDINAL_OFFSETS);
        return target == null ? firstValidTarget(serviceHole, DIAGONAL_OFFSETS) : target;
    }

    boolean insideServiceHole(BlockPos playerPos, BlockPos serviceHole) {
        if (playerPos == null || serviceHole == null) return false;
        return playerPos.getX() == serviceHole.getX()
            && playerPos.getZ() == serviceHole.getZ()
            && playerPos.getY() >= serviceHole.getY() - 1
            && playerPos.getY() <= serviceHole.getY();
    }

    private BlockPos firstValidTarget(BlockPos serviceHole, List<Offset> offsets) {
        for (Offset offset : offsets) {
            BlockPos target = serviceHole.add(offset.x(), 1, offset.z());
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
