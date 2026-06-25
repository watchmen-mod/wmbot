package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;

final class StashKitbotLifecycleWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashNavigator navigator;
    private final StashSafetyGuard safetyGuard;
    private final StashKitbotDelivery delivery;
    private final StashKitbotMessenger messenger;
    private final StashKitbotQueueWorkflow queueWorkflow;
    private final Callbacks callbacks;

    StashKitbotLifecycleWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashNavigator navigator,
        StashSafetyGuard safetyGuard,
        StashKitbotDelivery delivery,
        StashKitbotMessenger messenger,
        StashKitbotQueueWorkflow queueWorkflow,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.navigator = navigator;
        this.safetyGuard = safetyGuard;
        this.delivery = delivery;
        this.messenger = messenger;
        this.queueWorkflow = queueWorkflow;
        this.callbacks = callbacks;
    }

    void activate() {
        queueWorkflow.reset();
    }

    void deactivate() {
        queueWorkflow.captureDeliveryResume();
        queueWorkflow.savePersistentQueueState();
        delivery.stopWalking();
        navigator.stop();
        safetyGuard.restore();
        safetyGuard.cancelBreaking(mc);
        StashClientUtils.closeContainerScreen(mc);
        session.clearRequest();
        session.clearQueuedRequests();
        queueWorkflow.markUnloaded();
    }

    void failRequest(String message) {
        StashClientUtils.closeContainerScreen(mc);
        navigator.stop();
        KitRequest activeRequest = session.activeRequest();
        if (activeRequest != null) callbacks.reply(activeRequest.requester, message);
        callbacks.warning(message);
        session.phase(KitbotPhase.FAILED);
    }

    void finishRequest() {
        delivery.stopWalking();
        safetyGuard.restore();
        messenger.clearCommand();
        session.clearRequest();
        queueWorkflow.savePersistentQueueState();
    }

    interface Callbacks {
        void reply(String player, String message);

        void warning(String message, Object... args);
    }
}
