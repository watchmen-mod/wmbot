package com.watchmenbot.modules.stash;

import com.watchmenbot.util.TickTimer;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class KitRequest {
    final String requester;
    final String kitName;
    final String kitAlias;
    final int count;
    final List<KitSource> sources;
    final BlockPos requestOrigin;
    final GatherState gather = new GatherState();
    final DeliveryState delivery = new DeliveryState();
    final CooldownState cooldown = new CooldownState();

    KitRequest(String requester, String kitName, String kitAlias, int count, List<KitSource> sources, BlockPos requestOrigin) {
        this.requester = requester;
        this.kitName = kitName;
        this.kitAlias = kitAlias;
        this.count = count;
        this.sources = sources;
        this.requestOrigin = requestOrigin;
    }

    KitSource currentSource() {
        if (gather.sourceIndex < 0 || gather.sourceIndex >= sources.size()) return null;
        return sources.get(gather.sourceIndex);
    }

    KitSource nextSource() {
        return currentSource();
    }
}

final class GatherState {
    int sourceIndex;
    int gathered;
    int initialInventoryCount;
    int pendingTransferInventoryCount;
    int pendingTransferSlot = -1;
    boolean gatherStartNotified;
    final Set<Integer> skippedSourceIndexes = new HashSet<>();
}

final class DeliveryState {
    int delivered;
    boolean notifiedDelivered;
    final TickTimer teleportWait = new TickTimer();
    final TickTimer requesterReacquire = new TickTimer();
    final TickTimer deliveryTimeout = new TickTimer();
    final TickTimer throwDelay = new TickTimer();
    final DeliveryCommandState commands = new DeliveryCommandState();
    final PendingCommandCooldown pendingCommandCooldown = new PendingCommandCooldown();
    int directStepTicks;
    int positioningTicks;
    int deliveryTraceTicks;
    int tpaAttemptCount;
    int tpaRetryTicks;
    int stuckMovementTicks;
    int stuckRecoveryAttempts;
    boolean crossDimensionDelivery;
    boolean homeRespawnRequired;
    boolean homeRespawnRequested;
    int crossDimensionSettleTicks;
    BlockPos preTpaPos;
    String preTpaDimension;
    BlockPos deliverySpot;
    BlockPos lastMovementWatchdogPos;
    BlockPos lastMovementWatchdogSpot;
    double lastMovementWatchdogX = Double.NaN;
    double lastMovementWatchdogZ = Double.NaN;
    double lastMovementWatchdogRequesterDistanceSq = Double.MAX_VALUE;
}

final class PendingCommandCooldown {
    PendingCommand command = PendingCommand.NONE;
    int attemptId;
    int cooldownTicks;
    int graceTicks;
    String message;

    boolean active() {
        return command != PendingCommand.NONE;
    }

    void start(PendingCommand command, int attemptId, int cooldownTicks, int graceTicks, String message) {
        this.command = command;
        this.attemptId = attemptId;
        this.cooldownTicks = cooldownTicks;
        this.graceTicks = graceTicks;
        this.message = message;
    }

    void clear() {
        command = PendingCommand.NONE;
        attemptId = 0;
        cooldownTicks = 0;
        graceTicks = 0;
        message = null;
    }
}

final class DeliveryCommandState {
    private int nextAttemptId = 1;
    PendingCommand pendingCommand = PendingCommand.NONE;
    int pendingAttemptId;
    String pendingTarget;
    KitbotPhase expectedCooldownPhase;
    boolean tpaSent;
    boolean homeSent;

    int beginPending(PendingCommand command, String target, KitbotPhase expectedPhase) {
        int attemptId = nextAttemptId++;
        pendingCommand = command;
        pendingAttemptId = attemptId;
        pendingTarget = target;
        expectedCooldownPhase = expectedPhase;
        return attemptId;
    }

    void markSent(PendingCommand command) {
        if (command == PendingCommand.TPA) tpaSent = true;
        if (command == PendingCommand.HOME) homeSent = true;
    }

    boolean hasPending() {
        return pendingCommand != PendingCommand.NONE;
    }

    void clearPending() {
        pendingCommand = PendingCommand.NONE;
        pendingAttemptId = 0;
        pendingTarget = null;
        expectedCooldownPhase = null;
    }

    void markCooldown(PendingCommand command) {
        if (command == PendingCommand.TPA) tpaSent = false;
        if (command == PendingCommand.HOME) homeSent = false;
        clearPending();
    }
}

final class CooldownState {
    final TickTimer tpaCooldown = new TickTimer();
    final TickTimer homeCooldown = new TickTimer();
    final TickTimer homeConfirm = new TickTimer();
}
