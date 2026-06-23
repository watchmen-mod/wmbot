package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class PlanePlacement {
    private final PlaneRuntimeConfig config;
    private final PlaneClientContext context;
    private final PlaneInventory inventory;
    private final PlaneActionGuards guards;
    private final PlaneWorldAccess world;
    private final PlacementExecutor executor;

    PlanePlacement(PlaneInventory inventory, PlaneActionGuards guards) {
        this(inventory, guards, PlaneRuntimeConfig.DEFAULT, new PlaneClientContext(), new PlaneWorldAccess());
    }

    PlanePlacement(
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneWorldAccess world
    ) {
        this(inventory, guards, config, context, world, new PlaneEndermanLookSafety());
    }

    PlanePlacement(
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneWorldAccess world,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this.config = config;
        this.context = context;
        this.inventory = inventory;
        this.guards = guards;
        this.world = world;
        executor = new PlacementExecutor(guards, new PlaneActionExecutor(config, endermanLookSafety));
    }

    boolean placeObsidian(BlockPos target, FindItemResult item) {
        return placeBlock(target, item, config.buildBlock());
    }

    boolean placeBlock(BlockPos target, FindItemResult item, Block expectedBlock) {
        if (!guards.readyForUseAction()) return false;
        if (target == null || !inventory.findResultMatchesBlock(item, expectedBlock)) return false;
        if (!world.canPlaceBlock(target, expectedBlock)) return false;

        Vec3d hit = Vec3d.ofCenter(target);
        Direction side = world.placeSide(target);
        BlockPos neighbor;
        if (side == null) {
            side = Direction.UP;
            neighbor = target;
        }
        else {
            neighbor = target.offset(side);
            hit = hit.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        BlockHitResult hitResult = new BlockHitResult(hit, side.getOpposite(), neighbor, false);
        if (!target.equals(placementTarget(target, hitResult))) return false;

        Hand hand = item.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        return executor.execute(new PlacementRequest(
            target,
            item,
            expectedBlock,
            hit,
            hitResult,
            hand,
            () -> inventory.findResultMatchesBlock(item, expectedBlock)
                && world.canPlaceBlock(target, expectedBlock)
                && target.equals(placementTarget(target, hitResult)),
            () -> handMatchesBlock(hand, expectedBlock)
        ));
    }

    boolean placeShulkerInServiceHole(BlockPos serviceHole, BlockPos serviceSupport, FindItemResult shulker) {
        if (!guards.readyForUseAction()) return false;
        if (serviceHole == null || serviceSupport == null || shulker == null || !shulker.isHotbar()) return false;
        if (!world.isReplaceable(serviceHole)) return false;
        if (!inventory.isShulkerWithEnderChests(context.player().getInventory().getStack(shulker.slot()))) return false;

        Vec3d hit = Vec3d.ofCenter(serviceSupport).add(0.0, 0.5, 0.0);
        BlockHitResult hitResult = new BlockHitResult(hit, Direction.UP, serviceSupport, false);
        if (!serviceHole.equals(placementTarget(serviceHole, hitResult))) return false;

        return executor.execute(new PlacementRequest(
            serviceHole,
            shulker,
            config.buildBlock(),
            hit,
            hitResult,
            Hand.MAIN_HAND,
            () -> world.isReplaceable(serviceHole)
                && inventory.isShulkerWithEnderChests(context.player().getInventory().getStack(shulker.slot()))
                && serviceHole.equals(placementTarget(serviceHole, hitResult)),
            () -> inventory.isShulkerWithEnderChests(context.player().getMainHandStack())
        ));
    }

    BlockPos placementTarget(BlockPos target, BlockHitResult hitResult) {
        if (target.equals(hitResult.getBlockPos()) && world.isReplaceable(target)) return target;

        BlockPos offsetTarget = hitResult.getBlockPos().offset(hitResult.getSide());
        if (target.equals(offsetTarget) && world.isReplaceable(target)) return offsetTarget;

        return null;
    }

    private boolean handMatchesBlock(Hand hand, Block block) {
        return inventory.isBlockStack(hand == Hand.OFF_HAND ? context.player().getOffHandStack() : context.player().getMainHandStack(), block);
    }

}
