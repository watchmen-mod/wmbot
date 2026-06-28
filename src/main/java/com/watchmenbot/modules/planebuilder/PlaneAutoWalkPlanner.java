package com.watchmenbot.modules.planebuilder;

import java.util.ArrayList;
import java.util.List;

final class PlaneAutoWalkPlanner {
    private final List<Waypoint> waypoints;
    private final List<Lane> lanes;

    PlaneAutoWalkPlanner() {
        this(PlaneRuntimeConfig.DEFAULT);
    }

    PlaneAutoWalkPlanner(PlaneRuntimeConfig config) {
        this(config.buildArea(), config.scanRadius(), config.autoWalkLaneSpacing());
    }

    PlaneAutoWalkPlanner(PlaneAreaBounds bounds, int scanRadius, int laneSpacing) {
        Route route = buildRoute(bounds, scanRadius, laneSpacing);
        waypoints = route.waypoints();
        lanes = route.lanes();
    }

    AutoWalkState initialState(int x, int z) {
        if (waypoints.isEmpty()) return new AutoWalkState(0, 1);
        if (waypoints.size() == 1) return new AutoWalkState(0, 1);

        int bestLane = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int lane = 0; lane < laneCount(); lane++) {
            Lane candidate = lanes.get(lane);
            int clampedX = clamp(x, candidate.minX(), candidate.maxX());
            long distance = squaredDistance(x, z, clampedX, candidate.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestLane = lane;
            }
        }

        return new AutoWalkState(lanes.get(bestLane).endIndex(), 1);
    }

    AutoWalkState advance(AutoWalkState state) {
        if (waypoints.size() <= 1) return new AutoWalkState(0, 1);

        int nextIndex = state.index() + state.direction();
        int nextDirection = state.direction();
        if (nextIndex >= waypoints.size()) {
            nextDirection = -1;
            nextIndex = waypoints.size() - 2;
        }
        else if (nextIndex < 0) {
            nextDirection = 1;
            nextIndex = 1;
        }

        return new AutoWalkState(nextIndex, nextDirection);
    }

    Waypoint localTarget(AutoWalkState state, int x, int z, int maxDistance) {
        Segment segment = segment(state);
        if (segment == null) return waypoint(state);

        int step = Math.max(1, maxDistance);
        if (segment.axis() == SegmentAxis.X) {
            return alignedXTarget(segment, x, z, step);
        }

        return alignedZTarget(segment, x, z, step);
    }

    RouteCorrection correctedTarget(AutoWalkState state, int x, int z, int maxDistance, int corridorRadius) {
        Segment segment = segment(state);
        if (segment == null) return new RouteCorrection(waypoint(state), 0, true);

        int step = Math.max(1, maxDistance);
        int corridor = Math.max(0, corridorRadius);
        if (segment.axis() == SegmentAxis.X) {
            int lineZ = segment.start().z();
            int offAxis = Math.abs(z - lineZ);
            Waypoint target = offAxis > 0
                ? correctedXTarget(segment, x, step)
                : alignedXTarget(segment, x, z, step);
            return new RouteCorrection(target, offAxis, offAxis <= corridor);
        }

        int lineX = segment.start().x();
        int offAxis = Math.abs(x - lineX);
        Waypoint target = offAxis > 0
            ? correctedZTarget(segment, z, step)
            : alignedZTarget(segment, x, z, step);
        return new RouteCorrection(target, offAxis, offAxis <= corridor);
    }

    boolean compatibleWithSegment(AutoWalkState state, int x, int z, int corridorRadius) {
        Segment segment = segment(state);
        if (segment == null) return true;

        int corridor = Math.max(0, corridorRadius);
        if (segment.axis() == SegmentAxis.X) {
            return within(x, segment.start().x(), segment.end().x(), corridor)
                && Math.abs(z - segment.start().z()) <= corridor;
        }

        return within(z, segment.start().z(), segment.end().z(), corridor)
            && Math.abs(x - segment.start().x()) <= corridor;
    }

    boolean endpointReached(AutoWalkState state, int x, int z, int radius) {
        Waypoint waypoint = waypoint(state);
        if (waypoint == null) return false;

        long dx = (long) x - waypoint.x();
        long dz = (long) z - waypoint.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    Segment segment(AutoWalkState state) {
        if (waypoints.isEmpty()) return null;

        Waypoint end = waypoint(state);
        if (end == null) return null;

        int startIndex = clamp(state.index() - state.direction(), 0, waypoints.size() - 1);
        Waypoint start = waypoints.get(startIndex);
        return new Segment(start, end, start.z() == end.z() ? SegmentAxis.X : SegmentAxis.Z);
    }

    List<Segment> forwardSegments(AutoWalkState state) {
        if (waypoints.size() <= 1) return List.of();

        List<Segment> segments = new ArrayList<>();
        AutoWalkState cursor = state;
        while (cursor.index() >= 0 && cursor.index() < waypoints.size()) {
            Segment segment = segment(cursor);
            if (segment == null) break;

            segments.add(segment);

            int nextIndex = cursor.index() + cursor.direction();
            if (nextIndex < 0 || nextIndex >= waypoints.size()) break;

            cursor = new AutoWalkState(nextIndex, cursor.direction());
        }

        return List.copyOf(segments);
    }

    Waypoint waypoint(AutoWalkState state) {
        if (waypoints.isEmpty()) return null;
        return waypoints.get(clamp(state.index(), 0, waypoints.size() - 1));
    }

    List<Waypoint> waypoints() {
        return List.copyOf(waypoints);
    }

    private int laneCount() {
        return lanes.size();
    }

    private Waypoint laneStart(int lane) {
        return waypoints.get(lanes.get(lane).startIndex());
    }

    private Waypoint laneEnd(int lane) {
        return waypoints.get(lanes.get(lane).endIndex());
    }

    private static Route buildRoute(PlaneAreaBounds bounds, int scanRadius, int laneSpacing) {
        int xMin = insetMin(bounds.minX(), bounds.maxX(), scanRadius);
        int xMax = insetMax(bounds.minX(), bounds.maxX(), scanRadius);
        int zMin = insetMin(bounds.minZ(), bounds.maxZ(), scanRadius);
        int zMax = insetMax(bounds.minZ(), bounds.maxZ(), scanRadius);

        List<Integer> lanes = laneCenters(zMin, zMax, Math.max(1, laneSpacing));
        List<Waypoint> route = new ArrayList<>(lanes.size() * 2);
        List<Lane> routeLanes = new ArrayList<>(lanes.size());
        for (int i = 0; i < lanes.size(); i++) {
            int z = lanes.get(i);
            int startIndex = route.size();
            if (i % 2 == 0) {
                route.add(new Waypoint(xMin, z));
                if (xMax != xMin) route.add(new Waypoint(xMax, z));
            }
            else {
                route.add(new Waypoint(xMax, z));
                if (xMax != xMin) route.add(new Waypoint(xMin, z));
            }
            routeLanes.add(new Lane(startIndex, route.size() - 1, z, Math.min(xMin, xMax), Math.max(xMin, xMax)));
        }

        return new Route(List.copyOf(route), List.copyOf(routeLanes));
    }

    private static List<Integer> laneCenters(int min, int max, int spacing) {
        List<Integer> lanes = new ArrayList<>();
        lanes.add(min);
        for (int z = min + spacing; z < max; z += spacing) {
            lanes.add(z);
        }
        if (max != min) lanes.add(max);

        return lanes;
    }

    private static int insetMin(int min, int max, int inset) {
        if (max - min <= inset * 2) return min + (max - min) / 2;
        return min + inset;
    }

    private static int insetMax(int min, int max, int inset) {
        if (max - min <= inset * 2) return min + (max - min) / 2;
        return max - inset;
    }

    private static long squaredDistance(int ax, int az, int bx, int bz) {
        long dx = (long) ax - bx;
        long dz = (long) az - bz;
        return dx * dx + dz * dz;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean within(int value, int a, int b, int margin) {
        int min = Math.min(a, b) - margin;
        int max = Math.max(a, b) + margin;
        return value >= min && value <= max;
    }

    private static Waypoint alignedXTarget(Segment segment, int x, int z, int step) {
        int minX = Math.min(segment.start().x(), segment.end().x());
        int maxX = Math.max(segment.start().x(), segment.end().x());
        int currentX = clamp(x, minX, maxX);
        int lineZ = segment.start().z();
        int offAxis = Math.abs(z - lineZ);
        int advance = cappedAdvance(step, offAxis);
        int targetX = segment.end().x() >= segment.start().x()
            ? Math.min(segment.end().x(), currentX + advance)
            : Math.max(segment.end().x(), currentX - advance);

        return new Waypoint(targetX, lineZ);
    }

    private static Waypoint alignedZTarget(Segment segment, int x, int z, int step) {
        int minZ = Math.min(segment.start().z(), segment.end().z());
        int maxZ = Math.max(segment.start().z(), segment.end().z());
        int currentZ = clamp(z, minZ, maxZ);
        int lineX = segment.start().x();
        int offAxis = Math.abs(x - lineX);
        int advance = cappedAdvance(step, offAxis);
        int targetZ = segment.end().z() >= segment.start().z()
            ? Math.min(segment.end().z(), currentZ + advance)
            : Math.max(segment.end().z(), currentZ - advance);

        return new Waypoint(lineX, targetZ);
    }

    private static Waypoint correctedXTarget(Segment segment, int x, int step) {
        int minX = Math.min(segment.start().x(), segment.end().x());
        int maxX = Math.max(segment.start().x(), segment.end().x());
        int currentX = clamp(x, minX, maxX);
        int targetX = segment.end().x() >= segment.start().x()
            ? Math.min(segment.end().x(), currentX + step)
            : Math.max(segment.end().x(), currentX - step);

        return new Waypoint(targetX, segment.start().z());
    }

    private static Waypoint correctedZTarget(Segment segment, int z, int step) {
        int minZ = Math.min(segment.start().z(), segment.end().z());
        int maxZ = Math.max(segment.start().z(), segment.end().z());
        int currentZ = clamp(z, minZ, maxZ);
        int targetZ = segment.end().z() >= segment.start().z()
            ? Math.min(segment.end().z(), currentZ + step)
            : Math.max(segment.end().z(), currentZ - step);

        return new Waypoint(segment.start().x(), targetZ);
    }

    private static int cappedAdvance(int step, int offAxis) {
        if (offAxis >= step) return 0;
        return (int) Math.floor(Math.sqrt((long) step * step - (long) offAxis * offAxis));
    }

    record Waypoint(int x, int z) {
    }

    record AutoWalkState(int index, int direction) {
    }

    record Segment(Waypoint start, Waypoint end, SegmentAxis axis) {
    }

    record RouteCorrection(Waypoint target, int offAxisDistance, boolean withinCorridor) {
    }

    enum SegmentAxis {
        X,
        Z
    }

    private record Lane(int startIndex, int endIndex, int z, int minX, int maxX) {
    }

    private record Route(List<Waypoint> waypoints, List<Lane> lanes) {
    }
}
