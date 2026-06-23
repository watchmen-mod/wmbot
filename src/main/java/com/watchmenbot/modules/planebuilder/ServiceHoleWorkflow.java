package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

final class ServiceHoleWorkflow {
    private final ServiceHoleContext serviceHole;
    private final PlaneInventory inventory;
    private final PlaneActionGuards guards;
    private final PlaneWorldActions actions;

    ServiceHoleWorkflow(
        ServiceHoleContext serviceHole,
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneWorldActions actions
    ) {
        this.serviceHole = serviceHole;
        this.inventory = inventory;
        this.guards = guards;
        this.actions = actions;
    }

    Phase select() {
        if (!serviceHole.selectNearest()) {
            return Phase.SERVICE_HOLE_BLOCKED;
        }

        return Phase.PLACING_SUPPORT;
    }

    Phase ensureSupport() {
        if (!serviceHole.selected()) {
            return Phase.SELECTING_SERVICE_HOLE;
        }

        if (serviceHole.supportValid()) {
            return Phase.OPENING_SERVICE_HOLE;
        }

        if (!serviceHole.supportReplaceable()) {
            serviceHole.markSelectedBlocked();
            return Phase.SELECTING_SERVICE_HOLE;
        }

        return actions.placeBuildBlockOrMissing(serviceHole.support(), Phase.PLACING_SUPPORT);
    }

    Phase open() {
        if (!guards.readyForWorldAction()) return Phase.OPENING_SERVICE_HOLE;
        if (!serviceHole.selected()) return Phase.SELECTING_SERVICE_HOLE;

        if (serviceHole.block() == ServiceHoleContext.HoleBlock.REPLACEABLE) {
            return Phase.SERVICE_HOLE_OPEN;
        }

        if (!serviceHole.openableServiceHoleBlock()) {
            serviceHole.markSelectedBlocked();
            return Phase.SELECTING_SERVICE_HOLE;
        }

        return actions.breakWithPickaxe(serviceHole.hole(), Phase.OPENING_SERVICE_HOLE, true);
    }

    Phase close() {
        if (!guards.readyForWorldAction()) return Phase.CLOSING_SERVICE_HOLE;
        Phase unavailable = requireReady();
        if (unavailable != null) return unavailable;

        ServiceHoleContext.Status status = serviceHole.status();
        FindItemResult buildBlock = status == ServiceHoleContext.Status.READY_REPLACEABLE ? inventory.prepareUsableBuildBlock() : null;
        boolean hasBuildBlock = status == ServiceHoleContext.Status.READY_REPLACEABLE
            && inventory.findResultMatchesBuildBlock(buildBlock);
        Phase next = PlaneReplenishDecisions.closeServiceHolePhase(status, hasBuildBlock);
        if (next == Phase.IDLE) {
            actions.clearInstantRebreakTarget();
            serviceHole.clear();
            return Phase.IDLE;
        }
        if (next != Phase.CLOSING_SERVICE_HOLE) {
            return next;
        }

        actions.clearInstantRebreakTarget();
        return actions.placeBuildBlockOrMissing(serviceHole.hole(), buildBlock, Phase.CLOSING_SERVICE_HOLE);
    }

    Phase requireOpen() {
        Phase unavailable = requireReady();
        if (unavailable != null) return unavailable;

        if (serviceHole.status().openForReplenish()) return null;

        return Phase.SERVICE_HOLE_BLOCKED;
    }

    Phase requireReady() {
        return PlaneReplenishDecisions.serviceHoleReadyPhase(serviceHole.status());
    }

    Phase recoverBlocked() {
        if (serviceHole.selected()) {
            serviceHole.markSelectedBlocked();
        }

        return select();
    }
}
