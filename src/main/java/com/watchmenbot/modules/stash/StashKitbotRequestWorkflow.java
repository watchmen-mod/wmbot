package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StashKitbotRequestWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashInventoryCache cache;
    private final StashKitbotInventory inventory;
    private final StashKitbotRequestParser requestParser;
    private final StashKitbotStockPlanner stockPlanner;
    private final StashKitbotCooldowns cooldowns;
    private final Callbacks callbacks;

    StashKitbotRequestWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashInventoryCache cache,
        StashKitbotInventory inventory,
        StashKitbotRequestParser requestParser,
        StashKitbotStockPlanner stockPlanner,
        StashKitbotCooldowns cooldowns,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.cache = cache;
        this.inventory = inventory;
        this.requestParser = requestParser;
        this.stockPlanner = stockPlanner;
        this.cooldowns = cooldowns;
        this.callbacks = callbacks;
    }

    void handleMessage(String message, Settings settings) {
        callbacks.handleCooldownMessage(message);

        Whisper whisper = requestParser.parseWhisper(message);
        if (whisper == null) return;

        Optional<KitbotRequesterAccess> access = StashKitbotAccessPlanner.resolveAccess(
            whisper.sender(),
            settings.tier1Nicknames(),
            settings.tier1CooldownTicks(),
            settings.tier2Nicknames(),
            settings.tier2CooldownTicks()
        );
        if (access.isEmpty()) return;

        KitRequestIntent intent = requestParser.parseIntent(whisper.body());
        if (intent.isListKits()) {
            replyAvailableKits(access.get(), settings);
            return;
        }

        KitCommand command = requestParser.parseCommand(
            whisper.body(),
            access.get().tier() == KitbotTier.TIER_2,
            StashKitbotAccessPlanner.TIER_2_DEFAULT_KIT
        );
        if (command == null) return;

        if (command.count() <= 0) {
            callbacks.reply(whisper.sender(), "Invalid kit count.");
            return;
        }

        if (command.count() > settings.maxRequestCount()) {
            callbacks.reply(whisper.sender(), "Request too large. Max is %d shulkers.".formatted(settings.maxRequestCount()));
            return;
        }

        if (!StashKitbotAccessPlanner.requestAllowed(access.get(), command, settings.tier2KitWhitelist())) {
            callbacks.reply(whisper.sender(), "That kit is not available for your tier.");
            return;
        }

        long remainingCooldownMillis = cooldowns.remainingMillis(mc, access.get());
        if (remainingCooldownMillis > 0L) {
            callbacks.reply(whisper.sender(), "Please wait %s before requesting another kit.".formatted(StashKitbotAccessPlanner.formatCooldown(remainingCooldownMillis)));
            return;
        }

        if (rejectDuplicateRequest(access.get())) return;

        if (StashKitbotQueuePlanner.shouldQueue(session.hasActiveRequest(), session.queuedCount())) {
            enqueueRequest(access.get(), command, settings);
            return;
        }

        if (callbacks.startRequest(access.get(), command, false)) cooldowns.start(mc, access.get());
    }

    private void replyAvailableKits(KitbotRequesterAccess access, Settings settings) {
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            callbacks.reply(access.requester(), "Failed to read stash cache.");
            callbacks.warning("Failed to read stash inventory cache before listing kits: %s.", exception.getMessage());
            return;
        }

        List<String> kitNames = stockPlanner.availableKitNames(cacheFile, inventory.allKitCounts(), access, settings.tier2KitWhitelist());
        for (String message : stockPlanner.kitListMessages(kitNames)) {
            callbacks.reply(access.requester(), message);
        }
    }

    private void enqueueRequest(KitbotRequesterAccess access, KitCommand command, Settings settings) {
        if (requestIsAmbiguous(access.requester(), command)) return;

        int queuedCount = session.queuedCount();
        int maxQueued = settings.maxQueuedRequests();
        if (!StashKitbotQueuePlanner.canEnqueue(maxQueued, queuedCount)) {
            if (maxQueued <= 0) {
                KitRequest activeRequest = session.activeRequest();
                if (activeRequest != null) {
                    callbacks.reply(access.requester(), "Busy gathering %s for %s: %d/%d collected.".formatted(
                        activeRequest.kitName,
                        activeRequest.requester,
                        activeRequest.gather.gathered,
                        activeRequest.count
                    ));
                }
                else {
                    callbacks.reply(access.requester(), "Busy. Request queueing is disabled.");
                }
            }
            else {
                callbacks.reply(access.requester(), "Request queue is full (%d waiting). Please try again later.".formatted(queuedCount));
            }
            return;
        }

        int position = StashKitbotQueuePlanner.queuePosition(queuedCount);
        session.enqueue(new QueuedKitRequest(access, command));
        callbacks.savePersistentQueueState();
        cooldowns.start(mc, access);
        callbacks.reply(access.requester(), "Queued '%s' x%d. Position %d/%d.".formatted(command.name(), command.count(), position, maxQueued));
        callbacks.info("Queued stash kit request from %s: %s x%d at position %d/%d.", access.requester(), command.name(), command.count(), position, maxQueued);
    }

    private boolean rejectDuplicateRequest(KitbotRequesterAccess access) {
        RequesterRequestStatus status = session.requestStatus(access.normalizedRequester());
        if (status == RequesterRequestStatus.NONE) return false;

        if (status == RequesterRequestStatus.ACTIVE) {
            KitRequest activeRequest = session.activeRequest();
            if (activeRequest != null) {
                callbacks.reply(access.requester(), "You already have a kit request in progress: '%s' x%d.".formatted(activeRequest.kitName, activeRequest.count));
            }
            else {
                callbacks.reply(access.requester(), "You already have a kit request in progress.");
            }
            return true;
        }

        callbacks.reply(access.requester(), "You already have a kit request queued. Please wait for it to finish before requesting another.");
        return true;
    }

    private boolean requestIsAmbiguous(String requester, KitCommand command) {
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            callbacks.reply(requester, "Failed to read stash cache.");
            callbacks.warning("Failed to read stash inventory cache before queueing request: %s.", exception.getMessage());
            return true;
        }

        Map<String, Integer> inventoryCounts = inventory.matchingKitCounts(command.name(), command.quotedSearch());
        StashKitbotStockPlanner.KitResolution resolution = stockPlanner.resolveKit(cacheFile, command.name(), command.quotedSearch(), inventoryCounts);
        if (!resolution.ambiguous()) return false;

        replyAmbiguousChoices(requester, command, resolution.choices());
        return true;
    }

    private void replyAmbiguousChoices(String requester, KitCommand command, List<SelectedKit> choices) {
        callbacks.reply(requester, stockPlanner.ambiguousChoicesMessage(command.name(), command.count(), choices));
    }

    interface Callbacks {
        void reply(String player, String message);

        void info(String message, Object... args);

        void warning(String message, Object... args);

        void savePersistentQueueState();

        boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued);

        void handleCooldownMessage(String message);
    }

    record Settings(
        String tier1Nicknames,
        int tier1CooldownTicks,
        String tier2Nicknames,
        int tier2CooldownTicks,
        String tier2KitWhitelist,
        int maxQueuedRequests,
        int maxRequestCount
    ) {
    }
}
