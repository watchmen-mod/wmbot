package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class PlaneBuilderStats {
    private static final long SECOND_MILLIS = 1_000L;
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 3_600_000L;

    private final Set<BlockPos> pendingTargets = new HashSet<>();
    private final Deque<Long> placementTimes = new ArrayDeque<>();
    private long startedAtMillis = -1L;
    private long placedThisSession;

    void reset() {
        pendingTargets.clear();
        placementTimes.clear();
        startedAtMillis = -1L;
        placedThisSession = 0;
    }

    void start(long nowMillis) {
        if (startedAtMillis < 0) startedAtMillis = nowMillis;
    }

    void attemptedPlacement(BlockPos target) {
        if (target != null) pendingTargets.add(target.toImmutable());
    }

    boolean confirmPlaced(BlockPos target, long nowMillis) {
        if (target == null || !pendingTargets.remove(target)) return false;

        placedThisSession++;
        placementTimes.addLast(nowMillis);
        prune(nowMillis);
        return true;
    }

    Snapshot snapshot(long nowMillis) {
        prune(nowMillis);
        return new Snapshot(
            runtimeMillis(nowMillis),
            placedThisSession,
            rate(nowMillis, SECOND_MILLIS, 1_000.0),
            rate(nowMillis, MINUTE_MILLIS, 60_000.0),
            rate(nowMillis, HOUR_MILLIS, 3_600_000.0)
        );
    }

    Set<BlockPos> pendingTargets() {
        return Set.copyOf(pendingTargets);
    }

    private void prune(long nowMillis) {
        long earliest = nowMillis - HOUR_MILLIS;
        while (!placementTimes.isEmpty() && placementTimes.peekFirst() < earliest) {
            placementTimes.removeFirst();
        }
    }

    private double rate(long nowMillis, long windowMillis, double unitMillis) {
        long earliest = nowMillis - windowMillis;
        int count = 0;
        Iterator<Long> iterator = placementTimes.descendingIterator();
        while (iterator.hasNext()) {
            long placedAt = iterator.next();
            if (placedAt < earliest) break;
            count++;
        }

        return count * (unitMillis / windowMillis);
    }

    private long runtimeMillis(long nowMillis) {
        if (startedAtMillis < 0) return 0L;
        return Math.max(0L, nowMillis - startedAtMillis);
    }

    record Snapshot(long runtimeMillis, long placedThisSession, double perSecond, double perMinute, double perHour) {
    }
}
