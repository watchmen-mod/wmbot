package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiPredicate;

final class StashScannerTargetPlanner {
    private static final double VERTICAL_COST = 24.0;
    private static final double SAME_AISLE_BONUS = 256.0;
    private static final double TIMEOUT_NEIGHBORHOOD_PENALTY = 1_000_000.0;
    private static final int TIMEOUT_RECONSIDER_DISTANCE_SQ = 12 * 12;
    private static final int TIMEOUT_RECONSIDER_Y_DELTA = 4;

    private final java.util.List<TimeoutNeighborhood> timeoutNeighborhoods = new java.util.ArrayList<>();

    Optional<StashTarget> chooseNext(Collection<StashTarget> targets, Vec3d playerEyes, BlockPos playerPos) {
        return chooseNext(targets, playerEyes, playerPos, (target, pos) -> false);
    }

    Optional<StashTarget> chooseNext(Collection<StashTarget> targets, Vec3d playerEyes, BlockPos playerPos, BiPredicate<StashTarget, BlockPos> suppressed) {
        if (targets == null || targets.isEmpty()) return Optional.empty();

        pruneTimeoutNeighborhoods(playerPos);
        return targets.stream()
            .filter(target -> !suppressed.test(target, playerPos))
            .min(Comparator.comparingDouble(target -> score(target, playerEyes, playerPos)));
    }

    void recordPathTimeout(StashTarget target, BlockPos playerPos) {
        if (target == null || playerPos == null) return;

        timeoutNeighborhoods.add(new TimeoutNeighborhood(target, playerPos.toImmutable()));
    }

    void reset() {
        timeoutNeighborhoods.clear();
    }

    boolean hasActiveTimeoutPenalty(StashTarget target, BlockPos playerPos) {
        pruneTimeoutNeighborhoods(playerPos);
        return timeoutNeighborhoods.stream().anyMatch(neighborhood -> neighborhood.penalizes(target));
    }

    private double score(StashTarget target, Vec3d playerEyes, BlockPos playerPos) {
        double score = target.distanceSq(playerEyes);
        if (playerPos != null) {
            score += Math.abs(target.interactionPos().getY() - playerPos.getY()) * VERTICAL_COST;
            if (sameAisle(target.interactionPos(), playerPos)) score -= SAME_AISLE_BONUS;
        }

        if (hasActiveTimeoutPenalty(target, playerPos)) score += TIMEOUT_NEIGHBORHOOD_PENALTY;
        return score;
    }

    private boolean sameAisle(BlockPos target, BlockPos playerPos) {
        int dx = Math.abs(target.getX() - playerPos.getX());
        int dz = Math.abs(target.getZ() - playerPos.getZ());
        int dy = Math.abs(target.getY() - playerPos.getY());
        return dy <= 3 && (dx <= 4 || dz <= 4);
    }

    private void pruneTimeoutNeighborhoods(BlockPos playerPos) {
        if (playerPos == null) return;

        Iterator<TimeoutNeighborhood> iterator = timeoutNeighborhoods.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().shouldReconsider(playerPos)) iterator.remove();
        }
    }

    private record TimeoutNeighborhood(StashTarget timedOutTarget, BlockPos timeoutPlayerPos) {
        boolean penalizes(StashTarget target) {
            if (target == null || timedOutTarget.id().equals(target.id())) return false;

            BlockPos timedOut = timedOutTarget.interactionPos();
            BlockPos candidate = target.interactionPos();
            int dx = Math.abs(timedOut.getX() - candidate.getX());
            int dy = Math.abs(timedOut.getY() - candidate.getY());
            int dz = Math.abs(timedOut.getZ() - candidate.getZ());

            boolean sameVerticalColumn = dx <= 1 && dz <= 1 && dy <= 12;
            boolean sameDenseFace = dx <= 2 && dz <= 2 && dy <= 4;
            return sameVerticalColumn || sameDenseFace;
        }

        boolean shouldReconsider(BlockPos playerPos) {
            return playerPos.getSquaredDistance(timeoutPlayerPos) >= TIMEOUT_RECONSIDER_DISTANCE_SQ
                || Math.abs(playerPos.getY() - timeoutPlayerPos.getY()) >= TIMEOUT_RECONSIDER_Y_DELTA;
        }
    }
}
