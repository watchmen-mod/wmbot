package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

final class PlaneAutoElytraWorld implements PlaneAutoElytraScanner.BlockView {
    private final PlaneWorldAccess world;

    PlaneAutoElytraWorld(PlaneWorldAccess world) {
        this.world = world;
    }

    @Override
    public boolean blocking(BlockPos pos) {
        return solid(pos) || hazard(pos);
    }

    @Override
    public boolean solid(BlockPos pos) {
        return world.isSolidBlock(pos);
    }

    @Override
    public boolean hazard(BlockPos pos) {
        return PlaneAutoElytraHazards.isHazard(world.block(pos));
    }

    @Override
    public boolean passable(BlockPos pos) {
        BlockState state = world.blockState(pos);
        return state.isReplaceable() || state.getCollisionShape(world.blockView(), pos).isEmpty();
    }
}
