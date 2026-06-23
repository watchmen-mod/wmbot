package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class StashScannerPathThrottle {
    static final int BURST_LIMIT = 3;
    static final int WINDOW_TICKS = 120;
    static final int COOLDOWN_TICKS = 40;
    static final int FAILURE_SUPPRESS_THRESHOLD = 2;

    private final List<Integer> recentPathStarts = new ArrayList<>();
    private final List<SuppressedNeighborhood> suppressedNeighborhoods = new ArrayList<>();
    private int tick;
    private int cooldownTicks;

    PathStartDecision beforePathStart() {
        tick++;
        pruneRecentPathStarts();
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return PathStartDecision.THROTTLED;
        }

        if (recentPathStarts.size() >= BURST_LIMIT) {
            cooldownTicks = COOLDOWN_TICKS;
            return PathStartDecision.THROTTLED;
        }

        return PathStartDecision.ALLOWED;
    }

    void recordPathStart() {
        recentPathStarts.add(tick);
    }

    void recordPathFailure(StashTarget target, BlockPos playerPos) {
        if (target == null || playerPos == null) return;

        for (int i = 0; i < suppressedNeighborhoods.size(); i++) {
            SuppressedNeighborhood neighborhood = suppressedNeighborhoods.get(i);
            if (neighborhood.matches(target)) {
                suppressedNeighborhoods.set(i, neighborhood.withFailure(playerPos));
                return;
            }
        }

        suppressedNeighborhoods.add(SuppressedNeighborhood.first(target, playerPos));
    }

    boolean suppresses(StashTarget target, BlockPos playerPos) {
        pruneSuppressedNeighborhoods(playerPos);
        return suppressedNeighborhoods.stream().anyMatch(neighborhood -> neighborhood.suppresses(target));
    }

    void reset() {
        recentPathStarts.clear();
        suppressedNeighborhoods.clear();
        tick = 0;
        cooldownTicks = 0;
    }

    private void pruneRecentPathStarts() {
        recentPathStarts.removeIf(startTick -> tick - startTick > WINDOW_TICKS);
    }

    private void pruneSuppressedNeighborhoods(BlockPos playerPos) {
        if (playerPos == null) return;

        Iterator<SuppressedNeighborhood> iterator = suppressedNeighborhoods.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().shouldReconsider(playerPos)) iterator.remove();
        }
    }

    enum PathStartDecision {
        ALLOWED,
        THROTTLED
    }

    private record SuppressedNeighborhood(StashTarget representative, BlockPos failurePlayerPos, int failures) {
        static SuppressedNeighborhood first(StashTarget target, BlockPos playerPos) {
            return new SuppressedNeighborhood(target, playerPos.toImmutable(), 1);
        }

        SuppressedNeighborhood withFailure(BlockPos playerPos) {
            return new SuppressedNeighborhood(representative, playerPos.toImmutable(), failures + 1);
        }

        boolean matches(StashTarget target) {
            return related(representative.interactionPos(), target.interactionPos());
        }

        boolean suppresses(StashTarget target) {
            return failures >= FAILURE_SUPPRESS_THRESHOLD && matches(target);
        }

        boolean shouldReconsider(BlockPos playerPos) {
            return playerPos.getSquaredDistance(failurePlayerPos) >= 12 * 12
                || Math.abs(playerPos.getY() - failurePlayerPos.getY()) >= 4;
        }

        private static boolean related(BlockPos a, BlockPos b) {
            int dx = Math.abs(a.getX() - b.getX());
            int dy = Math.abs(a.getY() - b.getY());
            int dz = Math.abs(a.getZ() - b.getZ());
            return (dx <= 1 && dz <= 1 && dy <= 12) || (dx <= 2 && dz <= 2 && dy <= 4);
        }
    }
}
