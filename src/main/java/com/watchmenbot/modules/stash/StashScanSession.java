package com.watchmenbot.modules.stash;

import com.watchmenbot.util.TickTimer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class StashScanSession {
    private final Map<String, StashTarget> queued = new LinkedHashMap<>();
    private final Set<String> completed = new HashSet<>();
    private final Set<String> skippedIds = new HashSet<>();
    private final List<StashCachedContainer> containers = new ArrayList<>();
    private final List<StashSkippedContainer> skipped = new ArrayList<>();
    private final StashScannerTargetPlanner targetPlanner = new StashScannerTargetPlanner();
    private final StashScannerPathProgress pathProgress = new StashScannerPathProgress();
    private final StashScannerOpenAttempts openAttempts = new StashScannerOpenAttempts();
    private final StashScannerPathThrottle pathThrottle = new StashScannerPathThrottle();

    private StashTarget current;
    private StashScanPhase phase = StashScanPhase.IDLE;
    private Instant scanStartedAt;
    private BlockPos startPos;
    private int discoveryDelay;
    private final TickTimer phaseTimer = new TickTimer();
    private boolean noTargetsWarningShown;
    private boolean safeModeWarningShown;
    private boolean returnStarted;
    private long contentVersion;

    void reset(BlockPos startPos) {
        queued.clear();
        completed.clear();
        skippedIds.clear();
        containers.clear();
        skipped.clear();
        current = null;
        phase = StashScanPhase.IDLE;
        scanStartedAt = Instant.now();
        this.startPos = startPos == null ? null : startPos.toImmutable();
        discoveryDelay = 0;
        phaseTimer.reset(0);
        noTargetsWarningShown = false;
        safeModeWarningShown = false;
        returnStarted = false;
        targetPlanner.reset();
        pathProgress.clear();
        openAttempts.clear();
        pathThrottle.reset();
        bumpContentVersion();
    }

    void loadCached(CacheFile cacheFile) {
        if (cacheFile == null) return;

        containers.clear();
        skipped.clear();
        skippedIds.clear();

        if (cacheFile.containers() != null) {
            Map<String, StashCachedContainer> uniqueContainers = new LinkedHashMap<>();
            for (StashCachedContainer container : cacheFile.containers()) {
                if (container == null || container.id() == null) continue;
                uniqueContainers.put(container.id(), container);
            }

            containers.addAll(uniqueContainers.values());
        }

        if (cacheFile.skipped() != null) {
            for (StashSkippedContainer skippedContainer : cacheFile.skipped()) {
                if (skippedContainer == null || skippedContainer.id() == null || skippedIds.contains(skippedContainer.id())) continue;

                skippedIds.add(skippedContainer.id());
                completed.add(skippedContainer.id());
                skipped.add(skippedContainer);
            }
        }

        bumpContentVersion();
    }

    int queueTargets(List<StashTarget> targets, Vec3d playerEyes) {
        targets.sort((a, b) -> Double.compare(a.distanceSq(playerEyes), b.distanceSq(playerEyes)));

        int discovered = 0;
        for (StashTarget target : targets) {
            if (completed.contains(target.id()) || skippedIds.contains(target.id()) || queued.containsKey(target.id()) || isCurrent(target.id())) continue;

            queued.put(target.id(), target);
            discovered++;
        }

        return discovered;
    }

    void addSkipped(List<StashSkippedContainer> skippedContainers) {
        boolean changed = false;
        for (StashSkippedContainer skippedContainer : skippedContainers) {
            if (skippedContainer == null || skippedContainer.id() == null) continue;
            if (skippedIds.contains(skippedContainer.id()) || completed.contains(skippedContainer.id())) continue;

            containers.removeIf(existing -> existing.id().equals(skippedContainer.id()));
            skippedIds.add(skippedContainer.id());
            completed.add(skippedContainer.id());
            queued.remove(skippedContainer.id());
            skipped.add(skippedContainer);
            changed = true;
        }
        if (changed) bumpContentVersion();
    }

    StashTarget pollNearestTarget(Vec3d playerEyes, BlockPos playerPos) {
        StashTarget best = targetPlanner.chooseNext(queued.values(), playerEyes, playerPos, pathThrottle::suppresses).orElse(null);

        if (best != null) queued.remove(best.id());
        current = best;
        return best;
    }

    boolean hasPathSuppressedTargets(BlockPos playerPos) {
        return queued.values().stream().anyMatch(target -> pathThrottle.suppresses(target, playerPos));
    }

    void requeueTarget(StashTarget target) {
        if (target == null) return;
        if (!completed.contains(target.id()) && !skippedIds.contains(target.id()) && !queued.containsKey(target.id()) && !isCurrent(target.id())) {
            queued.put(target.id(), target);
        }
    }

    Optional<StashTarget> pollReachableQueuedTarget(Vec3d playerEyes, double interactionRange, int rollingLimit) {
        if (queued.isEmpty()) return Optional.empty();

        double rangeSq = interactionRange * interactionRange;
        StashTarget target = queued.values().stream()
            .sorted((a, b) -> Double.compare(a.distanceSq(playerEyes), b.distanceSq(playerEyes)))
            .limit(Math.max(1, rollingLimit))
            .filter(candidate -> candidate.distanceSq(playerEyes) <= rangeSq)
            .findFirst()
            .orElse(null);

        if (target == null) return Optional.empty();

        queued.remove(target.id());
        return Optional.of(target);
    }

    void requeueCurrentTarget() {
        if (current == null) return;
        if (!completed.contains(current.id()) && !skippedIds.contains(current.id()) && !queued.containsKey(current.id())) {
            queued.put(current.id(), current);
        }
        current = null;
        pathProgress.clear();
        openAttempts.clear();
    }

    void recordPathTimeout(StashTarget target, BlockPos playerPos) {
        targetPlanner.recordPathTimeout(target, playerPos);
        pathThrottle.recordPathFailure(target, playerPos);
    }

    StashScannerPathThrottle.PathStartDecision beforePathStart() {
        return pathThrottle.beforePathStart();
    }

    void recordPathStart() {
        pathThrottle.recordPathStart();
    }

    void beginPathProgress(StashTarget target, BlockPos playerPos, Vec3d playerEyes) {
        pathProgress.reset(target, playerPos, playerEyes);
    }

    boolean tickPathProgressFailed(StashTarget target, BlockPos playerPos, Vec3d playerEyes, boolean baritoneHasPath) {
        return pathProgress.tick(target, playerPos, playerEyes, baritoneHasPath) == StashScannerPathProgress.FastFailDecision.FAST_FAIL;
    }

    void beginOpenAttempts(StashTarget target) {
        openAttempts.start(target);
    }

    void markOpenAttemptSent(StashTarget target) {
        openAttempts.markAttemptSent(target);
    }

    void recordOpenInteraction(StashScannerOpenAttempts.InteractionResult result) {
        openAttempts.recordInteraction(result);
    }

    StashScannerOpenAttempts.Decision tickOpenAttemptDecision() {
        return openAttempts.tick();
    }

    void markScanned(StashCachedContainer container) {
        containers.removeIf(existing -> existing.id().equals(container.id()));
        skipped.removeIf(existing -> existing.id().equals(container.id()));
        skippedIds.remove(container.id());
        containers.add(container);
        completed.add(container.id());
        bumpContentVersion();
    }

    boolean markSkipped(StashTarget target, String reason) {
        if (target == null) return false;

        queued.remove(target.id());
        boolean alreadyKnown = skippedIds.contains(target.id()) || completed.contains(target.id());
        if (isCurrent(target.id())) {
            current = null;
            phase = StashScanPhase.IDLE;
            pathProgress.clear();
            openAttempts.clear();
        }

        if (alreadyKnown) return false;

        StashSkippedContainer skippedContainer = StashInventoryCache.skipped(target, reason);
        containers.removeIf(existing -> existing.id().equals(target.id()));
        skippedIds.add(target.id());
        completed.add(target.id());
        queued.remove(target.id());
        skipped.add(skippedContainer);
        current = null;
        phase = StashScanPhase.IDLE;
        pathProgress.clear();
        openAttempts.clear();
        bumpContentVersion();
        return true;
    }

    boolean tickDiscoveryDelay() {
        if (discoveryDelay > 0) {
            discoveryDelay--;
            return false;
        }

        return true;
    }

    boolean tickPhaseTimeout() {
        return phaseTimer.tickOrElapsedExpired();
    }

    void beginPhase(StashScanPhase phase, int timeoutTicks) {
        this.phase = phase;
        phaseTimer.reset(timeoutTicks);
    }

    void beginReturn(int timeoutTicks) {
        returnStarted = true;
        current = null;
        beginPhase(StashScanPhase.RETURNING, timeoutTicks);
    }

    boolean markSafeModeWarningShown() {
        if (safeModeWarningShown) return false;

        safeModeWarningShown = true;
        return true;
    }

    boolean shouldWarnNoTargets() {
        if (noTargetsWarningShown || current != null || !queued.isEmpty()) return false;

        noTargetsWarningShown = true;
        return true;
    }

    void markNoTargetsWarningShown() {
        noTargetsWarningShown = true;
    }

    void clearNoTargetsWarning() {
        noTargetsWarningShown = false;
    }

    boolean isCurrent(String id) {
        return current != null && current.id().equals(id);
    }

    boolean isCompleted(String id) {
        return completed.contains(id);
    }

    void clearCurrent() {
        current = null;
        phase = StashScanPhase.IDLE;
        pathProgress.clear();
        openAttempts.clear();
    }

    int scannedCount() {
        return containers.size();
    }

    int queuedCount() {
        return queued.size();
    }

    int skippedCount() {
        return skipped.size();
    }

    int seenCount() {
        return containers.size() + skipped.size() + queued.size() + (current == null ? 0 : 1);
    }

    StashTarget current() {
        return current;
    }

    void current(StashTarget current) {
        this.current = current;
    }

    StashScanPhase phase() {
        return phase;
    }

    void phase(StashScanPhase phase) {
        this.phase = phase;
    }

    Instant scanStartedAt() {
        return scanStartedAt;
    }

    BlockPos startPos() {
        return startPos;
    }

    boolean returnStarted() {
        return returnStarted;
    }

    void discoveryDelay(int discoveryDelay) {
        this.discoveryDelay = discoveryDelay;
    }

    List<StashCachedContainer> containers() {
        return containers;
    }

    List<StashSkippedContainer> skipped() {
        return skipped;
    }

    long contentVersion() {
        return contentVersion;
    }

    private void bumpContentVersion() {
        contentVersion++;
    }
}
