package com.watchmenbot.modules.stash;

import com.watchmenbot.WMBot;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.List;
import java.util.Locale;

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
            lifecycleWorkflow.failRequest(message);
        }

        @Override
        public void delivered(KitRequest request) {
            statsWorkflow.delivered(request, statsSettings());
        }
    };
    private final StashKitbotStatsWorkflow statsWorkflow = new StashKitbotStatsWorkflow(
        mc,
        session,
        stats,
        discord,
        new StashKitbotStatsWorkflow.Callbacks() {
            @Override
            public void warning(String message, Object... args) {
                StashKitbot.this.warning(message, args);
            }
        }
    );
    private final StashKitbotAcceptanceWorkflow acceptanceWorkflow = new StashKitbotAcceptanceWorkflow(
        mc,
        session,
        cache,
        inventory,
        stockPlanner,
        new StashKitbotAcceptanceWorkflow.Callbacks() {
            @Override
            public void reply(String player, String message) {
                StashKitbot.this.reply(player, message);
            }

            @Override
            public void queueReply(String player, String message) {
                StashKitbot.this.queueReply(player, message);
            }

            @Override
            public void info(String message, Object... args) {
                StashKitbot.this.info(message, args);
            }

            @Override
            public void warning(String message, Object... args) {
                StashKitbot.this.warning(message, args);
            }
        }
    );
    private final StashKitbotQueueWorkflow queueWorkflow = new StashKitbotQueueWorkflow(
        mc,
        session,
        inventory,
        new StashKitbotQueueWorkflow.Callbacks() {
            @Override
            public void reply(String player, String message) {
                StashKitbot.this.reply(player, message);
            }

            @Override
            public void info(String message, Object... args) {
                StashKitbot.this.info(message, args);
            }

            @Override
            public void warning(String message, Object... args) {
                StashKitbot.this.warning(message, args);
            }

            @Override
            public boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued) {
                return acceptanceWorkflow.startRequest(access, command, queued);
            }
        }
    );
    private final StashKitbotLifecycleWorkflow lifecycleWorkflow = new StashKitbotLifecycleWorkflow(
        mc,
        session,
        navigator,
        safetyGuard,
        delivery,
        messenger,
        queueWorkflow,
        new StashKitbotLifecycleWorkflow.Callbacks() {
            @Override
            public void reply(String player, String message) {
                StashKitbot.this.reply(player, message);
            }

            @Override
            public void warning(String message, Object... args) {
                StashKitbot.this.warning(message, args);
            }
        }
    );
    private final StashKitbotRequestWorkflow requestWorkflow = new StashKitbotRequestWorkflow(
        mc,
        session,
        cache,
        inventory,
        requestParser,
        stockPlanner,
        cooldowns,
        new StashKitbotRequestWorkflow.Callbacks() {
            @Override
            public void reply(String player, String message) {
                StashKitbot.this.reply(player, message);
            }

            @Override
            public void info(String message, Object... args) {
                StashKitbot.this.info(message, args);
            }

            @Override
            public void warning(String message, Object... args) {
                StashKitbot.this.warning(message, args);
            }

            @Override
            public void savePersistentQueueState() {
                queueWorkflow.savePersistentQueueState();
            }

            @Override
            public boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued) {
                return acceptanceWorkflow.startRequest(access, command, queued);
            }

            @Override
            public void handleCooldownMessage(String message) {
                StashKitbot.this.handleCooldownMessage(message);
            }
        }
    );
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
        lifecycleWorkflow.activate();
    }

    @Override
    public void onDeactivate() {
        lifecycleWorkflow.deactivate();
    }

    @Override
    public String getInfoString() {
        return session.infoString();
    }

    public KitbotStatsSnapshot statsSnapshot() {
        return statsWorkflow.snapshot(isActive());
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        queueWorkflow.loadPersistentQueueState();

        String message = event.getMessage().getString();
        requestWorkflow.handleMessage(message, requestSettings());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        queueWorkflow.loadPersistentQueueState();

        if (!session.hasActiveRequest()) {
            if (canWork() && queueWorkflow.tryResumeDelivery(queueSettings())) return;
            if (canWork()) queueWorkflow.promoteQueuedRequests();
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
            case FAILED, DONE -> lifecycleWorkflow.finishRequest();
        }

        tracePhaseTransition(beforePhase, beforeRequest);
        if (!session.hasActiveRequest()) queueWorkflow.promoteQueuedRequests();
        messenger.tickWhisperQueue(replyCommand.get());
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!session.hasActiveRequest()) return;

        if (safetyGuard.cancelDestroyPacket(event)) {
            safetyGuard.cancelBreaking(mc);
            navigator.stop();
            lifecycleWorkflow.failRequest("Stopped because a block-breaking packet was cancelled.");
        }
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

    private StashKitbotRequestWorkflow.Settings requestSettings() {
        return new StashKitbotRequestWorkflow.Settings(
            tier1Nicknames.get(),
            tier1CooldownTicks.get(),
            tier2Nicknames.get(),
            tier2CooldownTicks.get(),
            tier2KitWhitelist.get(),
            maxQueuedRequests.get(),
            maxRequestCount.get()
        );
    }

    private StashKitbotQueueWorkflow.Settings queueSettings() {
        return new StashKitbotQueueWorkflow.Settings(requesterReacquireTimeoutTicks.get());
    }

    private StashKitbotStatsWorkflow.Settings statsSettings() {
        return new StashKitbotStatsWorkflow.Settings(
            discordWebhook.get(),
            discordWebhookUrl.get()
        );
    }

    private boolean canWork() {
        return StashClientUtils.canUse(mc);
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
