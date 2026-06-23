package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.function.Predicate;

final class PlaneWorldAccess {
    private final PlaneClientContext context;

    PlaneWorldAccess() {
        this(new PlaneClientContext());
    }

    PlaneWorldAccess(PlaneClientContext context) {
        this.context = context;
    }

    boolean canPlaceBlock(BlockPos pos, Block block) {
        return BlockUtils.canPlaceBlock(pos, true, block);
    }

    Direction placeSide(BlockPos pos) {
        return BlockUtils.getPlaceSide(pos);
    }

    boolean isReplaceable(BlockPos pos) {
        return context.world().getBlockState(pos).isReplaceable();
    }

    boolean isBlock(BlockPos pos, Block block) {
        return context.world().getBlockState(pos).isOf(block);
    }

    Block block(BlockPos pos) {
        return context.world().getBlockState(pos).getBlock();
    }

    BlockState blockState(BlockPos pos) {
        return context.world().getBlockState(pos);
    }

    BlockView blockView() {
        return context.world();
    }

    boolean isSolidBlock(BlockPos pos) {
        return context.world().getBlockState(pos).isSolidBlock(context.world(), pos);
    }

    List<ItemEntity> itemEntities(Box box, Predicate<ItemEntity> predicate) {
        return context.world().getEntitiesByClass(ItemEntity.class, box, predicate);
    }
}
