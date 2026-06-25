package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

final class StashKitbotDeliveryWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashNavigator navigator;
    private final StashKitbotMessenger messenger;
    private final StashKitbotDelivery delivery;
    private final StashKitbotEvents events;
    private final StashKitbotDeliveryCommandWorkflow commandWorkflow;
    private final StashKitbotDeliveryPositionWorkflow positionWorkflow;
    private final StashKitbotDeliveryThrowWorkflow throwWorkflow;
    private final StashKitbotDeliveryCompletionWorkflow completionWorkflow;

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
        this.events = events;
        commandWorkflow = new StashKitbotDeliveryCommandWorkflow(
            session,
            messenger,
            delivery,
            events,
            new StashKitbotDeliveryCommandWorkflow.TeleportCallbacks() {
                @Override
                public boolean tryStartDeliveryAfterTeleport(Settings settings) {
                    return StashKitbotDeliveryWorkflow.this.tryStartDeliveryAfterTeleport(settings);
                }

                @Override
                public void postTeleportFailure(String message) {
                    StashKitbotDeliveryWorkflow.this.postTeleportFailure(message);
                }
            }
        );
        positionWorkflow = new StashKitbotDeliveryPositionWorkflow(
            mc,
            session,
            navigator,
            delivery,
            events,
            StashKitbotDeliveryWorkflow.this::postTeleportFailure
        );
        throwWorkflow = new StashKitbotDeliveryThrowWorkflow(
            session,
            delivery,
            inventory,
            events,
            positionWorkflow,
            commandWorkflow,
            StashKitbotDeliveryWorkflow.this::postTeleportFailure
        );
        completionWorkflow = new StashKitbotDeliveryCompletionWorkflow(
            mc,
            session,
            navigator,
            delivery,
            events,
            commandWorkflow
        );
    }

    void tick(Settings settings) {
        commandWorkflow.expirePendingCommandWindow();
        commandWorkflow.tickPendingCommandCooldown(settings);
        switch (session.phase()) {
            case TPA_REQUEST -> tickTpaRequest(settings);
            case WAITING_FOR_TPY -> tickWaitingForTpy(settings);
            case REACQUIRE_REQUESTER -> positionWorkflow.tickReacquireRequester(settings);
            case MOVE_TO_DELIVERY_SPOT -> positionWorkflow.tickMoveToDeliverySpot(settings);
            case THROWING -> throwWorkflow.tickThrowing(settings);
            case PREPARED_THROW -> throwWorkflow.tickPreparedThrow(settings);
            case HOME_REQUEST -> completionWorkflow.tickHomeRequest(settings);
            case HOME_COOLDOWN -> completionWorkflow.tickHomeCooldown(settings);
            case HOME_CONFIRM -> completionWorkflow.tickHomeConfirm();
            case RETURN_TO_ORIGIN -> completionWorkflow.tickReturnToOrigin(settings);
            default -> {
            }
        }
    }

    void handleCooldownMessage(String message, Settings settings) {
        commandWorkflow.handleCooldownMessage(message, settings);
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
        positionWorkflow.logCrossDimensionWait(request, settings, "waiting_for_tpy");
        if (tryStartDeliveryAfterTeleport(settings)) return;

        if (StashKitbotDeliveryPlanner.tpaTimeoutShouldReturn(false, request.delivery.teleportWait.tickExpired())) {
            commandWorkflow.clearPendingCommandIf(PendingCommand.TPA);
            events.reply(request.requester, "Delivery timed out waiting for /tpy. Returning to stash position.");
            completionWorkflow.beginReturnToOrigin(settings);
        }
    }

    private void postTeleportFailure(String message) {
        events.reply(session.activeRequest().requester, message);
        events.warning(message);
        delivery.stopWalking();
        navigator.stop();
        session.phase(KitbotPhase.HOME_REQUEST);
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
        commandWorkflow.finishTpaAttempt(request);

        if (request.delivery.crossDimensionDelivery) {
            request.delivery.crossDimensionSettleTicks = StashKitbotDeliveryPositionWorkflow.CROSS_DIMENSION_SETTLE_TICKS;
            delivery.stopWalking();
            navigator.stop();
            request.delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());
            session.phase(KitbotPhase.REACQUIRE_REQUESTER);
            events.info("Detected cross-dimension teleport for %s. Waiting for world to settle before searching.", request.requester);
            return true;
        }

        AbstractClientPlayerEntity requester = positionWorkflow.findDeliveryTarget(request, settings);
        if (positionWorkflow.startMovingToDeliverySpot(request, requester, settings)) {
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
