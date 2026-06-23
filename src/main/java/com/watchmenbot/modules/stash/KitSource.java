package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;

import java.util.List;

record KitSource(String containerId, BlockPos interactionPos, List<Integer> slots, int cachedCount) {
}
