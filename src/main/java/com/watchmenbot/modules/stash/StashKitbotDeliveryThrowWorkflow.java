package com.watchmenbot.modules.stash;

import net.minecraft.client.network.AbstractClientPlayerEntity;

final class StashKitbotDeliveryThrowWorkflow {
    private static final int PREPARED_THROW_TICKS = 2;

    private final StashKitbotSession session;
    private final StashKitbotDelivery delivery;
    private final StashKitbotInventory inventory;
    private final StashKitbotEvents events;
    private final StashKitbotDeliveryPositionWorkflow positionWorkflow;
    private final StashKitbotDeliveryCommandWorkflow commandWorkflow;
    private final Callbacks callbacks;

    StashKitbotDeliveryThrowWorkflow(
        StashKitbotSession session,
        StashKitbotDelivery delivery,
        StashKitbotInventory inventory,
        StashKitbotEvents events,
        StashKitbotDeliveryPositionWorkflow positionWorkflow,
        StashKitbotDeliveryCommandWorkflow commandWorkflow,
        Callbacks callbacks
    ) {
        this.session = session;
        this.delivery = delivery;
        this.inventory = inventory;
        this.events = events;
        this.positionWorkflow = positionWorkflow;
        this.commandWorkflow = commandWorkflow;
        this.callbacks = callbacks;
    }

    void tickThrowing(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        AbstractClientPlayerEntity requester = positionWorkflow.findDeliveryTarget(request, settings);
        if (requester == null) {
            delivery.stopWalking();
            callbacks.postTeleportFailure("I lost sight of you before throwing everything. Delivered %d/%d; heading home.".formatted(
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        if (request.delivery.delivered >= request.gather.gathered) {
            delivery.stopWalking();
            notifyDelivered(request);
            events.reply(request.requester, "Delivered %d '%s' shulkers. Returning home.".formatted(request.delivery.delivered, request.kitName));
            commandWorkflow.finishTpaAttempt(request);
            commandWorkflow.requestHome(settings);
            return;
        }

        if (request.delivery.deliveryTimeout.tickExpired()) {
            delivery.stopWalking();
            callbacks.postTeleportFailure("Delivery timed out after %d/%d shulkers. Heading home.".formatted(request.delivery.delivered, request.gather.gathered));
            return;
        }

        if (request.delivery.throwDelay.tickDelay()) return;

        int slot = inventory.findMatchingInventorySlot(request.kitAlias);
        if (slot < 0) {
            callbacks.postTeleportFailure("Could not find remaining '%s' shulkers to throw. Delivered %d/%d; heading home.".formatted(
                request.kitName,
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        delivery.aimLowAt(requester);
        if (!inventory.prepareThrowSlot(slot, request.kitAlias)) {
            callbacks.postTeleportFailure("Could not throw '%s' from inventory. Delivered %d/%d; heading home.".formatted(
                request.kitName,
                request.delivery.delivered,
                request.gather.gathered
            ));
            return;
        }

        request.delivery.throwDelay.reset(PREPARED_THROW_TICKS);
        session.phase(KitbotPhase.PREPARED_THROW);
    }

    void tickPreparedThrow(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        AbstractClientPlayerEntity requester = positionWorkflow.findDeliveryTarget(request, settings);
        if (requester == null) {
            delivery.stopWalking();
            callbacks.postTeleportFailure("I lost sight of you before throwing everything. Delivered %d/%d; heading home.".formatted(
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
            callbacks.postTeleportFailure("Could not drop prepared '%s' shulker. Delivered %d/%d; heading home.".formatted(
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
            commandWorkflow.finishTpaAttempt(request);
            commandWorkflow.requestHome(settings);
            return;
        }

        request.delivery.throwDelay.reset(settings.throwDelayTicks());
        session.phase(KitbotPhase.THROWING);
        lookDown(settings);
    }

    private void notifyDelivered(KitRequest request) {
        if (request.delivery.notifiedDelivered) return;

        request.delivery.notifiedDelivered = true;
        events.delivered(request);
    }

    private void lookDown(StashKitbotDeliveryWorkflow.Settings settings) {
        delivery.lookDown(settings.deliveryLookPitch());
    }

    interface Callbacks {
        void postTeleportFailure(String message);
    }
}
