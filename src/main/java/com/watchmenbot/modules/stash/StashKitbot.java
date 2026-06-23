package com.watchmenbot.modules.stash;

import com.watchmenbot.WMBot;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class StashKitbot extends Module {
    private final SettingGroup sgRequest = settings.getDefaultGroup();
    private final SettingGroup sgGather = settings.createGroup("Gather");
    private final SettingGroup sgDelivery = settings.createGroup("Delivery");
    private final SettingGroup sgCooldowns = settings.createGroup("Cooldowns");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    private final StashKitbotSettings kitbotSettings = StashKitbotSettings.create(sgRequest, sgGather, sgDelivery, sgCooldowns, sgDebug);
    private final Setting<String> tier1Nicknames = kitbotSettings.tier1Nicknames();
    private final Setting<Integer> tier1CooldownTicks = kitbotSettings.tier1CooldownTicks();
    private final Setting<String> tier2Nicknames = kitbotSettings.tier2Nicknames();
    private final Setting<Integer> tier2CooldownTicks = kitbotSettings.tier2CooldownTicks();
    private final Setting<String> tier2KitWhitelist = kitbotSettings.tier2KitWhitelist();
    private final Setting<String> replyCommand = kitbotSettings.replyCommand();
    private final Setting<Integer> maxQueuedRequests = kitbotSettings.maxQueuedRequests();
    private final Setting<Integer> interactionRange = kitbotSettings.interactionRange();
    private final Setting<Integer> pathTimeoutTicks = kitbotSettings.pathTimeoutTicks();
    private final Setting<Integer> openTimeoutTicks = kitbotSettings.openTimeoutTicks();
    private final Setting<Integer> maxRequestCount = kitbotSettings.maxRequestCount();
    private final Setting<String> tpaCommand = kitbotSettings.tpaCommand();
    private final Setting<String> homeCommand = kitbotSettings.homeCommand();
    private final Setting<Integer> deliveryDistance = kitbotSettings.deliveryDistance();
    private final Setting<Integer> teleportTimeoutTicks = kitbotSettings.teleportTimeoutTicks();
    private final Setting<Integer> deliveryTimeoutTicks = kitbotSettings.deliveryTimeoutTicks();
    private final Setting<Integer> requesterReacquireTimeoutTicks = kitbotSettings.requesterReacquireTimeoutTicks();
    private final Setting<Integer> requesterSearchRadius = kitbotSettings.requesterSearchRadius();
    private final Setting<Integer> throwDelayTicks = kitbotSettings.throwDelayTicks();
    private final Setting<Integer> deliveryLookPitch = kitbotSettings.deliveryLookPitch();
    private final Setting<Boolean> discordWebhook = kitbotSettings.discordWebhook();
    private final Setting<String> discordWebhookUrl = kitbotSettings.discordWebhookUrl();
    private final Setting<Integer> fallbackCooldownTicks = kitbotSettings.fallbackCooldownTicks();
    private final Setting<Boolean> traceLogs = kitbotSettings.traceLogs();

    private StashKitbotQueueState.DeliveryResume pendingDeliveryResume;
    private boolean persistentQueueLoaded;

    private final StashInventoryCache cache = new StashInventoryCache();
    private final StashTargetDiscovery discovery = new StashTargetDiscovery();
    private final StashNavigator navigator = new StashNavigator();
    private final StashContainerReader reader = new StashContainerReader();
    private final StashSafetyGuard safetyGuard = new StashSafetyGuard();
    private final StashContainerInteractor interactor = new StashContainerInteractor(mc, reader);
    private final StashKitbotRequestParser requestParser = new StashKitbotRequestParser();
    private final StashKitbotStockPlanner stockPlanner = new StashKitbotStockPlanner();
    private final StashKitbotMessenger messenger = new StashKitbotMessenger(mc);
    private final StashKitbotDelivery delivery = new StashKitbotDelivery(mc);
    private final StashKitbotInventory inventory = new StashKitbotInventory(mc);
    private final StashKitbotDiscordWebhook discord = new StashKitbotDiscordWebhook();
    private final StashKitbotStats stats = new StashKitbotStats();
    private final StashKitbotCooldowns cooldowns = new StashKitbotCooldowns();
    private final StashKitbotSession session = new StashKitbotSession();
    private final StashKitbotEvents kitbotEvents = new StashKitbotEvents() {
        @Override
        public void info(String message, Object... args) {
            StashKitbot.this.info(message, args);
        }

        @Override
        public void warning(String message, Object... args) {
            StashKitbot.this.warning(message, args);
        }

        @Override
        public void reply(String player, String message) {
            StashKitbot.this.reply(player, message);
        }

        @Override
        public void failRequest(String message) {
            StashKitbot.this.failRequest(message);
        }

        @Override
        public void delivered(KitRequest request) {
            StashKitbot.this.recordDeliveryStats(request);
            StashKitbot.this.notifyDiscordDelivery(request);
        }
    };
    private final StashKitbotGatherWorkflow gatherWorkflow = new StashKitbotGatherWorkflow(
        mc,
        session,
        discovery,
        navigator,
        interactor,
        reader,
        cache,
        inventory,
        kitbotEvents
    );
    private final StashKitbotDeliveryWorkflow deliveryWorkflow = new StashKitbotDeliveryWorkflow(
        mc,
        session,
        navigator,
        messenger,
        delivery,
        inventory,
        kitbotEvents
    );

    public StashKitbot() {
        super(WMBot.CATEGORY, "stash-kitbot", "Gathers requested shulker kits from the stash inventory cache after allowlisted whispers.");
    }

    @Override
    public void onActivate() {
        persistentQueueLoaded = false;
        pendingDeliveryResume = null;
    }

    @Override
    public void onDeactivate() {
        KitRequest request = session.activeRequest();
        if (request != null && session.isDeliveryPhase() && request.delivery.commands.tpaSent) {
            pendingDeliveryResume = StashKitbotQueueState.resumeFromRequest(request);
            if (pendingDeliveryResume != null) {
                info("Saved cross-server delivery resume for %d x '%s' to %s.", request.count, request.kitName, request.requester);
            }
        }

        savePersistentQueueState();
        delivery.stopWalking();
        navigator.stop();
        safetyGuard.restore();
        safetyGuard.cancelBreaking(mc);
        StashClientUtils.closeContainerScreen(mc);
        session.clearRequest();
        session.clearQueuedRequests();
        persistentQueueLoaded = false;
    }

    @Override
    public String getInfoString() {
        return session.infoString();
    }

    public KitbotStatsSnapshot statsSnapshot() {
        StashKitbotStats.PersistentStats persistent = stats.snapshot(mc);
        KitRequest request = session.activeRequest();
        CurrentRequest currentRequest = request == null ? null : new CurrentRequest(
            request.requester,
            request.kitName,
            request.count,
            request.gather.gathered,
            request.delivery.delivered
        );

        return new KitbotStatsSnapshot(
            isActive(),
            session.phase().name().toLowerCase(Locale.ROOT),
            currentRequest,
            session.queuedCount(),
            persistent.completedDeliveries(),
            persistent.deliveredShulkers(),
            persistent.topRequesters()
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        loadPersistentQueueState();

        String message = event.getMessage().getString();
        handleCooldownMessage(message);

        Whisper whisper = requestParser.parseWhisper(message);
        if (whisper == null) return;

        Optional<KitbotRequesterAccess> access = StashKitbotAccessPlanner.resolveAccess(
            whisper.sender(),
            tier1Nicknames.get(),
            tier1CooldownTicks.get(),
            tier2Nicknames.get(),
            tier2CooldownTicks.get()
        );
        if (access.isEmpty()) return;

        KitRequestIntent intent = requestParser.parseIntent(whisper.body());
        if (intent.isListKits()) {
            replyAvailableKits(access.get());
            return;
        }

        KitCommand command = requestParser.parseCommand(
            whisper.body(),
            access.get().tier() == KitbotTier.TIER_2,
            StashKitbotAccessPlanner.TIER_2_DEFAULT_KIT
        );
        if (command == null) return;

        if (command.count() <= 0) {
            reply(whisper.sender(), "Invalid kit count.");
            return;
        }

        if (command.count() > maxRequestCount.get()) {
            reply(whisper.sender(), "Request too large. Max is %d shulkers.".formatted(maxRequestCount.get()));
            return;
        }

        if (!StashKitbotAccessPlanner.requestAllowed(access.get(), command, tier2KitWhitelist.get())) {
            reply(whisper.sender(), "That kit is not available for your tier.");
            return;
        }

        long remainingCooldownMillis = cooldowns.remainingMillis(mc, access.get());
        if (remainingCooldownMillis > 0L) {
            reply(whisper.sender(), "Please wait %s before requesting another kit.".formatted(StashKitbotAccessPlanner.formatCooldown(remainingCooldownMillis)));
            return;
        }

        if (rejectDuplicateRequest(access.get())) return;

        if (StashKitbotQueuePlanner.shouldQueue(session.hasActiveRequest(), session.queuedCount())) {
            enqueueRequest(access.get(), command);
            return;
        }

        if (startRequest(access.get(), command, false)) cooldowns.start(mc, access.get());
    }

    private void replyAvailableKits(KitbotRequesterAccess access) {
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            reply(access.requester(), "Failed to read stash cache.");
            warning("Failed to read stash inventory cache before listing kits: %s.", exception.getMessage());
            return;
        }

        List<String> kitNames = stockPlanner.availableKitNames(cacheFile, inventory.allKitCounts(), access, tier2KitWhitelist.get());
        for (String message : stockPlanner.kitListMessages(kitNames)) {
            reply(access.requester(), message);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        loadPersistentQueueState();

        if (!session.hasActiveRequest()) {
            if (canWork() && pendingDeliveryResume != null) {
                tryResumeDelivery();
                return;
            }
            if (canWork()) promoteQueuedRequests();
            if (session.hasActiveRequest() && session.phase() == KitbotPhase.TPA_REQUEST && !session.activeRequest().delivery.commands.tpaSent) {
                return;
            }
            messenger.tickWhisperQueue(replyCommand.get());
            return;
        }
        if (!canWork()) return;

        safetyGuard.apply();
        safetyGuard.cancelBreaking(mc);
        messenger.tickPendingCommandWindow();
        if (session.isDeliveryPhase()) lookDown();

        KitbotPhase beforePhase = session.phase();
        KitRequest beforeRequest = session.activeRequest();
        switch (beforePhase) {
            case IDLE, PATHING, OPENING, TAKING, VERIFYING_TRANSFER -> gatherWorkflow.tick(gatherSettings());
            case TPA_REQUEST, WAITING_FOR_TPY, REACQUIRE_REQUESTER, MOVE_TO_DELIVERY_SPOT, THROWING, PREPARED_THROW, HOME_REQUEST, HOME_COOLDOWN, HOME_CONFIRM, RETURN_TO_ORIGIN -> deliveryWorkflow.tick(deliverySettings());
            case FAILED, DONE -> finishRequest();
        }

        tracePhaseTransition(beforePhase, beforeRequest);
        if (!session.hasActiveRequest()) promoteQueuedRequests();
        messenger.tickWhisperQueue(replyCommand.get());
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!session.hasActiveRequest()) return;

        if (safetyGuard.cancelDestroyPacket(event)) {
            safetyGuard.cancelBreaking(mc);
            navigator.stop();
            failRequest("Stopped because a block-breaking packet was cancelled.");
        }
    }

    private void enqueueRequest(KitbotRequesterAccess access, KitCommand command) {
        if (requestIsAmbiguous(access.requester(), command)) return;

        int queuedCount = session.queuedCount();
        int maxQueued = maxQueuedRequests.get();
        if (!StashKitbotQueuePlanner.canEnqueue(maxQueued, queuedCount)) {
            if (maxQueued <= 0) {
                KitRequest activeRequest = session.activeRequest();
                if (activeRequest != null) {
                    reply(access.requester(), "Busy gathering %s for %s: %d/%d collected.".formatted(
                        activeRequest.kitName,
                        activeRequest.requester,
                        activeRequest.gather.gathered,
                        activeRequest.count
                    ));
                }
                else {
                    reply(access.requester(), "Busy. Request queueing is disabled.");
                }
            }
            else {
                reply(access.requester(), "Request queue is full (%d waiting). Please try again later.".formatted(queuedCount));
            }
            return;
        }

        int position = StashKitbotQueuePlanner.queuePosition(queuedCount);
        session.enqueue(new QueuedKitRequest(access, command));
        savePersistentQueueState();
        cooldowns.start(mc, access);
        reply(access.requester(), "Queued '%s' x%d. Position %d/%d.".formatted(command.name(), command.count(), position, maxQueued));
        info("Queued stash kit request from %s: %s x%d at position %d/%d.", access.requester(), command.name(), command.count(), position, maxQueued);
    }

    private boolean rejectDuplicateRequest(KitbotRequesterAccess access) {
        RequesterRequestStatus status = session.requestStatus(access.normalizedRequester());
        if (status == RequesterRequestStatus.NONE) return false;

        if (status == RequesterRequestStatus.ACTIVE) {
            KitRequest activeRequest = session.activeRequest();
            if (activeRequest != null) {
                reply(access.requester(), "You already have a kit request in progress: '%s' x%d.".formatted(activeRequest.kitName, activeRequest.count));
            }
            else {
                reply(access.requester(), "You already have a kit request in progress.");
            }
            return true;
        }

        reply(access.requester(), "You already have a kit request queued. Please wait for it to finish before requesting another.");
        return true;
    }

    private void promoteQueuedRequests() {
        while (!session.hasActiveRequest()) {
            QueuedKitRequest queued = session.pollNextQueuedRequest();
            if (queued == null) return;

            savePersistentQueueState();
            info("Starting queued stash kit request from %s: %s x%d.", queued.access().requester(), queued.command().name(), queued.command().count());
            startRequest(queued.access(), queued.command(), true);
        }
    }

    private boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued) {
        String requester = access.requester();
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            reply(requester, "Failed to read stash cache.");
            warning("Failed to read stash inventory cache: %s.", exception.getMessage());
            return false;
        }

        Map<String, Integer> inventoryCounts = inventory.matchingKitCounts(command.name(), command.quotedSearch());
        if ((cacheFile == null || cacheFile.containers() == null || cacheFile.containers().isEmpty()) && inventoryCounts.isEmpty()) {
            reply(requester, "No stash inventory cache is available.");
            return false;
        }

        StashKitbotStockPlanner.KitResolution resolution = stockPlanner.resolveKit(cacheFile, command.name(), command.quotedSearch(), inventoryCounts);
        if (resolution.ambiguous()) {
            replyAmbiguousChoices(requester, command, resolution.choices());
            return false;
        }
        if (!resolution.hasSelection()) {
            reply(requester, "No shulkers match '%s' in inventory or cache.".formatted(command.name()));
            return false;
        }

        SelectedKit kit = resolution.selected();
        if (kit.totalCount() < command.count()) {
            reply(requester, "Only %d '%s' shulkers are available across inventory and cache.".formatted(kit.totalCount(), kit.name()));
            return false;
        }

        int initialInventoryCount = inventory.matchingInventoryCount(kit.alias());
        int inventoryMatches = Math.min(command.count(), initialInventoryCount);
        int remainingToGather = command.count() - inventoryMatches;
        if (inventory.emptyInventorySlots() < remainingToGather) {
            reply(requester, "Inventory needs %d empty slots for '%s'.".formatted(remainingToGather, kit.name()));
            return false;
        }

        List<KitSource> sources = stockPlanner.buildSources(cacheFile, kit.alias());
        if (remainingToGather > 0 && sources.isEmpty()) {
            reply(requester, "No source containers found for '%s'.".formatted(kit.name()));
            return false;
        }

        sources.sort(Comparator.comparingDouble(source -> StashClientUtils.playerBlockDistanceSq(mc, source.interactionPos())));
        KitRequest request = new KitRequest(
            requester,
            kit.name(),
            kit.alias(),
            command.count(),
            sources,
            mc.player.getBlockPos().toImmutable()
        );
        request.gather.initialInventoryCount = initialInventoryCount;
        request.gather.gathered = StashKitbotGatherPlanner.confirmedGathered(command.count(), initialInventoryCount, initialInventoryCount);
        session.startRequest(request);

        String prefix = queued ? "Starting queued " : "Accepted ";
        if (inventoryMatches > 0 && remainingToGather > 0) {
            reply(requester, "%s'%s' x%d. Using %d already in inventory; gathering %d more.".formatted(prefix, kit.name(), command.count(), inventoryMatches, remainingToGather));
        }
        else if (inventoryMatches >= command.count()) {
            session.phase(KitbotPhase.TPA_REQUEST);
            queueReply(requester, "%s'%s' x%d. I already have it in inventory; sending TPA now.".formatted(prefix, kit.name(), command.count()));
            info("%s'%s' x%d from current inventory for %s. Queued acceptance whisper after TPA to avoid command cooldown.", prefix, kit.name(), command.count(), requester);
        }
        else {
            reply(requester, "%s'%s' x%d. Gathering now.".formatted(prefix, kit.name(), command.count()));
        }
        info("Accepted stash kit request from %s: %s x%d (%d already in inventory, %d to gather).", requester, kit.name(), command.count(), inventoryMatches, remainingToGather);
        return true;
    }

    private boolean requestIsAmbiguous(String requester, KitCommand command) {
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            reply(requester, "Failed to read stash cache.");
            warning("Failed to read stash inventory cache before queueing request: %s.", exception.getMessage());
            return true;
        }

        Map<String, Integer> inventoryCounts = inventory.matchingKitCounts(command.name(), command.quotedSearch());
        StashKitbotStockPlanner.KitResolution resolution = stockPlanner.resolveKit(cacheFile, command.name(), command.quotedSearch(), inventoryCounts);
        if (!resolution.ambiguous()) return false;

        replyAmbiguousChoices(requester, command, resolution.choices());
        return true;
    }

    private void replyAmbiguousChoices(String requester, KitCommand command, List<SelectedKit> choices) {
        reply(requester, stockPlanner.ambiguousChoicesMessage(command.name(), command.count(), choices));
    }

    private void tryResumeDelivery() {
        StashKitbotQueueState.DeliveryResume resume = pendingDeliveryResume;
        pendingDeliveryResume = null;
        savePersistentQueueState();

        String currentDimension = StashClientUtils.dimensionId(mc);
        boolean dimensionChanged = StashKitbotDeliveryPlanner.dimensionChangedAfterTpa(resume.preTpaDimension(), currentDimension);
        if (!dimensionChanged) {
            reply(resume.requester(), "Delivery resume cancelled for '%s': I am still in the same dimension.".formatted(resume.kitName()));
            info("Cross-server delivery resume cancelled for '%s': still in same dimension (%s).", resume.kitName(), currentDimension);
            return;
        }

        String resumeAlias = StashKitNameNormalizer.alias(resume.kitName());
        int inventoryCount = inventory.matchingInventoryCount(resumeAlias);
        if (inventoryCount <= 0) {
            reply(resume.requester(), "Delivery resume cancelled for '%s': I do not have the kits in inventory anymore.".formatted(resume.kitName()));
            info("Cross-server delivery resume cancelled for '%s': no items found in inventory.", resume.kitName());
            return;
        }

        int gathered = Math.min(resume.gatheredCount(), inventoryCount);
        if (gathered <= 0) {
            reply(resume.requester(), "Delivery resume cancelled for '%s': no gathered kits remain in inventory.".formatted(resume.kitName()));
            info("Cross-server delivery resume cancelled for '%s': no gathered items remain in inventory.", resume.kitName());
            return;
        }

        KitRequest request = new KitRequest(resume.requester(), resume.kitName(), resumeAlias, resume.requestedCount(), java.util.List.of(), null);
        request.gather.initialInventoryCount = 0;
        request.gather.gathered = gathered;
        request.delivery.commands.tpaSent = true;
        request.delivery.preTpaDimension = resume.preTpaDimension();
        request.delivery.crossDimensionDelivery = true;
        request.delivery.crossDimensionSettleTicks = 40;
        request.delivery.requesterReacquire.reset(requesterReacquireTimeoutTicks.get());

        session.startRequest(request);
        session.phase(KitbotPhase.REACQUIRE_REQUESTER);
        info("Resuming cross-server delivery after dimension change: %d '%s' to %s in %s.", request.gather.gathered, resume.kitName(), resume.requester(), currentDimension);
    }

    private void failRequest(String message) {
        StashClientUtils.closeContainerScreen(mc);
        navigator.stop();
        KitRequest activeRequest = session.activeRequest();
        if (activeRequest != null) reply(activeRequest.requester, message);
        warning(message);
        session.phase(KitbotPhase.FAILED);
    }

    private void finishRequest() {
        delivery.stopWalking();
        safetyGuard.restore();
        messenger.clearCommand();
        session.clearRequest();
        savePersistentQueueState();
    }

    private void recordDeliveryStats(KitRequest request) {
        stats.recordDelivery(mc, request);
    }

    private void notifyDiscordDelivery(KitRequest request) {
        if (!discordWebhook.get()) return;

        String url = discordWebhookUrl.get();
        if (url == null || url.isBlank()) {
            warning("Discord webhook is enabled but no webhook URL is configured.");
            return;
        }

        discord.send(url, request).whenComplete((sent, exception) -> {
            if (exception != null) {
                warning("Failed to send Discord delivery webhook: %s.", exception.getMessage());
            }
            else if (!sent) {
                warning("Discord delivery webhook returned a non-success status.");
            }
        });
    }

    private void handleCooldownMessage(String message) {
        deliveryWorkflow.handleCooldownMessage(message, deliverySettings());
    }

    private void lookDown() {
        delivery.lookDown(deliveryLookPitch.get());
    }

    private void reply(String player, String message) {
        messenger.reply(player, message, replyCommand.get());
    }

    private void queueReply(String player, String message) {
        messenger.queueReply(player, message);
    }

    private void tracePhaseTransition(KitbotPhase beforePhase, KitRequest request) {
        KitbotPhase phase = session.phase();
        if (!traceLogs.get() || phase == beforePhase || request == null) return;

        info("[kitbot trace] %s -> %s for %s/%s gathered=%d/%d delivered=%d.",
            beforePhase.name().toLowerCase(Locale.ROOT),
            phase.name().toLowerCase(Locale.ROOT),
            request.requester,
            request.kitName,
            request.gather.gathered,
            request.count,
            request.delivery.delivered
        );
    }

    private StashKitbotGatherWorkflow.Settings gatherSettings() {
        return new StashKitbotGatherWorkflow.Settings(
            interactionRange.get(),
            pathTimeoutTicks.get(),
            openTimeoutTicks.get(),
            deliveryTimeoutTicks.get()
        );
    }

    private StashKitbotDeliveryWorkflow.Settings deliverySettings() {
        return new StashKitbotDeliveryWorkflow.Settings(
            tpaCommand.get(),
            homeCommand.get(),
            deliveryDistance.get(),
            teleportTimeoutTicks.get(),
            deliveryTimeoutTicks.get(),
            requesterReacquireTimeoutTicks.get(),
            requesterSearchRadius.get(),
            throwDelayTicks.get(),
            deliveryLookPitch.get(),
            fallbackCooldownTicks.get(),
            traceLogs.get()
        );
    }

    private boolean canWork() {
        return StashClientUtils.canUse(mc);
    }

    private void loadPersistentQueueState() {
        if (persistentQueueLoaded) return;
        if (mc == null || mc.runDirectory == null) return;

        StashKitbotQueueState.LoadResult result = StashKitbotQueueState.load(mc);
        StashKitbotQueueState.State state = result.state();
        if (result.failed()) warning("Failed to read stash kitbot queue state; starting with an empty persisted queue.");

        if (!session.hasActiveRequest() && session.queuedCount() == 0) {
            session.replaceQueuedRequests(state.queuedRequests());
        }
        if (pendingDeliveryResume == null) pendingDeliveryResume = state.deliveryResume();

        persistentQueueLoaded = true;
        if (session.queuedCount() > 0 || pendingDeliveryResume != null) {
            info("Loaded persisted stash kitbot state: %d queued request%s%s.",
                session.queuedCount(),
                session.queuedCount() == 1 ? "" : "s",
                pendingDeliveryResume == null ? "" : " and a pending delivery resume"
            );
        }
    }

    private void savePersistentQueueState() {
        try {
            StashKitbotQueueState.write(
                mc,
                new StashKitbotQueueState.State(session.queuedRequestsSnapshot(), pendingDeliveryResume)
            );
        }
        catch (IOException exception) {
            warning("Failed to write stash kitbot queue state: %s.", exception.getMessage());
        }
    }

    public record KitbotStatsSnapshot(
        boolean active,
        String phase,
        CurrentRequest currentRequest,
        int queuedCount,
        long completedDeliveries,
        long deliveredShulkers,
        List<RequesterDeliveryCount> topRequesters
    ) {
    }

    public record CurrentRequest(
        String requester,
        String kitName,
        int requestedCount,
        int gatheredCount,
        int deliveredCount
    ) {
    }

    public record RequesterDeliveryCount(String requester, long shulkersDelivered) {
    }
}
