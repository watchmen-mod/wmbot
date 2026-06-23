package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

final class StashScannerWorkflow {
    private static final int ROLLING_NEARBY_TARGET_LIMIT = 12;
    private static final int PATH_THROTTLE_IDLE_TICKS = 10;

    private final MinecraftClient mc;
    private final StashScanSession session;
    private final StashTargetDiscovery discovery;
    private final StashNavigator navigator;
    private final StashSafetyGuard safetyGuard;
    private final StashContainerReader reader;
    private final StashContainerInteractor interactor;
    private final StashScannerEvents events;

    StashScannerWorkflow(
        MinecraftClient mc,
        StashScanSession session,
        StashTargetDiscovery discovery,
        StashNavigator navigator,
        StashSafetyGuard safetyGuard,
        StashContainerReader reader,
        StashContainerInteractor interactor,
        StashScannerEvents events
    ) {
        this.mc = mc;
        this.session = session;
        this.discovery = discovery;
        this.navigator = navigator;
        this.safetyGuard = safetyGuard;
        this.reader = reader;
        this.interactor = interactor;
        this.events = events;
    }

    void tick(Settings settings) {
        switch (session.phase()) {
            case IDLE -> startNextTarget(settings);
            case PATHING -> tickPathing(settings);
            case OPENING -> tickOpening(settings);
            case RETURNING -> tickReturnToStart(settings);
        }
    }

    int discoverAndQueueTargets(int scanRadius, boolean baritonePathing, double interactionRange, boolean skipShulkers) {
        if (!StashClientUtils.canUse(mc)) return 0;

        StashDiscoveryResult result = discovery.discover(
            mc,
            StashClientUtils.dimensionId(mc),
            scanRadius,
            baritonePathing,
            interactionRange,
            skipShulkers
        );

        session.addSkipped(result.skipped());
        int discovered = session.queueTargets(result.targets(), mc.player.getEyePos());
        if (discovered > 0) events.info("Queued %d stash container%s.", discovered, discovered == 1 ? "" : "s");

        return discovered;
    }

    private void startNextTarget(Settings settings) {
        StashTarget target = session.pollNearestTarget(mc.player.getEyePos(), mc.player.getBlockPos());
        if (target == null) {
            if (session.hasPathSuppressedTargets(mc.player.getBlockPos())) {
                session.discoveryDelay(PATH_THROTTLE_IDLE_TICKS);
                return;
            }
            finishScan(settings);
            return;
        }

        if (session.isCompleted(target.id())) {
            session.clearCurrent();
            return;
        }

        StashTarget refreshed = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), target.interactionPos());
        if (refreshed != null && refreshed.id().equals(target.id())) target = refreshed;

        session.current(target);
        if (isCloseEnough(target, settings)) {
            events.info("Opening %s at %s.", target.type(), StashClientUtils.formatPos(target.interactionPos()));
            interactWithCurrent(settings);
            return;
        }

        if (!settings.baritonePathing()) {
            session.clearCurrent();
            return;
        }

        Optional<BlockPos> standingPos = scannerStandingPos(target, settings);
        if (standingPos.isEmpty()) {
            session.recordPathTimeout(target, mc.player.getBlockPos());
            finishSkipped(target, StashSkipReasons.PATH_TIMEOUT, settings);
            return;
        }

        if (session.beforePathStart() == StashScannerPathThrottle.PathStartDecision.THROTTLED) {
            session.requeueTarget(target);
            session.clearCurrent();
            session.discoveryDelay(PATH_THROTTLE_IDLE_TICKS);
            return;
        }

        session.beginPhase(StashScanPhase.PATHING, settings.pathTimeoutTicks());
        session.beginPathProgress(target, mc.player.getBlockPos(), mc.player.getEyePos());
        session.recordPathStart();
        navigator.pathToScannerTarget(target, settings.interactionRange(), standingPos);
        events.info("Pathing safely near %s.", StashClientUtils.formatPos(target.interactionPos()));
    }

    private void tickPathing(Settings settings) {
        StashTarget target = session.current();
        if (target == null) {
            session.phase(StashScanPhase.IDLE);
            return;
        }

        safetyGuard.apply();
        safetyGuard.cancelBreaking(mc);

        if (!targetStillValid(target)) {
            navigator.stop();
            finishSkipped(target, StashSkipReasons.CHANGED_OR_MISSING, settings);
            return;
        }

        if (isCloseEnough(target, settings)) {
            navigator.stop();
            interactWithCurrent(settings);
            return;
        }

        if (tryOpenNearbyQueuedTarget(settings)) return;

        if (session.tickPathProgressFailed(target, mc.player.getBlockPos(), mc.player.getEyePos(), navigator.hasPath())) {
            navigator.stop();
            session.recordPathTimeout(target, mc.player.getBlockPos());
            finishSkipped(target, StashSkipReasons.PATH_TIMEOUT, settings);
            return;
        }

        if (session.tickPhaseTimeout()) {
            navigator.stop();
            session.recordPathTimeout(target, mc.player.getBlockPos());
            finishSkipped(target, StashSkipReasons.PATH_TIMEOUT, settings);
            return;
        }

        Optional<BlockPos> standingPos = scannerStandingPos(target, settings);
        if (standingPos.isEmpty()) {
            navigator.stop();
            session.recordPathTimeout(target, mc.player.getBlockPos());
            finishSkipped(target, StashSkipReasons.PATH_TIMEOUT, settings);
            return;
        }

        navigator.ensureScannerPathTo(target, settings.interactionRange(), standingPos);
    }

    private void interactWithCurrent(Settings settings) {
        StashTarget target = session.current();
        if (target == null || !StashClientUtils.canUse(mc)) return;

        session.beginPhase(StashScanPhase.OPENING, settings.openTimeoutTicks());
        session.beginOpenAttempts(target);
        sendOpenAttempt(target);
    }

    private void tickOpening(Settings settings) {
        StashTarget target = session.current();
        if (target == null) {
            session.phase(StashScanPhase.IDLE);
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        int openedSize = interactor.openedSize(handler);
        if (openedSize > 0) {
            if (!interactor.screenMatchesTarget(handler, target)) {
                StashClientUtils.closeContainerScreen(mc);
                finishSkipped(target, StashSkipReasons.UNEXPECTED_SCREEN, settings);
                return;
            }

            session.markScanned(reader.read(target, handler, openedSize));
            StashClientUtils.closeContainerScreen(mc);
            events.info("Scanned %s with %d slots at %s.", target.type(), openedSize, StashClientUtils.formatPos(target.interactionPos()));
            session.clearCurrent();
            if (settings.writeCacheEachContainer()) events.writeCache(null);
            return;
        }

        StashScannerOpenAttempts.Decision decision = session.tickOpenAttemptDecision();
        if (decision == StashScannerOpenAttempts.Decision.RETRY) {
            if (!targetStillValid(target)) {
                finishSkipped(target, StashSkipReasons.CHANGED_OR_MISSING, settings);
                return;
            }
            if (!isCloseEnough(target, settings)) {
                finishSkipped(target, StashSkipReasons.OPEN_TIMEOUT, settings);
                return;
            }

            sendOpenAttempt(target);
            return;
        }

        if (decision == StashScannerOpenAttempts.Decision.FAIL) {
            StashClientUtils.closeContainerScreen(mc);
            finishSkipped(target, StashSkipReasons.OPEN_TIMEOUT, settings);
            return;
        }

        if (session.tickPhaseTimeout()) {
            StashClientUtils.closeContainerScreen(mc);
            finishSkipped(target, StashSkipReasons.OPEN_TIMEOUT, settings);
        }
    }

    private void finishScan(Settings settings) {
        if (beginReturnToStart(settings)) return;

        events.stopModule();
    }

    private boolean beginReturnToStart(Settings settings) {
        BlockPos startPos = session.startPos();
        if (!settings.returnToStart() || session.returnStarted() || startPos == null || !settings.baritonePathing()) return false;
        if (mc.player.getBlockPos().getSquaredDistance(startPos) <= 4) return false;

        session.beginReturn(settings.returnTimeoutTicks());
        navigator.returnTo(startPos);
        events.info("Returning to scan start at %s.", StashClientUtils.formatPos(startPos));
        return true;
    }

    private void tickReturnToStart(Settings settings) {
        safetyGuard.apply();
        safetyGuard.cancelBreaking(mc);

        BlockPos startPos = session.startPos();
        if (startPos == null || mc.player.getBlockPos().getSquaredDistance(startPos) <= 4) {
            navigator.stop();
            events.info("Returned to scan start.");
            events.stopModule();
            return;
        }

        if (session.tickPhaseTimeout()) {
            navigator.stop();
            events.warning("Timed out returning to scan start.");
            events.stopModule();
            return;
        }

        navigator.ensureReturnTo(startPos);
    }

    private void finishSkipped(StashTarget target, String reason, Settings settings) {
        boolean recorded = session.markSkipped(target, reason);
        if (!recorded) return;

        events.warning("Skipped %s at %s: %s.", target.type(), StashClientUtils.formatPos(target.interactionPos()), reason);
        if (settings.writeCacheEachContainer()) events.writeCache(null);
    }

    private boolean targetStillValid(StashTarget target) {
        StashTarget refreshed = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), target.interactionPos());
        return refreshed != null && refreshed.id().equals(target.id()) && refreshed.expectedSize() == target.expectedSize();
    }

    private boolean isCloseEnough(StashTarget target, Settings settings) {
        return StashClientUtils.isCloseEnough(mc, target, settings.interactionRangeValue());
    }

    private void sendOpenAttempt(StashTarget target) {
        session.markOpenAttemptSent(target);
        interactor.interactWithTarget(
            target,
            () -> StashClientUtils.canUse(mc) && session.current() != null && session.current().id().equals(target.id()),
            session::recordOpenInteraction
        );
    }

    private boolean tryOpenNearbyQueuedTarget(Settings settings) {
        Optional<StashTarget> nearby = session.pollReachableQueuedTarget(
            mc.player.getEyePos(),
            settings.interactionRangeValue(),
            ROLLING_NEARBY_TARGET_LIMIT
        );
        if (nearby.isEmpty()) return false;

        StashTarget target = nearby.get();
        StashTarget refreshed = discovery.createTarget(mc, StashClientUtils.dimensionId(mc), target.interactionPos());
        navigator.stop();
        session.requeueCurrentTarget();
        if (refreshed == null || !refreshed.id().equals(target.id()) || refreshed.expectedSize() != target.expectedSize()) {
            session.current(target);
            finishSkipped(target, StashSkipReasons.CHANGED_OR_MISSING, settings);
            return true;
        }

        session.current(refreshed);
        events.info("Opening nearby %s at %s.", refreshed.type(), StashClientUtils.formatPos(refreshed.interactionPos()));
        interactWithCurrent(settings);
        return true;
    }

    private Optional<BlockPos> scannerStandingPos(StashTarget target, Settings settings) {
        return StashScannerPathPlanner.bestStandingPos(mc, target, settings.interactionRange());
    }

    record Settings(
        int interactionRange,
        int openTimeoutTicks,
        boolean baritonePathing,
        int pathTimeoutTicks,
        boolean returnToStart,
        int returnTimeoutTicks,
        boolean writeCacheEachContainer
    ) {
        double interactionRangeValue() {
            return interactionRange + 0.5;
        }
    }
}
