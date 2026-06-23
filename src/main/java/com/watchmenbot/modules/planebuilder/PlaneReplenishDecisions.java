package com.watchmenbot.modules.planebuilder;

final class PlaneReplenishDecisions {
    private PlaneReplenishDecisions() {
    }

    static boolean active(Phase phase, boolean serviceHoleSelected) {
        return PlanePhasePolicy.replenishActive(phase, serviceHoleSelected);
    }

    static Phase sourcePhase(int buildBlockCount, int targetBuildBlocks, int looseEnderChestCount) {
        if (buildBlockCount >= targetBuildBlocks) return Phase.CLOSING_SERVICE_HOLE;
        if (looseEnderChestCount > 0) return Phase.PLACING_ENDER_CHEST;
        return Phase.PLACING_ENDER_CHEST_SHULKER;
    }

    static Phase sourcePhase(int buildBlockCount, int targetBuildBlocks, int looseEnderChestCount, boolean hasShulkerSource) {
        if (buildBlockCount >= targetBuildBlocks) return Phase.CLOSING_SERVICE_HOLE;
        return missingEnderChestRecoveryPhase(Phase.MISSING_ENDER_CHEST, looseEnderChestCount, hasShulkerSource);
    }

    static Phase missingEnderChestRecoveryPhase(Phase missingPhase, int looseEnderChestCount, boolean hasShulkerSource) {
        if (looseEnderChestCount > 0) return Phase.PLACING_ENDER_CHEST;
        if (hasShulkerSource) return Phase.PLACING_ENDER_CHEST_SHULKER;
        return missingPhase;
    }

    static Phase afterEnderChestBreak(ServiceHoleContext.Status status, int buildBlockCount, int targetBuildBlocks) {
        return switch (status) {
            case READY_REPLACEABLE -> buildBlockCount >= targetBuildBlocks ? Phase.CLOSING_SERVICE_HOLE : Phase.PLACING_ENDER_CHEST;
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    static Phase enderChestPlacementPhase(ServiceHoleContext.Status status) {
        return switch (status) {
            case READY_ENDER_CHEST -> Phase.BREAKING_ENDER_CHEST;
            case READY_SHULKER -> Phase.ENDER_CHEST_SHULKER_PLACED;
            case READY_REPLACEABLE -> Phase.PLACING_ENDER_CHEST;
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    static Phase enderChestInventoryPhase(boolean usableEnderChest, boolean mainInventoryEnderChest) {
        return usableEnderChest || mainInventoryEnderChest
            ? Phase.PLACING_ENDER_CHEST
            : Phase.MISSING_ENDER_CHEST;
    }

    static Phase closeServiceHolePhase(ServiceHoleContext.Status status, boolean hasBuildBlock) {
        return switch (status) {
            case READY_BUILD_BLOCK -> Phase.IDLE;
            case READY_ENDER_CHEST -> Phase.BREAKING_ENDER_CHEST;
            case READY_REPLACEABLE -> hasBuildBlock ? Phase.CLOSING_SERVICE_HOLE : Phase.MISSING_OBSIDIAN;
            default -> Phase.SERVICE_HOLE_BLOCKED;
        };
    }

    static Phase serviceHoleReadyPhase(ServiceHoleContext.Status status) {
        if (status == ServiceHoleContext.Status.MISSING) {
            return Phase.SELECTING_SERVICE_HOLE;
        }
        if (!status.readyForWorkflow()) {
            return Phase.SERVICE_HOLE_BLOCKED;
        }

        return null;
    }

    static Phase missingObsidianRecoveryPhase(int buildBlockCount, int replenishMinBuildBlocks, boolean serviceHoleSelected) {
        if (buildBlockCount > 0 && buildBlockCount < replenishMinBuildBlocks) return Phase.SELECTING_SERVICE_HOLE;
        return serviceHoleSelected ? Phase.CLOSING_SERVICE_HOLE : Phase.MISSING_OBSIDIAN;
    }

    static Phase missingObsidianServiceHoleRecoveryPhase(ServiceHoleContext.Status status, boolean hasBuildBlock) {
        return afterNormalServiceHoleClose(closeServiceHolePhase(status, hasBuildBlock));
    }

    static Phase afterNormalServiceHoleClose(Phase closePhase) {
        return closePhase == Phase.IDLE ? Phase.PICKING_UP_REPLENISH_DROPS : closePhase;
    }

    static Phase cleanupPickupPhase(boolean targetStillAvailable) {
        return targetStillAvailable ? Phase.PICKING_UP_REPLENISH_DROPS : Phase.MOVING_TO_TRASH_EDGE;
    }

    static Phase afterShulkerRemoved(int looseEnderChestCount) {
        return looseEnderChestCount > 0 ? Phase.PLACING_ENDER_CHEST : Phase.MISSING_ENDER_CHEST;
    }

    static Phase shulkerExtractionUnavailable(int baselineLooseEnderChests, int currentLooseEnderChests) {
        return currentLooseEnderChests > baselineLooseEnderChests
            ? Phase.BREAKING_ENDER_CHEST_SHULKER
            : Phase.MISSING_ENDER_CHEST;
    }

    static Phase unavailableShulkerSource(boolean hasAvailableShulkerSource) {
        return hasAvailableShulkerSource
            ? Phase.PLACING_ENDER_CHEST_SHULKER
            : Phase.MISSING_ENDER_CHEST_SHULKER;
    }
}
