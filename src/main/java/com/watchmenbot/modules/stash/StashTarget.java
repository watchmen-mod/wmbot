package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

record StashTarget(String id, String type, List<BlockPos> positions, BlockPos interactionPos, int expectedSize) {
    double distanceSq(Vec3d from) {
        double bestDistanceSq = Double.MAX_VALUE;

        for (BlockPos pos : positions) {
            double distanceSq = from.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distanceSq < bestDistanceSq) bestDistanceSq = distanceSq;
        }

        return bestDistanceSq;
    }
}
