package com.watchmenbot.modules.stash;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StashTargetDiscovery {
    static final int SINGLE_STORAGE_SIZE = 27;
    static final int DOUBLE_CHEST_SIZE = 54;

    StashDiscoveryResult discover(MinecraftClient mc, String dimensionId, int scanRadius, boolean baritonePathing, double interactionRange, boolean skipShulkers) {
        if (mc.player == null || mc.world == null) return new StashDiscoveryResult(List.of(), List.of());

        double maxDistanceSq = scanRadius * scanRadius;
        Vec3d eyes = mc.player.getEyePos();
        List<BlockEntity> blockEntities = loadedBlockEntities(mc, scanRadius);
        blockEntities.sort(Comparator.comparingDouble(be -> mc.player.getBlockPos().getSquaredDistance(be.getPos())));

        List<StashTarget> targets = new ArrayList<>();
        List<StashSkippedContainer> skipped = new ArrayList<>();
        for (BlockEntity blockEntity : blockEntities) {
            BlockPos pos = blockEntity.getPos();
            if (mc.player.getBlockPos().getSquaredDistance(pos) > maxDistanceSq) continue;

            TargetResult result = createTargetResult(mc, dimensionId, pos, skipShulkers);
            if (result.skipped() != null) {
                skipped.add(result.skipped());
                continue;
            }

            StashTarget target = result.target();
            if (target == null) continue;
            if (!baritonePathing && !isCloseEnough(target, eyes, interactionRange)) continue;

            targets.add(target);
        }

        return new StashDiscoveryResult(targets, skipped);
    }

    StashTarget createTarget(MinecraftClient mc, String dimensionId, BlockPos pos) {
        return createTargetResult(mc, dimensionId, pos, false).target();
    }

    private TargetResult createTargetResult(MinecraftClient mc, String dimensionId, BlockPos pos, boolean skipShulkers) {
        if (mc.world == null) return TargetResult.empty();

        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (shouldSkipScanTarget(block, skipShulkers)) return TargetResult.empty();

        if (block instanceof ChestBlock) return TargetResult.target(createChestTarget(mc, dimensionId, pos, state));
        if (block instanceof BarrelBlock) return TargetResult.target(createSingleTarget(dimensionId, pos, "minecraft:barrel", SINGLE_STORAGE_SIZE));
        if (block instanceof ShulkerBoxBlock) return createShulkerTarget(mc, dimensionId, pos, state);

        return TargetResult.empty();
    }

    static boolean shouldSkipScanTarget(Block block, boolean skipShulkers) {
        return shouldSkipScanTarget(block instanceof ShulkerBoxBlock, skipShulkers);
    }

    static boolean shouldSkipScanTarget(boolean targetIsShulker, boolean skipShulkers) {
        return skipShulkers && targetIsShulker;
    }

    private StashTarget createChestTarget(MinecraftClient mc, String dimensionId, BlockPos pos, BlockState state) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos.toImmutable());
        int expectedSize = SINGLE_STORAGE_SIZE;

        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
        if (chestType != ChestType.SINGLE) {
            BlockPos other = findOtherChestHalf(mc, pos, state);
            if (other != null) {
                positions.add(other.toImmutable());
                positions.sort(StashTargetDiscovery::comparePos);
                expectedSize = DOUBLE_CHEST_SIZE;
            }
        }

        BlockPos canonical = positions.getFirst();
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        return new StashTarget(containerId(dimensionId, canonical), blockId, positions, canonical, expectedSize);
    }

    private BlockPos findOtherChestHalf(MinecraftClient mc, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        Direction facing = state.get(ChestBlock.FACING);

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = mc.world.getBlockState(neighborPos);
            if (!neighbor.isOf(block)) continue;
            if (neighbor.get(ChestBlock.CHEST_TYPE) != type.getOpposite()) continue;
            if (neighbor.get(ChestBlock.FACING) != facing) continue;
            return neighborPos;
        }

        return null;
    }

    private TargetResult createShulkerTarget(MinecraftClient mc, String dimensionId, BlockPos pos, BlockState state) {
        Direction openingDirection = state.get(ShulkerBoxBlock.FACING);
        BlockPos openingPos = pos.offset(openingDirection);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        StashTarget target = createSingleTarget(dimensionId, pos, blockId, SINGLE_STORAGE_SIZE);

        if (!mc.world.getBlockState(openingPos).getCollisionShape(mc.world, openingPos).isEmpty()) {
            return TargetResult.skipped(StashInventoryCache.skipped(target, "blocked-opening"));
        }

        return TargetResult.target(target);
    }

    private StashTarget createSingleTarget(String dimensionId, BlockPos pos, String type, int expectedSize) {
        BlockPos immutable = pos.toImmutable();
        return new StashTarget(containerId(dimensionId, immutable), type, List.of(immutable), immutable, expectedSize);
    }

    private List<BlockEntity> loadedBlockEntities(MinecraftClient mc, int scanRadius) {
        List<BlockEntity> blockEntities = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        BlockPos playerPos = mc.player.getBlockPos();
        int minChunkX = (playerPos.getX() - scanRadius) >> 4;
        int maxChunkX = (playerPos.getX() + scanRadius) >> 4;
        int minChunkZ = (playerPos.getZ() - scanRadius) >> 4;
        int maxChunkZ = (playerPos.getZ() + scanRadius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (seen.add(blockEntity.getPos())) blockEntities.add(blockEntity);
                }
            }
        }

        for (BlockEntity blockEntity : mc.world.getBlockEntities()) {
            if (seen.add(blockEntity.getPos())) blockEntities.add(blockEntity);
        }

        return blockEntities;
    }

    private boolean isCloseEnough(StashTarget target, Vec3d eyes, double interactionRange) {
        double rangeSq = interactionRange * interactionRange;
        for (BlockPos pos : target.positions()) {
            if (eyes.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq) return true;
        }

        return false;
    }

    private static String containerId(String dimensionId, BlockPos pos) {
        return dimensionId + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        if (y != 0) return y;
        return Integer.compare(a.getZ(), b.getZ());
    }

    private record TargetResult(StashTarget target, StashSkippedContainer skipped) {
        private static TargetResult target(StashTarget target) {
            return new TargetResult(target, null);
        }

        private static TargetResult skipped(StashSkippedContainer skipped) {
            return new TargetResult(null, skipped);
        }

        private static TargetResult empty() {
            return new TargetResult(null, null);
        }
    }
}
