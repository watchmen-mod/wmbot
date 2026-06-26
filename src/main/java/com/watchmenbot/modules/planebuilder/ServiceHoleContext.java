package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

final class ServiceHoleContext {
    private final PlaneRuntimeConfig config;
    private final PlaneClientContext context;
    private final PlaneAreaScanner scanner;
    private final Set<BlockPos> blockedHoles = new HashSet<>();

    private BlockPos hole;
    private BlockPos support;

    ServiceHoleContext(PlaneAreaScanner scanner) {
        this(scanner, PlaneRuntimeConfig.DEFAULT, new PlaneClientContext());
    }

    ServiceHoleContext(PlaneAreaScanner scanner, PlaneRuntimeConfig config, PlaneClientContext context) {
        this.scanner = scanner;
        this.config = config;
        this.context = context;
    }

    void reset() {
        hole = null;
        support = null;
        blockedHoles.clear();
    }

    boolean selectNearest() {
        hole = scanner.nearestServiceHole(blockedHoles);
        support = hole == null ? null : hole.down();
        return hole != null;
    }

    boolean selected() {
        return hole != null && support != null;
    }

    BlockPos hole() {
        return hole;
    }

    BlockPos support() {
        return support;
    }

    void clear() {
        hole = null;
        support = null;
    }

    void markSelectedBlocked() {
        if (hole != null) blockedHoles.add(hole.toImmutable());
        clear();
    }

    boolean supportValid() {
        return selected() && scanner.validServiceSupport(support);
    }

    boolean supportReplaceable() {
        return selected() && context.world().getBlockState(support).isReplaceable();
    }

    boolean readyForWorkflow() {
        return status().readyForWorkflow();
    }

    boolean openableServiceHoleBlock() {
        return selected() && (scanner.isServiceHoleBlock(hole) || scanner.isBreakableServiceHoleCap(hole)) && supportValid();
    }

    HoleBlock block() {
        if (!selected()) return HoleBlock.MISSING;

        Block block = context.world().getBlockState(hole).getBlock();
        if (block == config.buildBlock()) return HoleBlock.BUILD_BLOCK;
        if (block == Blocks.ENDER_CHEST) return HoleBlock.ENDER_CHEST;
        if (block instanceof ShulkerBoxBlock) return HoleBlock.SHULKER;
        if (context.world().getBlockState(hole).isReplaceable()) return HoleBlock.REPLACEABLE;
        if (scanner.isBreakableServiceHoleCap(hole)) return HoleBlock.BREAKABLE_CAP;
        return HoleBlock.BLOCKED;
    }

    Status status() {
        return statusFor(selected(), selected() && supportValid(), block());
    }

    static Status statusFor(boolean selected, boolean supportValid, HoleBlock block) {
        if (!selected || block == HoleBlock.MISSING) return Status.MISSING;
        if (!supportValid) return Status.INVALID_SUPPORT;

        return switch (block) {
            case BUILD_BLOCK -> Status.READY_BUILD_BLOCK;
            case BREAKABLE_CAP -> Status.READY_BREAKABLE_CAP;
            case REPLACEABLE -> Status.READY_REPLACEABLE;
            case ENDER_CHEST -> Status.READY_ENDER_CHEST;
            case SHULKER -> Status.READY_SHULKER;
            default -> Status.BLOCKED;
        };
    }

    enum HoleBlock {
        MISSING,
        REPLACEABLE,
        BUILD_BLOCK,
        BREAKABLE_CAP,
        ENDER_CHEST,
        SHULKER,
        BLOCKED
    }

    enum Status {
        MISSING,
        READY_BUILD_BLOCK,
        READY_BREAKABLE_CAP,
        READY_REPLACEABLE,
        READY_ENDER_CHEST,
        READY_SHULKER,
        INVALID_SUPPORT,
        BLOCKED;

        boolean readyForWorkflow() {
            return switch (this) {
                case READY_BUILD_BLOCK, READY_BREAKABLE_CAP, READY_REPLACEABLE, READY_ENDER_CHEST, READY_SHULKER -> true;
                default -> false;
            };
        }

        boolean openForReplenish() {
            return switch (this) {
                case READY_REPLACEABLE, READY_ENDER_CHEST, READY_SHULKER -> true;
                default -> false;
            };
        }
    }
}
