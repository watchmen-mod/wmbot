package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.util.math.BlockPos;

final class StashKitbotDeliveryCompletionWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashNavigator navigator;
    private final StashKitbotDelivery delivery;
    private final StashKitbotEvents events;
    private final StashKitbotDeliveryCommandWorkflow commandWorkflow;

    StashKitbotDeliveryCompletionWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashNavigator navigator,
        StashKitbotDelivery delivery,
        StashKitbotEvents events,
        StashKitbotDeliveryCommandWorkflow commandWorkflow
    ) {
        this.mc = mc;
        this.session = session;
        this.navigator = navigator;
        this.delivery = delivery;
        this.events = events;
        this.commandWorkflow = commandWorkflow;
    }

    void tickHomeRequest(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        commandWorkflow.finishTpaAttempt(request);
        commandWorkflow.requestHome(settings);
    }

    void tickHomeCooldown(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        lookDown(settings);
        commandWorkflow.finishTpaAttempt(request);
        if (request.cooldown.homeCooldown.tickDelay()) return;

        commandWorkflow.requestHome(settings);
    }

    void tickHomeConfirm() {
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

    void tickReturnToOrigin(StashKitbotDeliveryWorkflow.Settings settings) {
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
            commandWorkflow.requestHome(settings);
            return;
        }

        navigator.ensureReturnTo(origin);
    }

    void beginReturnToOrigin(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        delivery.stopWalking();
        navigator.stop();
        request.delivery.deliveryTimeout.reset(settings.deliveryTimeoutTicks());
        session.phase(KitbotPhase.RETURN_TO_ORIGIN);
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

    private void lookDown(StashKitbotDeliveryWorkflow.Settings settings) {
        delivery.lookDown(settings.deliveryLookPitch());
    }
}
