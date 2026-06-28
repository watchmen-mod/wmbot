package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

final class ServiceHoleWorkflow {
    private final ServiceHoleContext serviceHole;
    private final PlaneInventory inventory;
    private final PlaneActionGuards guards;
    private final PlaneWorldActions actions;
    private final ServiceHoleOpenWatchdog openWatchdog;
    private final WorkflowLogger logger;

    ServiceHoleWorkflow(
        ServiceHoleContext serviceHole,
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneWorldActions actions
    ) {
        this(serviceHole, inventory, guards, actions, PlaneWorkflowLoggers.NOOP);
    }

    ServiceHoleWorkflow(
        ServiceHoleContext serviceHole,
        PlaneInventory inventory,
        PlaneActionGuards guards,
        PlaneWorldActions actions,
        WorkflowLogger logger
    ) {
        this.serviceHole = serviceHole;
        this.inventory = inventory;
        this.guards = guards;
        this.actions = actions;
        this.logger = logger;
        openWatchdog = new ServiceHoleOpenWatchdog();
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

        ServiceHoleContext.HoleBlock block = serviceHole.block();
        if (block == ServiceHoleContext.HoleBlock.REPLACEABLE) {
            openWatchdog.reset();
            return Phase.SERVICE_HOLE_OPEN;
        }

        if (!serviceHole.openableServiceHoleBlock()) {
            openWatchdog.reset();
            serviceHole.markSelectedBlocked();
            return Phase.SELECTING_SERVICE_HOLE;
        }

        Phase next = actions.breakWithPickaxe(serviceHole.hole(), Phase.OPENING_SERVICE_HOLE, true);
        if (next != Phase.OPENING_SERVICE_HOLE) {
            openWatchdog.reset();
            return next;
        }

        if (openWatchdog.timeout(serviceHole.hole(), block)) {
            logger.warning(
                "Timed out opening service hole: pos=%s block=%s staleTicks=%d next=%s.",
                serviceHole.hole(),
                block,
                openWatchdog.staleOpenTicks(),
                Phase.SELECTING_SERVICE_HOLE.label()
            );
            actions.clearInstantRebreakTarget();
            serviceHole.markSelectedBlocked();
            openWatchdog.reset();
            return Phase.SELECTING_SERVICE_HOLE;
        }

        return Phase.OPENING_SERVICE_HOLE;
    }

    void resetOpenWatchdog() {
        openWatchdog.reset();
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
