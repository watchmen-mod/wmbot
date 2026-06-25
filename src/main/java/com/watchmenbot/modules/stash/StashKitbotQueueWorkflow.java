package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;

final class StashKitbotQueueWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashKitbotInventory inventory;
    private final Callbacks callbacks;

    private StashKitbotQueueState.DeliveryResume pendingDeliveryResume;
    private boolean persistentQueueLoaded;

    StashKitbotQueueWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashKitbotInventory inventory,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.inventory = inventory;
        this.callbacks = callbacks;
    }

    void reset() {
        persistentQueueLoaded = false;
        pendingDeliveryResume = null;
    }

    void markUnloaded() {
        persistentQueueLoaded = false;
    }

    void captureDeliveryResume() {
        KitRequest request = session.activeRequest();
        if (request == null || !session.isDeliveryPhase() || !request.delivery.commands.tpaSent) return;

        pendingDeliveryResume = StashKitbotQueueState.resumeFromRequest(request);
        if (pendingDeliveryResume != null) {
            callbacks.info("Saved cross-server delivery resume for %d x '%s' to %s.", request.count, request.kitName, request.requester);
        }
    }

    void loadPersistentQueueState() {
        if (persistentQueueLoaded) return;
        if (mc == null || mc.runDirectory == null) return;

        StashKitbotQueueState.LoadResult result = StashKitbotQueueState.load(mc);
        StashKitbotQueueState.State state = result.state();
        if (result.failed()) callbacks.warning("Failed to read stash kitbot queue state; starting with an empty persisted queue.");

        if (!session.hasActiveRequest() && session.queuedCount() == 0) {
            session.replaceQueuedRequests(state.queuedRequests());
        }
        if (pendingDeliveryResume == null) pendingDeliveryResume = state.deliveryResume();

        persistentQueueLoaded = true;
        if (session.queuedCount() > 0 || pendingDeliveryResume != null) {
            callbacks.info("Loaded persisted stash kitbot state: %d queued request%s%s.",
                session.queuedCount(),
                session.queuedCount() == 1 ? "" : "s",
                pendingDeliveryResume == null ? "" : " and a pending delivery resume"
            );
        }
    }

    void savePersistentQueueState() {
        try {
            StashKitbotQueueState.write(
                mc,
                new StashKitbotQueueState.State(session.queuedRequestsSnapshot(), pendingDeliveryResume)
            );
        }
        catch (IOException exception) {
            callbacks.warning("Failed to write stash kitbot queue state: %s.", exception.getMessage());
        }
    }

    void promoteQueuedRequests() {
        while (!session.hasActiveRequest()) {
            QueuedKitRequest queued = session.pollNextQueuedRequest();
            if (queued == null) return;

            savePersistentQueueState();
            callbacks.info("Starting queued stash kit request from %s: %s x%d.", queued.access().requester(), queued.command().name(), queued.command().count());
            callbacks.startRequest(queued.access(), queued.command(), true);
        }
    }

    boolean tryResumeDelivery(Settings settings) {
        if (pendingDeliveryResume == null) return false;

        StashKitbotQueueState.DeliveryResume resume = pendingDeliveryResume;
        pendingDeliveryResume = null;
        savePersistentQueueState();

        String currentDimension = StashClientUtils.dimensionId(mc);
        boolean dimensionChanged = StashKitbotDeliveryPlanner.dimensionChangedAfterTpa(resume.preTpaDimension(), currentDimension);
        if (!dimensionChanged) {
            callbacks.reply(resume.requester(), "Delivery resume cancelled for '%s': I am still in the same dimension.".formatted(resume.kitName()));
            callbacks.info("Cross-server delivery resume cancelled for '%s': still in same dimension (%s).", resume.kitName(), currentDimension);
            return true;
        }

        String resumeAlias = StashKitNameNormalizer.alias(resume.kitName());
        int inventoryCount = inventory.matchingInventoryCount(resumeAlias);
        if (inventoryCount <= 0) {
            callbacks.reply(resume.requester(), "Delivery resume cancelled for '%s': I do not have the kits in inventory anymore.".formatted(resume.kitName()));
            callbacks.info("Cross-server delivery resume cancelled for '%s': no items found in inventory.", resume.kitName());
            return true;
        }

        int gathered = Math.min(resume.gatheredCount(), inventoryCount);
        if (gathered <= 0) {
            callbacks.reply(resume.requester(), "Delivery resume cancelled for '%s': no gathered kits remain in inventory.".formatted(resume.kitName()));
            callbacks.info("Cross-server delivery resume cancelled for '%s': no gathered items remain in inventory.", resume.kitName());
            return true;
        }

        KitRequest request = new KitRequest(resume.requester(), resume.kitName(), resumeAlias, resume.requestedCount(), java.util.List.of(), null);
        request.gather.initialInventoryCount = 0;
        request.gather.gathered = gathered;
        request.delivery.commands.tpaSent = true;
        request.delivery.preTpaDimension = resume.preTpaDimension();
        request.delivery.crossDimensionDelivery = true;
        request.delivery.crossDimensionSettleTicks = 40;
        request.delivery.requesterReacquire.reset(settings.requesterReacquireTimeoutTicks());

        session.startRequest(request);
        session.phase(KitbotPhase.REACQUIRE_REQUESTER);
        callbacks.info("Resuming cross-server delivery after dimension change: %d '%s' to %s in %s.", request.gather.gathered, resume.kitName(), resume.requester(), currentDimension);
        return true;
    }

    interface Callbacks {
        void reply(String player, String message);

        void info(String message, Object... args);

        void warning(String message, Object... args);

        boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued);
    }

    record Settings(int requesterReacquireTimeoutTicks) {
    }
}
