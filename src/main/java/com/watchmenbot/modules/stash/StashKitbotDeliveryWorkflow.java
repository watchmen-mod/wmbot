package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

final class StashKitbotDeliveryWorkflow {
    private static final int HOME_CONFIRM_TICKS = 80;
    private static final int PREPARED_THROW_TICKS = 2;
    private static final int DIRECT_STEP_FALLBACK_TICKS = 40;
    private static final int CROSS_DIMENSION_POSITIONING_FALLBACK_TICKS = 60;
    private static final int CROSS_DIMENSION_TRACE_INTERVAL_TICKS = 40;
    private static final int CROSS_DIMENSION_SETTLE_TICKS = 40;
    private static final int TPA_COOLDOWN_GRACE_TICKS = 20;

    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashNavigator navigator;
    private final StashKitbotMessenger messenger;
    private final StashKitbotDelivery delivery;
    private final StashKitbotInventory inventory;
    private final StashKitbotEvents events;

    StashKitbotDeliveryWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashNavigator navigator,
        StashKitbotMessenger messenger,
        StashKitbotDelivery delivery,
        StashKitbotInventory inventory,
        StashKitbotEvents events
    ) {
        this.mc = mc;
        this.session = session;
        this.navigator = navigator;
        this.messenger = messenger;
        this.delivery = delivery;
        this.inventory = inventory;
        this.events = events;
    }

    void tick(Settings settings) {
        expirePendingCommandWindow();
        tickPendingCommandCooldown(settings);
        switch (session.phase()) {
            case TPA_REQUEST -> tickTpaRequest(settings);
            case WAITING_FOR_TPY -> tickWaitingForTpy(settings);
            case REACQUIRE_REQUESTER -> tickReacquireRequester(settings);
            case MOVE_TO_DELIVERY_SPOT -> tickMoveToDeliverySpot(settings);
            case THROWING -> tickThrowing(settings);
            case PREPARED_THROW -> tickPreparedThrow(settings);
            case HOME_REQUEST -> tickHomeRequest(settings);
            case HOME_COOLDOWN -> tickHomeCooldown(settings);
            case HOME_CONFIRM -> tickHomeConfirm();
            case RETURN_TO_ORIGIN -> tickReturnToOrigin(settings);
            default -> {
            }
        }
    }

    void handleCooldownMessage(String message, Settings settings) {
        if (StashKitbotDeliveryPlanner.ignoreForCooldownHandling(message)) return;

        KitRequest request = session.activeRequest();
        if (request != null && StashKitbotDeliveryPlanner.tpaRequestAccepted(message, request.requester)) {
            handleTpaAccepted(request);
            return;
        }

        if (!delivery.looksLikeCooldown(message)) return;

        boolean commandPendingWindow = request != null
            && request.delivery.commands.hasPending()
            && messenger.hasPendingCommandWindow();
        StashKitbotDeliveryPlanner.CooldownAttribution attribution = StashKitbotDeliveryPlanner.cooldownAttribution(
            commandPendingWindow,
            messenger.hasPendingWhisperWindow(),
            messenger.pendingCommandSentAfterWhisper()
        );

        if (attribution == StashKitbotDeliveryPlanner.CooldownAttribution.COMMAND) {
            if (request.delivery.commands.pendingCommand == PendingCommand.TPA && tryStartDeliveryAfterTeleport(settings)) return;
            if (handlePendingCommandCooldown(request, message, settings)) return;
            ignoreStaleCommandCooldown(request, message);
            return;
        }

        if (attribution == StashKitbotDeliveryPlanner.CooldownAttribution.WHISPER) {
            int cooldownTicks = cooldownTicks(message, settings);

            PendingWhisper pendingWhisper = messenger.pendingWhisper();
            messenger.applyWhisperCooldown(cooldownTicks);
            events.warning("Whisper command is cooling down for about %s. Queued whisper to %s.", delivery.formatDuration(cooldownTicks), pendingWhisper.player());
        }
    }

    private int cooldownTicks(String message, Settings settings) {
        int cooldownTicks = delivery.parseCooldownTicks(message);
        return cooldownTicks <= 0 ? settings.fallbackCooldownTicks() : cooldownTicks;
    }

    private void tickPendingCommandCooldown(Settings settings) {
        KitRequest request = session.activeRequest();
        if (request == null || !request.delivery.pendingCommandCooldown.active()) return;

        if (request.delivery.pendingCommandCooldown.command == PendingCommand.TPA && tryStartDeliveryAfterTeleport(settings)) {
            request.delivery.pendingCommandCooldown.clear();
            return;
        }

        StashKitbotDeliveryPlanner.PendingCooldownDecision decision = StashKitbotDeliveryPlanner.pendingCooldownDecision(
            false,
            false,
            --request.delivery.pendingCommandCooldown.graceTicks
        );
        if (decision == StashKitbotDeliveryPlanner.PendingCooldownDecision.WAIT) return;
        if (decision == StashKitbotDeliveryPlanner.PendingCooldownDecision.DISCARD) {
            request.delivery.pendingCommandCooldown.clear();
            return;
        }

        applyPendingCommandCooldown(request, settings);
    }

    private void tickTpaRequest(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        if (tryStartDeliveryAfterTeleport(settings)) return;

        boolean cooldownActive = request.cooldown.tpaCooldown.tickDelay();
        switch (StashKitbotDeliveryPlanner.tpaRequestDecision(false, cooldownActive, request.delivery.commands.tpaSent)) {
            case START_DELIVERY -> {
                return;
            }
            case WAIT -> {
                if (request.delivery.commands.tpaSent) session.phase(KitbotPhase.WAITING_FOR_TPY);
                return;
            }
            case SEND_TPA -> {
            }
        }

        request.delivery.preTpaPos = mc.player.getBlockPos().toImmutable();
        request.delivery.preTpaDimension = StashClientUtils.dimensionId(mc);
        request.delivery.crossDimensionDelivery = false;
        request.delivery.deliveryTraceTicks = 0;
        int attemptId = request.delivery.commands.beginPending(PendingCommand.TPA, request.requester, KitbotPhase.WAITING_FOR_TPY);
        if (!messenger.sendCommand(settings.tpaCommand(), request.requester, PendingCommand.TPA)) {
            request.delivery.commands.clearPending();
            request.delivery.commands.tpaSent = false;
            postTeleportFailure("Could not send TPA command because the configured command is blank. Heading home with the kits.");
            return;
        }

        request.delivery.commands.markSent(PendingCommand.TPA);
        events.info("Sent TPA command attempt %d to %s.", attemptId, request.requester);
        request.delivery.teleportWait.reset(settings.teleportTimeoutTicks());
        session.phase(KitbotPhase.WAITING_FOR_TPY);
    }

    private void tickWaitingForTpy(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        logCrossDimensionWait(request, settings, "waiting_for_tpy");
        if (tryStartDeliveryAfterTeleport(settings)) return;

        if (StashKitbotDeliveryPlanner.tpaTimeoutShouldReturn(false, request.delivery.teleportWait.tickExpired())) {
            clearPendingCommandIf(PendingCommand.TPA);
            events.reply(request.requester, "Delivery timed out waiting for /tpy. Returning to stash position.");
            beginReturnToOrigin(settings);
        }
    }

    private void tickReacquireRequester(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);

        correctCrossDimensionFlagIfNeeded(request, "reacquire", true);

        if (request.delivery.crossDimensionSettleTicks > 0) {
            request.delivery.crossDimensionSettleTicks--;
            logCrossDimensionWait(request, settings, "settling");
            return;
        }

        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (startMovingToDeliverySpot(request, requester, settings)) return;

        delivery.stopWalking();
        navigator.stop();
        logCrossDimensionWait(request, settings, "reacquire_requester");
        if (StashKitbotDeliveryPlanner.shouldKeepReacquiringRequester(requester != null, request.delivery.requesterReacquire.tickExpired())) {
            return;
        }

        postTeleportFailure("I teleported, but I could not see you nearby after waiting for the server transfer to finish. Heading home with the kits.");
    }

    private void tickMoveToDeliverySpot(Settings settings) {
        KitRequest request = session.activeRequest();
        correctCrossDimensionFlagIfNeeded(request, "move", false);

        boolean movementExpired = request.delivery.deliveryTimeout.tickExpired();
        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (requester == null) {
            if (!movementExpired) {
                beginRequesterReacquire(settings);
                events.info("Requester %s disappeared during delivery positioning; reacquiring.", request.requester);
                return;
            }

            postTeleportFailure("I teleported, but I cannot see you nearby. Heading home with the kits.");
            return;
        }

        if (request.delivery.crossDimensionDelivery) {
            tickCrossDimensionMoveToRequester(request, requester, settings, movementExpired);
            return;
        }

        request.delivery.deliverySpot = delivery.deliverySpot(requester, settings.deliveryDistance());
        BlockPos spot = request.delivery.deliverySpot;
        if (spot == null) {
            postTeleportFailure("Could not choose a safe delivery spot. Heading home with the kits.");
            return;
        }

        double requesterDistanceSq = delivery.horizontalDistanceSq(requester);
        boolean closeEnoughForDirectStep = delivery.shouldStepAwayDirectly(requesterDistanceSq);
        boolean directStepExpired = closeEnoughForDirectStep && request.delivery.directStepTicks >= DIRECT_STEP_FALLBACK_TICKS;
        request.delivery.positioningTicks++;
        boolean positioningExpired = request.delivery.crossDimensionDelivery
            && request.delivery.positioningTicks >= CROSS_DIMENSION_POSITIONING_FALLBACK_TICKS;
        StashKitbotDeliveryPlanner.DeliveryPositionDecision decision = StashKitbotDeliveryPlanner.deliveryPositionDecision(
            true,
            requesterDistanceSq,
            mc.player.getBlockPos().getSquaredDistance(spot),
            settings.deliveryDistance(),
            positioningExpired,
            movementExpired,
            directStepExpired
        );

        if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.THROW_NOW) {
            delivery.stopWalking();
            navigator.stop();
            request.delivery.directStepTicks = 0;
            request.delivery.positioningTicks = 0;
            request.delivery.throwDelay.reset(0);
            request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
            session.phase(KitbotPhase.THROWING);
            events.info("%s %d '%s' shulkers to %s from %.1f blocks away.",
                movementExpired || directStepExpired || positioningExpired ? "Delivery positioning timed out; throwing" : "Ready to throw",
                request.gather.gathered,
                request.kitName,
                request.requester,
                Math.sqrt(requesterDistanceSq)
            );
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.DIRECT_STEP_AWAY) {
            navigator.stop();
            request.delivery.directStepTicks++;
            delivery.walkAwayFrom(requester);
        }
        else if (decision == StashKitbotDeliveryPlanner.DeliveryPositionDecision.PATH_TO_SPOT) {
            request.delivery.directStepTicks = 0;
            delivery.stopWalking();
            navigator.ensureReturnTo(spot);
        }
        else {
            request.delivery.directStepTicks = 0;
            delivery.stopWalking();
            postTeleportFailure("Timed out moving into delivery position. Heading home with the kits.");
        }
    }

    private void tickThrowing(Settings settings) {
        KitRequest request = session.activeRequest();
        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (requester == null) {
            delivery.stopWalking();
            postTeleportFailure("I lost sight of you before throwing everything. Delivered %d/%d; heading home.".formatted(
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        if (request.delivery.delivered >= request.gather.gathered) {
            delivery.stopWalking();
            notifyDelivered(request);
            events.reply(request.requester, "Delivered %d '%s' shulkers. Returning home.".formatted(request.delivery.delivered, request.kitName));
            finishTpaAttempt(request);
            requestHome(settings);
            return;
        }

        if (request.delivery.deliveryTimeout.tickExpired()) {
            delivery.stopWalking();
            postTeleportFailure("Delivery timed out after %d/%d shulkers. Heading home.".formatted(request.delivery.delivered, request.gather.gathered));
            return;
        }

        if (request.delivery.throwDelay.tickDelay()) return;

        int slot = inventory.findMatchingInventorySlot(request.kitAlias);
        if (slot < 0) {
            postTeleportFailure("Could not find remaining '%s' shulkers to throw. Delivered %d/%d; heading home.".formatted(
                request.kitName,
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        delivery.aimLowAt(requester);
        if (!inventory.prepareThrowSlot(slot, request.kitAlias)) {
            postTeleportFailure("Could not throw '%s' from inventory. Delivered %d/%d; heading home.".formatted(
                request.kitName,
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        request.delivery.throwDelay.reset(PREPARED_THROW_TICKS);
        session.phase(KitbotPhase.PREPARED_THROW);
    }

    private void tickPreparedThrow(Settings settings) {
        KitRequest request = session.activeRequest();
        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (requester == null) {
            delivery.stopWalking();
            postTeleportFailure("I lost sight of you before throwing everything. Delivered %d/%d; heading home.".formatted(
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        if (request.delivery.throwDelay.tickDelay()) {
            delivery.aimLowAt(requester);
            return;
        }

        delivery.aimLowAt(requester);
        if (!inventory.dropSelectedPreparedSlot(request.kitAlias)) {
            postTeleportFailure("Could not drop prepared '%s' shulker. Delivered %d/%d; heading home.".formatted(
                request.kitName,
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        request.delivery.delivered++;
        if (request.delivery.delivered >= request.gather.gathered) {
            delivery.stopWalking();
            notifyDelivered(request);
            events.reply(request.requester, "Delivered %d '%s' shulkers. Returning home.".formatted(request.delivery.delivered, request.kitName));
            finishTpaAttempt(request);
            requestHome(settings);
            return;
        }

        request.delivery.throwDelay.reset(settings.throwDelayTicks());
        session.phase(KitbotPhase.THROWING);
        lookDown(settings);
    }

    private void tickHomeRequest(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        finishTpaAttempt(request);
        requestHome(settings);
    }

    private void tickHomeCooldown(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        finishTpaAttempt(request);
        if (request.cooldown.homeCooldown.tickDelay()) return;

        requestHome(settings);
    }

    private void tickHomeConfirm() {
        KitRequest request = session.activeRequest();
        boolean confirmExpired = request.cooldown.homeConfirm.tickExpired();
        boolean dead = isDeathScreenOpen() || isPlayerDead();
        boolean alive = isPlayerAlive();
        StashKitbotDeliveryPlanner.HomeConfirmDecision decision = StashKitbotDeliveryPlanner.homeConfirmDecision(
            request.delivery.homeRespawnRequired,
            confirmExpired,
            dead,
            request.delivery.homeRespawnRequested,
            alive
        );

        if (decision == StashKitbotDeliveryPlanner.HomeConfirmDecision.WAIT) return;
        if (decision == StashKitbotDeliveryPlanner.HomeConfirmDecision.REQUEST_RESPAWN) {
            requestRespawn(request);
            return;
        }

        events.info("Finished stash kit request for %s: delivered %d/%d %s.", request.requester, request.delivery.delivered, request.gather.gathered, request.kitName);
        events.reply(request.requester, "Finished '%s' delivery. Ready for the next request.".formatted(request.kitName));
        session.phase(KitbotPhase.DONE);
    }

    private void tickReturnToOrigin(Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        BlockPos origin = request.requestOrigin;
        if (origin == null) {
            session.phase(KitbotPhase.HOME_REQUEST);
            return;
        }

        if (mc.player.getBlockPos().getSquaredDistance(origin) <= 4) {
            navigator.stop();
            events.reply(request.requester, "Delivery cancelled before teleport. I returned to the stash position with the kits.");
            session.phase(KitbotPhase.DONE);
            return;
        }

        if (request.delivery.deliveryTimeout.tickExpired()) {
            navigator.stop();
            events.reply(request.requester, "Could not return to the stash position in time; trying home command.");
            requestHome(settings);
            return;
        }

        navigator.ensureReturnTo(origin);
    }

    private void beginReturnToOrigin(Settings settings) {
        KitRequest request = session.activeRequest();
        delivery.stopWalking();
        navigator.stop();
        request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
        session.phase(KitbotPhase.RETURN_TO_ORIGIN);
    }

    private void postTeleportFailure(String message) {
        events.reply(session.activeRequest().requester, message);
        events.warning(message);
        delivery.stopWalking();
        navigator.stop();
        session.phase(KitbotPhase.HOME_REQUEST);
    }

    private void notifyDelivered(KitRequest request) {
        if (request.delivery.notifiedDelivered) return;

        request.delivery.notifiedDelivered = true;
        events.delivered(request);
    }

    private boolean handlePendingCommandCooldown(KitRequest request, String message, Settings settings) {
        DeliveryCommandState commands = request.delivery.commands;
        PendingCommand pending = commands.pendingCommand;
        boolean applies = StashKitbotDeliveryPlanner.cooldownApplies(
            pending,
            pending,
            messenger.hasPendingCommandWindow() && messenger.pendingCommand() == pending,
            commands.hasPending(),
            pendingCommandTargetMatches(request, commands),
            session.phase(),
            commands.expectedCooldownPhase
        );
        if (!applies) return false;

        int cooldownTicks = cooldownTicks(message, settings);
        int attemptId = commands.pendingAttemptId;
        if (pending == PendingCommand.TPA) {
            if (!StashKitbotDeliveryPlanner.tpaCooldownMayRearm(session.phase(), commands.tpaSent)) {
                return false;
            }

            request.delivery.pendingCommandCooldown.start(PendingCommand.TPA, attemptId, cooldownTicks, TPA_COOLDOWN_GRACE_TICKS, message);
            events.info("Queued possible TPA cooldown for attempt %d: %s. Waiting briefly for server acceptance.", attemptId, delivery.formatDuration(cooldownTicks));
            return true;
        }

        if (pending == PendingCommand.HOME) {
            request.cooldown.homeCooldown.reset(cooldownTicks);
            commands.markCooldown(PendingCommand.HOME);
            messenger.clearPendingCommand();
            session.phase(KitbotPhase.HOME_COOLDOWN);
            events.info("Accepted home cooldown for attempt %d: %s.", attemptId, delivery.formatDuration(cooldownTicks));
            events.reply(request.requester, "Delivery is handled, but my home command is cooling down for about %s. I will retry.".formatted(delivery.formatDuration(cooldownTicks)));
            return true;
        }

        return false;
    }

    private void applyPendingCommandCooldown(KitRequest request, Settings settings) {
        PendingCommandCooldown pendingCooldown = request.delivery.pendingCommandCooldown;
        if (!pendingCooldown.active()) return;

        if (pendingCooldown.command == PendingCommand.TPA) {
            request.cooldown.tpaCooldown.reset(pendingCooldown.cooldownTicks);
            request.delivery.teleportWait.reset(settings.teleportTimeoutTicks());
            request.delivery.commands.markCooldown(PendingCommand.TPA);
            messenger.clearPendingCommand();
            session.phase(KitbotPhase.TPA_REQUEST);
            events.info("Confirmed TPA cooldown for attempt %d: %s.", pendingCooldown.attemptId, delivery.formatDuration(pendingCooldown.cooldownTicks));
            events.reply(request.requester, "TPA is cooling down for about %s. I will retry then.".formatted(delivery.formatDuration(pendingCooldown.cooldownTicks)));
            pendingCooldown.clear();
        }
    }

    private void handleTpaAccepted(KitRequest request) {
        request.delivery.pendingCommandCooldown.clear();
        request.delivery.commands.tpaSent = true;
        clearPendingCommandIf(PendingCommand.TPA);
        if (session.phase() == KitbotPhase.TPA_REQUEST) session.phase(KitbotPhase.WAITING_FOR_TPY);
        events.info("TPA request accepted by server for %s.", request.requester);
    }

    private void ignoreStaleCommandCooldown(KitRequest request, String message) {
        DeliveryCommandState commands = request.delivery.commands;
        events.warning("Ignored stale %s cooldown while in %s for %s: %s", commands.pendingCommand, session.phase(), request.requester, message);
        commands.clearPending();
        messenger.clearPendingCommand();
    }

    private boolean pendingCommandTargetMatches(KitRequest request, DeliveryCommandState commands) {
        if (commands.pendingCommand == PendingCommand.TPA) return request.requester.equals(commands.pendingTarget);
        if (commands.pendingCommand == PendingCommand.HOME) return "home".equals(commands.pendingTarget);
        return false;
    }

    private boolean tryStartDeliveryAfterTeleport(Settings settings) {
        KitRequest request = session.activeRequest();
        if (request == null) return false;
        if (!request.delivery.commands.tpaSent) return false;
        if (mc.player == null || mc.world == null) return false;

        if (!delivery.hasTeleportArrived(request.delivery.preTpaPos, request.delivery.preTpaDimension)) {
            return false;
        }

        request.delivery.pendingCommandCooldown.clear();
        String currentDimension = StashClientUtils.dimensionId(mc);
        request.delivery.crossDimensionDelivery = StashKitbotDeliveryPlanner.dimensionChangedAfterTpa(
            request.delivery.preTpaDimension,
            currentDimension
        );
        finishTpaAttempt(request);

        if (request.delivery.crossDimensionDelivery) {
            request.delivery.crossDimensionSettleTicks = CROSS_DIMENSION_SETTLE_TICKS;
            delivery.stopWalking();
            navigator.stop();
            request.delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());
            session.phase(KitbotPhase.REACQUIRE_REQUESTER);
            events.info("Detected cross-dimension teleport for %s. Waiting for world to settle before searching.", request.requester);
            return true;
        }

        AbstractClientPlayerEntity requester = findDeliveryTarget(request, settings);
        if (startMovingToDeliverySpot(request, requester, settings)) {
            events.info("Detected teleport to %s. Moving to delivery spot.", request.requester);
            return true;
        }

        delivery.stopWalking();
        navigator.stop();
        request.delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());
        session.phase(KitbotPhase.REACQUIRE_REQUESTER);
        events.info("Detected teleport for %s. Waiting for requester entity to load.", request.requester);
        return true;
    }

    private boolean startMovingToDeliverySpot(KitRequest request, AbstractClientPlayerEntity requester, Settings settings) {
        if (requester == null) return false;
        if (!request.delivery.crossDimensionDelivery && !delivery.isRequesterNearby(requester, effectiveRequesterSearchRadius(request, settings))) return false;

        request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
        request.delivery.directStepTicks = 0;
        request.delivery.positioningTicks = 0;
        request.delivery.deliveryTraceTicks = 0;
        request.delivery.deliverySpot = delivery.deliverySpot(requester, settings.deliveryDistance());
        session.phase(KitbotPhase.MOVE_TO_DELIVERY_SPOT);
        return true;
    }

    private void beginRequesterReacquire(Settings settings) {
        delivery.stopWalking();
        navigator.stop();
        session.activeRequest().delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());
        session.phase(KitbotPhase.REACQUIRE_REQUESTER);
    }

    private AbstractClientPlayerEntity findDeliveryTarget(KitRequest request, Settings settings) {
        return delivery.findRequester(request.requester, effectiveRequesterSearchRadius(request, settings));
    }

    private int effectiveRequesterSearchRadius(KitRequest request, Settings settings) {
        return StashKitbotDeliveryPlanner.effectiveRequesterSearchRadius(
            settings.requesterSearchRadius(),
            request.delivery.crossDimensionDelivery
        );
    }

    private void correctCrossDimensionFlagIfNeeded(KitRequest request, String phaseLabel, boolean resetSettleTicks) {
        String currentDimension = StashClientUtils.dimensionId(mc);
        if (!StashKitbotDeliveryPlanner.shouldCorrectCrossDimensionFlag(
            request.delivery.crossDimensionDelivery,
            request.delivery.preTpaDimension,
            currentDimension
        )) {
            return;
        }

        request.delivery.crossDimensionDelivery = true;
        if (resetSettleTicks) {
            request.delivery.crossDimensionSettleTicks = StashKitbotDeliveryPlanner.correctedCrossDimensionSettleTicks(
                request.delivery.crossDimensionSettleTicks,
                CROSS_DIMENSION_SETTLE_TICKS
            );
        }
        events.info("Corrected cross-dimension delivery flag for %s in %s (detected in %s phase).", request.requester, currentDimension, phaseLabel);
    }

    private void tickCrossDimensionMoveToRequester(KitRequest request, AbstractClientPlayerEntity requester, Settings settings, boolean movementExpired) {
        double requesterDistanceSq = delivery.horizontalDistanceSq(requester);
        boolean closeEnoughForDirectStep = delivery.shouldStepAwayDirectly(requesterDistanceSq);
        boolean directStepExpired = closeEnoughForDirectStep && request.delivery.directStepTicks >= DIRECT_STEP_FALLBACK_TICKS;
        StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision decision = StashKitbotDeliveryPlanner.crossDimensionDeliveryDecision(
            true,
            requesterDistanceSq,
            settings.deliveryDistance(),
            movementExpired,
            directStepExpired
        );
        logCrossDimensionWait(request, settings, "move_to_delivery_spot:" + decision.name().toLowerCase());

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.THROW_NOW) {
            delivery.stopWalking();
            navigator.stop();
            request.delivery.directStepTicks = 0;
            request.delivery.throwDelay.reset(0);
            request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
            session.phase(KitbotPhase.THROWING);
            events.info("Cross-dimension requester loaded; throwing %d '%s' shulkers to %s from %.1f blocks away.",
                request.gather.gathered,
                request.kitName,
                request.requester,
                Math.sqrt(requesterDistanceSq)
            );
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.DIRECT_STEP_AWAY) {
            navigator.stop();
            request.delivery.directStepTicks++;
            delivery.walkAwayFrom(requester);
            return;
        }

        if (decision == StashKitbotDeliveryPlanner.CrossDimensionDeliveryDecision.APPROACH_REQUESTER) {
            navigator.stop();
            request.delivery.directStepTicks = 0;
            delivery.walkToward(requester);
            return;
        }

        request.delivery.directStepTicks = 0;
        delivery.stopWalking();
        postTeleportFailure("I found you after teleporting, but could not get into delivery range. Heading home with the kits.");
    }

    private void logCrossDimensionWait(KitRequest request, Settings settings, String decision) {
        if (!settings.traceLogs() || request == null || !isCrossDimensionDeliveryContext(request)) return;
        if (request.delivery.deliveryTraceTicks++ % CROSS_DIMENSION_TRACE_INTERVAL_TICKS != 0) return;

        AbstractClientPlayerEntity requester = delivery.findRequester(request.requester, effectiveRequesterSearchRadius(request, settings));
        events.info("[kitbot trace] cross-dimension delivery phase=%s dimension=%s requesterLoaded=%s decision=%s %s.",
            session.phase().name().toLowerCase(),
            StashClientUtils.dimensionId(mc),
            requester != null,
            decision,
            delivery.loadedPlayerDebug(requester)
        );
    }

    private boolean isCrossDimensionDeliveryContext(KitRequest request) {
        return request.delivery.crossDimensionDelivery
            || StashKitbotDeliveryPlanner.dimensionChangedAfterTpa(request.delivery.preTpaDimension, StashClientUtils.dimensionId(mc));
    }

    private void expirePendingCommandWindow() {
        KitRequest request = session.activeRequest();
        if (request == null) return;

        DeliveryCommandState commands = request.delivery.commands;
        if (!StashKitbotDeliveryPlanner.pendingCommandExpired(commands.hasPending(), messenger.hasPendingCommandWindow())) return;

        events.info("Delivery command %s attempt %d left cooldown-listen window in phase %s.",
            commands.pendingCommand,
            commands.pendingAttemptId,
            session.phase()
        );
        commands.clearPending();
    }

    private void clearPendingCommandIf(PendingCommand command) {
        KitRequest request = session.activeRequest();
        if (request == null || request.delivery.commands.pendingCommand != command) return;

        request.delivery.commands.clearPending();
        if (messenger.pendingCommand() == command) messenger.clearPendingCommand();
    }

    private void finishTpaAttempt(KitRequest request) {
        if (request == null) return;

        DeliveryCommandState commands = request.delivery.commands;
        if (commands.pendingCommand == PendingCommand.TPA) commands.clearPending();
        if (messenger.pendingCommand() == PendingCommand.TPA) messenger.clearPendingCommand();
        commands.tpaSent = true;
    }

    private void requestHome(Settings settings) {
        KitRequest request = session.activeRequest();
        DeliveryCommandState commands = request.delivery.commands;
        boolean pendingHome = commands.pendingCommand == PendingCommand.HOME && messenger.hasPendingCommandWindow();
        boolean confirmingHome = session.phase() == KitbotPhase.HOME_CONFIRM;
        boolean cooldownActive = false;
        if (!StashKitbotDeliveryPlanner.homeSendAllowed(cooldownActive, commands.homeSent, pendingHome, confirmingHome)) {
            if (!cooldownActive && (commands.homeSent || pendingHome || confirmingHome)) {
                session.phase(KitbotPhase.HOME_CONFIRM);
                request.cooldown.homeConfirm.reset(HOME_CONFIRM_TICKS);
            }
            return;
        }

        int attemptId = commands.beginPending(PendingCommand.HOME, "home", KitbotPhase.HOME_CONFIRM);
        request.delivery.homeRespawnRequired = StashKitbotDeliveryPlanner.homeCommandRequiresRespawn(settings.homeCommand());
        request.delivery.homeRespawnRequested = false;
        if (!messenger.sendHomeCommand(settings.homeCommand())) {
            commands.clearPending();
            commands.homeSent = false;
            request.delivery.homeRespawnRequired = false;
            request.delivery.homeRespawnRequested = false;
            postTeleportFailure("Could not send home command. Delivery is paused with the kits.");
            return;
        }

        commands.markSent(PendingCommand.HOME);
        events.info("Sent home command attempt %d after stash kit delivery.", attemptId);
        session.phase(KitbotPhase.HOME_CONFIRM);
        request.cooldown.homeConfirm.reset(HOME_CONFIRM_TICKS);
    }

    private void requestRespawn(KitRequest request) {
        if (mc.player == null) return;

        request.delivery.homeRespawnRequested = true;
        mc.player.requestRespawn();
        if (isDeathScreenOpen()) mc.setScreen(null);
        events.info("Requested respawn after /kill home command for %s.", request.requester);
    }

    private boolean isDeathScreenOpen() {
        return mc.currentScreen instanceof DeathScreen;
    }

    private boolean isPlayerDead() {
        return mc.player != null && (!mc.player.isAlive() || mc.player.getHealth() <= 0.0F);
    }

    private boolean isPlayerAlive() {
        return mc.player != null && mc.world != null && mc.player.isAlive() && mc.player.getHealth() > 0.0F && !isDeathScreenOpen();
    }

    private void lookDown(Settings settings) {
        delivery.lookDown(settings.deliveryLookPitch());
    }

    record Settings(
        String tpaCommand,
        String homeCommand,
        int deliveryDistance,
        int teleportTimeoutTicks,
        int deliveryTimeoutTicks,
        int requesterReacquireTimeoutTicks,
        int requesterSearchRadius,
        int throwDelayTicks,
        int deliveryLookPitch,
        int fallbackCooldownTicks,
        boolean traceLogs
    ) {
    }
}
