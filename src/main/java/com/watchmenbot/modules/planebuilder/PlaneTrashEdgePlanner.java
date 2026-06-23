package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PlaneTrashEdgePlanner {
    private static final Direction[] EDGE_DIRECTIONS = {
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    };

    private final PlaneAreaBounds buildArea;
    private final int buildY;
    private final int scanRadius;
    private final BlockView blocks;

    PlaneTrashEdgePlanner(PlaneRuntimeConfig config, BlockView blocks) {
        this(config.buildArea(), config.buildY(), config.scanRadius(), blocks);
    }

    PlaneTrashEdgePlanner(PlaneAreaBounds buildArea, int buildY, int scanRadius, BlockView blocks) {
        this.buildArea = buildArea;
        this.buildY = buildY;
        this.scanRadius = Math.max(1, scanRadius);
        this.blocks = blocks;
    }

    Target select(BlockPos playerPos) {
        if (playerPos == null) return null;

        PlaneAreaBounds scan = PlaneAreaBounds.scanWindow(buildArea, playerPos.getX(), playerPos.getZ(), scanRadius);
        List<Target> candidates = new ArrayList<>();
        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                BlockPos standing = new BlockPos(x, buildY, z);
                Direction outward = outwardDirection(standing);
                if (outward != null) candidates.add(new Target(standing, outward));
            }
        }

        Vec3d playerCenter = Vec3d.ofBottomCenter(playerPos);
        return candidates.stream()
            .min(Comparator
                .comparingDouble((Target target) -> Vec3d.ofCenter(target.standing()).squaredDistanceTo(playerCenter))
                .thenComparingInt(target -> directionPriority(target.outward())))
            .orElse(null);
    }

    boolean validStanding(BlockPos pos) {
        return pos != null
            && pos.getY() == buildY
            && buildArea.contains(pos.getX(), pos.getZ())
            && blocks.buildBlock(pos)
            && blocks.replaceable(pos.up())
            && blocks.replaceable(pos.up(2));
    }

    Direction outwardDirection(BlockPos standing) {
        if (!validStanding(standing)) return null;

        for (Direction direction : EDGE_DIRECTIONS) {
            BlockPos adjacent = standing.offset(direction);
            if (!buildArea.contains(adjacent.getX(), adjacent.getZ()) || !blocks.buildBlock(adjacent) || blocks.replaceable(adjacent)) {
                return direction;
            }
        }

        return null;
    }

    static Vec3d dropTarget(Target target) {
        return Vec3d.ofCenter(target.standing()).add(
            target.outward().getOffsetX() * 1.5,
            -0.75,
            target.outward().getOffsetZ() * 1.5
        );
    }

    private static int directionPriority(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 4;
        };
    }

    record Target(BlockPos standing, Direction outward) {
    }

    interface BlockView {
        boolean buildBlock(BlockPos pos);

        boolean replaceable(BlockPos pos);
    }
}
