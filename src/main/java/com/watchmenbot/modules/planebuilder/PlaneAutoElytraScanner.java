package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlaneAutoElytraScanner {
    private final PlaneRuntimeConfig config;
    private final BlockView world;

    PlaneAutoElytraScanner(PlaneRuntimeConfig config, BlockView world) {
        this.config = config;
        this.world = world;
    }

    boolean routeAheadBlocked(
        PlaneAutoWalkPlanner.Segment segment,
        BlockPos playerPos,
        int lookaheadThreshold
    ) {
        return blockingRunAhead(segment, playerPos, lookaheadThreshold) > Math.max(0, lookaheadThreshold);
    }

    boolean hazardAhead(
        PlaneAutoWalkPlanner.Segment segment,
        BlockPos playerPos,
        int maxScan
    ) {
        if (segment == null || maxScan < 0) return false;

        int dx = Integer.compare(segment.end().x(), segment.start().x());
        int dz = Integer.compare(segment.end().z(), segment.start().z());
        if (dx == 0 && dz == 0) return false;

        for (int i = 1; i <= maxScan; i++) {
            int x = playerPos.getX() + dx * i;
            int z = playerPos.getZ() + dz * i;
            if (!config.buildArea().contains(x, z)) break;

            if (world.hazard(new BlockPos(x, config.buildY(), z))) return true;
        }

        return false;
    }

    int blockingRunAhead(
        PlaneAutoWalkPlanner.Segment segment,
        BlockPos playerPos,
        int maxScan
    ) {
        if (segment == null || maxScan < 0) return 0;

        int dx = Integer.compare(segment.end().x(), segment.start().x());
        int dz = Integer.compare(segment.end().z(), segment.start().z());
        if (dx == 0 && dz == 0) return 0;

        int run = 0;
        for (int i = 1; i <= maxScan + 1; i++) {
            int x = playerPos.getX() + dx * i;
            int z = playerPos.getZ() + dz * i;
            if (!config.buildArea().contains(x, z)) break;

            BlockPos pos = new BlockPos(x, config.buildY(), z);
            if (!world.blocking(pos)) break;

            run++;
        }

        return run;
    }

    PlaneAutoWalkPlanner.Waypoint safeForwardContinuationTarget(
        PlaneAutoWalkPlanner planner,
        PlaneAutoWalkPlanner.AutoWalkState state,
        BlockPos playerPos
    ) {
        if (planner == null || state == null) return null;

        PlaneAutoWalkPlanner.Waypoint best = null;
        long bestRouteDistance = Long.MAX_VALUE;
        double bestPlayerDistance = Double.MAX_VALUE;
        int traveledBeforeSegment = 0;
        boolean currentSegment = true;
        for (PlaneAutoWalkPlanner.Segment segment : planner.forwardSegments(state)) {
            int dx = Integer.compare(segment.end().x(), segment.start().x());
            int dz = Integer.compare(segment.end().z(), segment.start().z());
            if (dx == 0 && dz == 0) continue;

            int segmentLength = Math.abs(segment.end().x() - segment.start().x())
                + Math.abs(segment.end().z() - segment.start().z());
            int startOffset = currentSegment ? currentSegmentStartOffset(segment, playerPos) : 0;
            for (int offset = startOffset; offset <= segmentLength; offset++) {
                int x = segment.start().x() + dx * offset;
                int z = segment.start().z() + dz * offset;
                BlockPos feet = new BlockPos(x, config.buildY() + 1, z);
                if (!safeLandingTarget(feet)) continue;

                long routeDistance = (long) traveledBeforeSegment + offset;
                double playerDistance = feet.getSquaredDistance(playerPos);
                if (routeDistance < bestRouteDistance || (routeDistance == bestRouteDistance && playerDistance < bestPlayerDistance)) {
                    best = new PlaneAutoWalkPlanner.Waypoint(x, z);
                    bestRouteDistance = routeDistance;
                    bestPlayerDistance = playerDistance;
                }
            }

            traveledBeforeSegment += segmentLength;
            currentSegment = false;
        }

        return best;
    }

    BlockPos safeLandingTarget(BlockPos playerPos, int searchRadius) {
        BlockPos centered = new BlockPos(playerPos.getX(), config.buildY() + 1, playerPos.getZ());
        if (safeLandingTarget(centered)) return centered;

        int radius = Math.max(1, searchRadius);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos target = new BlockPos(playerPos.getX() + dx, config.buildY() + 1, playerPos.getZ() + dz);
                if (!safeLandingTarget(target)) continue;

                double distance = target.getSquaredDistance(playerPos);
                if (distance < bestDistance) {
                    best = target;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    boolean safeLandingTarget(BlockPos feet) {
        if (!config.buildArea().contains(feet.getX(), feet.getZ())) return false;

        BlockPos support = new BlockPos(feet.getX(), config.buildY(), feet.getZ());
        return world.solid(support)
            && !world.hazard(support)
            && world.passable(feet)
            && world.passable(feet.up());
    }

    private int currentSegmentStartOffset(PlaneAutoWalkPlanner.Segment segment, BlockPos playerPos) {
        int length = Math.abs(segment.end().x() - segment.start().x())
            + Math.abs(segment.end().z() - segment.start().z());
        int progress = segment.axis() == PlaneAutoWalkPlanner.SegmentAxis.X
            ? axisProgress(segment.start().x(), segment.end().x(), playerPos.getX())
            : axisProgress(segment.start().z(), segment.end().z(), playerPos.getZ());

        return Math.min(length, Math.max(0, progress + 1));
    }

    private static int axisProgress(int start, int end, int current) {
        if (end >= start) return current - start;

        return start - current;
    }

    interface BlockView {
        boolean blocking(BlockPos pos);

        boolean solid(BlockPos pos);

        boolean hazard(BlockPos pos);

        boolean passable(BlockPos pos);
    }
}
