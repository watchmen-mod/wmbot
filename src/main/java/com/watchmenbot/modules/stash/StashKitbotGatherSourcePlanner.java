package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiPredicate;

final class StashKitbotGatherSourcePlanner {
    private StashKitbotGatherSourcePlanner() {
    }

    static OptionalInt chooseClosestSourceIndex(
        KitRequest request,
        Vec3d playerEyes,
        BlockPos playerPos,
        double interactionRange,
        BiPredicate<KitSource, BlockPos> suppressed
    ) {
        if (request == null || request.sources == null || request.sources.isEmpty()) return OptionalInt.empty();

        Set<Integer> skipped = request.gather.skippedSourceIndexes;
        double rangeSq = interactionRange * interactionRange;
        return java.util.stream.IntStream.range(0, request.sources.size())
            .filter(index -> !skipped.contains(index))
            .filter(index -> !suppressed.test(request.sources.get(index), playerPos))
            .boxed()
            .min(Comparator
                .comparingInt((Integer index) -> sourceDistanceSq(request.sources.get(index), playerEyes) <= rangeSq ? 0 : 1)
                .thenComparingDouble(index -> sourceDistanceSq(request.sources.get(index), playerEyes)))
            .map(OptionalInt::of)
            .orElse(OptionalInt.empty());
    }

    static boolean allRemainingSourcesSuppressed(KitRequest request, BlockPos playerPos, BiPredicate<KitSource, BlockPos> suppressed) {
        if (request == null || request.sources == null || request.sources.isEmpty()) return false;

        boolean hasRemaining = false;
        for (int i = 0; i < request.sources.size(); i++) {
            if (request.gather.skippedSourceIndexes.contains(i)) continue;

            hasRemaining = true;
            if (!suppressed.test(request.sources.get(i), playerPos)) return false;
        }

        return hasRemaining;
    }

    private static double sourceDistanceSq(KitSource source, Vec3d playerEyes) {
        return playerEyes.squaredDistanceTo(Vec3d.ofCenter(source.interactionPos()));
    }
}
