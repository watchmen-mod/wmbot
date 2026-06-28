package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

final class StashKitbotGatherWorkflow {
    private static final int TRANSFER_VERIFY_TICKS = 20;
    private static final int SUPPRESSED_SOURCE_WAIT_TICKS = 10;

    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashTargetDiscovery discovery;
    private final StashNavigator navigator;
    private final StashContainerInteractor interactor;
    private final StashContainerReader reader;
    private final StashInventoryCache cache;
    private final StashKitbotInventory inventory;
    private final StashKitbotEvents events;
    private final StashScannerPathProgress pathProgress = new StashScannerPathProgress();
    private final StashScannerPathThrottle pathThrottle = new StashScannerPathThrottle();

    StashKitbotGatherWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashTargetDiscovery discovery,
        StashNavigator navigator,
        StashContainerInteractor interactor,
        StashContainerReader reader,
        StashInventoryCache cache,
        StashKitbotInventory inventory,
        StashKitbotEvents events
    ) {
        this.mc = mc;
        this.session = session;
        this.discovery = discovery;
        this.navigator = navigator;
        this.interactor = interactor;
        this.reader = reader;
        this.cache = cache;
        this.inventory = inventory;
        this.events = events;
    }

    void tick(Settings settings) {
        switch (session.phase()) {
            case IDLE -> startNextSource(settings);
            case PATHING -> tickPathing(settings);
            case OPENING -> tickOpening(settings);
            case TAKING -> tickTaking(settings);
            case VERIFYING_TRANSFER -> tickVerifyingTransfer(settings);
            default -> {
            }
        }
    }

    private void startNextSource(Settings settings) {
        KitRequest request = session.activeRequest();
        refreshConfirmedGathered(request);
        if (request.gather.gathered >= request.count) {
            completeRequest(settings);
            return;
        }

        OptionalInt sourceIndex = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
            request,
            mc.player.getEyePos(),
            mc.player.getBlockPos(),
            settings.interactionRange() + 0.5,
            this::sourceSuppressed
        );
        if (sourceIndex.isEmpty()) {
            if (StashKitbotGatherSourcePlanner.allRemainingSourcesSuppressed(request, mc.player.getBlockPos(), this::sourceSuppressed)) {
                sourceIndex = StashKitbotGatherSourcePlanner.chooseClosestSourceIndex(
                    request,
                    mc.player.getEyePos(),
                    mc.player.getBlockPos(),
                    settings.interactionRange() + 0.5,
                    (source, pos) -> false
                );
            }
        }

        KitSource source = sourceIndex.isEmpty() ? null : request.sources.get(sourceIndex.getAsInt());
        if (source == null) {
            events.warning("Failed to start stash kit gather for %s: no live source for '%s' after collecting %d/%d.",
                request.requester,
                request.kitName,
                request.gather.gathered,
                request.count
            );
            events.failRequest("Could not find enough live '%s' shulkers. Collected %d/%d.".formatted(
                request.kitName,
                request.gather.gathered,
                request.count
            ));
            return;
        }

        StashTarget target = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), source.interactionPos());
        if (target == null || !target.id().equals(source.containerId())) {
            events.warning("Skipping changed or missing source container at %s.", StashClientUtils.formatPos(source.interactionPos()));
            markSourceSkipped(request, sourceIndex.getAsInt());
            session.phase(KitbotPhase.IDLE);
            return;
        }

        request.gather.sourceIndex = sourceIndex.getAsInt();
        session.currentTarget(target);
        events.info("Selected stash kit source at %s for %s/%s (%d/%d gathered).",
            StashClientUtils.formatPos(target.interactionPos()),
            request.requester,
            request.kitName,
            request.gather.gathered,
            request.count
        );
        if (isCloseEnough(target, settings)) {
            if (interactWithCurrent(settings)) notifyGatherStarted(request);
            return;
        }

        if (!navigator.canPath()) {
            events.warning("Failed to start stash kit gather for %s: pathing is unavailable and source at %s is out of range.",
                request.requester,
                StashClientUtils.formatPos(target.interactionPos())
            );
            events.failRequest("Could not start gathering '%s' because pathing is unavailable and the source is out of reach.".formatted(request.kitName));
            return;
        }

        Optional<BlockPos> standingPos = scannerStandingPos(target, settings);
        if (standingPos.isEmpty()) {
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
            return;
        }

        if (pathThrottle.beforePathStart() == StashScannerPathThrottle.PathStartDecision.THROTTLED) {
            session.clearCurrentTarget();
            session.beginGatherPhase(KitbotPhase.IDLE, SUPPRESSED_SOURCE_WAIT_TICKS);
            return;
        }

        session.beginGatherPhase(KitbotPhase.PATHING, settings.pathTimeoutTicks());
        pathProgress.reset(target, mc.player.getBlockPos(), mc.player.getEyePos());
        pathThrottle.recordPathStart();
        if (!navigator.pathToScannerTarget(target, settings.interactionRange(), standingPos)) {
            session.phase(KitbotPhase.IDLE);
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
            return;
        }
        notifyGatherStarted(request);
        events.info("Pathing to stash kit source at %s.", StashClientUtils.formatPos(target.interactionPos()));
    }

    private void tickPathing(Settings settings) {
        StashTarget target = session.currentTarget();
        if (target == null) {
            session.phase(KitbotPhase.IDLE);
            return;
        }

        if (!targetStillValid(target)) {
            navigator.stop();
            skipCurrentSource(StashSkipReasons.CHANGED_OR_MISSING);
            return;
        }

        if (isCloseEnough(target, settings)) {
            navigator.stop();
            if (interactWithCurrent(settings)) notifyGatherStarted(session.activeRequest());
            return;
        }

        if (pathProgress.tick(target, mc.player.getBlockPos(), mc.player.getEyePos(), navigator.hasPath()) == StashScannerPathProgress.FastFailDecision.FAST_FAIL) {
            navigator.stop();
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
            return;
        }

        if (session.tickGatherTimeoutExpired()) {
            navigator.stop();
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
            return;
        }

        Optional<BlockPos> standingPos = scannerStandingPos(target, settings);
        if (standingPos.isEmpty()) {
            navigator.stop();
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
            return;
        }

        if (!navigator.ensureScannerPathTo(target, settings.interactionRange(), standingPos)) {
            navigator.stop();
            pathThrottle.recordPathFailure(target, mc.player.getBlockPos());
            skipCurrentSource(StashSkipReasons.PATH_TIMEOUT);
        }
    }

    private boolean interactWithCurrent(Settings settings) {
        StashTarget target = session.currentTarget();
        if (target == null || !StashClientUtils.canUse(mc)) return false;

        interactor.interactWithTarget(target, () -> StashClientUtils.canUse(mc) && session.currentTarget() != null && session.currentTarget().id().equals(target.id()));
        session.beginGatherPhase(KitbotPhase.OPENING, settings.openTimeoutTicks());
        return true;
    }

    private void tickOpening(Settings settings) {
        StashTarget target = session.currentTarget();
        if (target == null) {
            session.phase(KitbotPhase.IDLE);
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        int openedSize = interactor.openedSize(handler);
        if (openedSize > 0) {
            if (!interactor.screenMatchesTarget(handler, target)) {
                StashClientUtils.closeContainerScreen(mc);
                skipCurrentSource(StashSkipReasons.UNEXPECTED_SCREEN);
                return;
            }

            session.phase(KitbotPhase.TAKING);
            return;
        }

        if (session.tickGatherTimeoutExpired()) {
            StashClientUtils.closeContainerScreen(mc);
            skipCurrentSource(StashSkipReasons.OPEN_TIMEOUT);
        }
    }

    private void tickTaking(Settings settings) {
        KitRequest request = session.activeRequest();
        refreshConfirmedGathered(request);
        if (request.gather.gathered >= request.count) {
            updateOpenSourceCache();
            completeRequest(settings);
            return;
        }

        if (inventory.emptyInventorySlots() <= 0) {
            events.failRequest("Inventory filled while gathering '%s'.".formatted(request.kitName));
            return;
        }

        StashTarget target = session.currentTarget();
        ScreenHandler handler = mc.player.currentScreenHandler;
        int openedSize = interactor.openedSize(handler);
        if (openedSize <= 0 || target == null || !interactor.screenMatchesTarget(handler, target)) {
            StashClientUtils.closeContainerScreen(mc);
            if (request.gather.gathered >= request.count) completeRequest(settings);
            else skipCurrentSource(StashSkipReasons.CLOSED_SCREEN);
            return;
        }

        int slot = inventory.findNextMatchingContainerSlot(handler, openedSize, request.currentSource(), request.kitAlias);
        if (slot < 0) {
            updateSourceCache(target, handler, openedSize);
            StashClientUtils.closeContainerScreen(mc);
            markSourceSkipped(request, request.gather.sourceIndex);
            session.clearCurrentTarget();
            session.phase(KitbotPhase.IDLE);
            return;
        }

        request.gather.pendingTransferInventoryCount = inventory.matchingInventoryCount(request.kitAlias);
        request.gather.pendingTransferSlot = slot;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        session.beginGatherPhase(KitbotPhase.VERIFYING_TRANSFER, TRANSFER_VERIFY_TICKS);
    }

    private void tickVerifyingTransfer(Settings settings) {
        KitRequest request = session.activeRequest();
        refreshConfirmedGathered(request);

        StashTarget target = session.currentTarget();
        ScreenHandler handler = mc.player.currentScreenHandler;
        int openedSize = interactor.openedSize(handler);
        boolean containerOpen = openedSize > 0 && target != null && interactor.screenMatchesTarget(handler, target);
        boolean verifyExpired = session.tickGatherTimeoutExpired();
        int currentInventoryCount = inventory.matchingInventoryCount(request.kitAlias);
        int transferredCount = Math.max(0, currentInventoryCount - request.gather.pendingTransferInventoryCount);

        StashKitbotGatherPlanner.TransferVerifyDecision decision = StashKitbotGatherPlanner.transferVerifyDecision(
            request.gather.gathered,
            request.count,
            request.gather.pendingTransferInventoryCount,
            currentInventoryCount,
            containerOpen,
            verifyExpired
        );

        switch (decision) {
            case COMPLETE_REQUEST -> {
                updateCacheAfterTransfer(target, handler, openedSize, transferredCount);
                completeRequest(settings);
            }
            case CONTINUE_TAKING -> {
                updateCacheAfterTransfer(target, handler, openedSize, transferredCount);
                clearPendingTransfer(request);
                session.phase(KitbotPhase.TAKING);
            }
            case CONTAINER_CLOSED -> {
                updateCacheAfterTransfer(target, handler, openedSize, transferredCount);
                StashClientUtils.closeContainerScreen(mc);
                skipCurrentSource(StashSkipReasons.CLOSED_SCREEN);
            }
            case WAIT -> {
            }
        }
    }

    private void skipCurrentSource(String reason) {
        StashTarget target = session.currentTarget();
        KitRequest request = session.activeRequest();
        events.warning("Skipped kit source at %s for %s/%s: %s.",
            target == null ? "unknown" : StashClientUtils.formatPos(target.interactionPos()),
            request == null ? "unknown" : request.requester,
            request == null ? "unknown" : request.kitName,
            reason
        );
        markSourceSkipped(request, request.gather.sourceIndex);
        clearPendingTransfer(request);
        session.clearCurrentTarget();
        session.phase(KitbotPhase.IDLE);
    }

    private void notifyGatherStarted(KitRequest request) {
        if (request == null || request.gather.gatherStartNotified) return;

        request.gather.gatherStartNotified = true;
        events.reply(request.requester, "Gathering '%s' x%d now.".formatted(request.kitName, request.count));
    }

    private void completeRequest(Settings settings) {
        KitRequest request = session.activeRequest();
        refreshConfirmedGathered(request);
        StashClientUtils.closeContainerScreen(mc);
        navigator.stop();
        session.clearCurrentTarget();
        pathProgress.clear();
        pathThrottle.reset();
        clearPendingTransfer(request);
        request.delivery.preTpaPos = mc.player.getBlockPos().toImmutable();
        request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
        request.delivery.throwDelay.reset(0);
        events.info("Gathered stash kit request for %s: %s x%d confirmed in inventory. Starting delivery.", request.requester, request.kitName, request.gather.gathered);
        session.phase(KitbotPhase.TPA_REQUEST);
    }

    private void refreshConfirmedGathered(KitRequest request) {
        request.gather.gathered = StashKitbotGatherPlanner.confirmedGathered(
            request.count,
            request.gather.initialInventoryCount,
            inventory.matchingInventoryCount(request.kitAlias)
        );
    }

    private boolean targetStillValid(StashTarget target) {
        StashTarget refreshed = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), target.interactionPos());
        return refreshed != null && refreshed.id().equals(target.id()) && refreshed.expectedSize() == target.expectedSize();
    }

    private boolean isCloseEnough(StashTarget target, Settings settings) {
        return StashClientUtils.isCloseEnough(mc, target, settings.interactionRange() + 0.5);
    }

    private boolean sourceSuppressed(KitSource source, BlockPos playerPos) {
        StashTarget target = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), source.interactionPos());
        return target != null && pathThrottle.suppresses(target, playerPos);
    }

    private Optional<BlockPos> scannerStandingPos(StashTarget target, Settings settings) {
        return StashScannerPathPlanner.bestStandingPos(mc, target, settings.interactionRange());
    }

    private void markSourceSkipped(KitRequest request, int sourceIndex) {
        if (request == null || sourceIndex < 0) return;

        request.gather.skippedSourceIndexes.add(sourceIndex);
    }

    private void clearPendingTransfer(KitRequest request) {
        if (request == null) return;

        request.gather.pendingTransferInventoryCount = 0;
        request.gather.pendingTransferSlot = -1;
    }

    private void updateOpenSourceCache() {
        StashTarget target = session.currentTarget();
        if (target == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        int openedSize = interactor.openedSize(handler);
        if (openedSize <= 0 || !interactor.screenMatchesTarget(handler, target)) return;

        updateSourceCache(target, handler, openedSize);
    }

    private void updateCacheAfterTransfer(StashTarget target, ScreenHandler handler, int openedSize, int transferredCount) {
        KitRequest request = session.activeRequest();
        if (transferredCount <= 0 || request == null || request.gather.pendingTransferSlot < 0) return;

        if (openedSize > 0 && target != null && interactor.screenMatchesTarget(handler, target)) {
            updateSourceCache(target, handler, openedSize);
            return;
        }

        KitSource source = request.currentSource();
        if (source == null) return;

        try {
            cache.depleteSourceSlot(mc, source.containerId(), request.gather.pendingTransferSlot, request.kitAlias, transferredCount);
        }
        catch (IOException exception) {
            events.warning("Failed to deplete stash cache for %s: %s.", source.containerId(), exception.getMessage());
        }
    }

    private void updateSourceCache(StashTarget target, ScreenHandler handler, int openedSize) {
        try {
            cache.updateContainer(mc, reader.read(target, handler, openedSize));
        }
        catch (IOException exception) {
            events.warning("Failed to update stash cache for %s: %s.", StashClientUtils.formatPos(target.interactionPos()), exception.getMessage());
        }
    }

    record Settings(
        int interactionRange,
        int pathTimeoutTicks,
        int openTimeoutTicks,
        int deliveryTimeoutTicks
    ) {
    }
}
