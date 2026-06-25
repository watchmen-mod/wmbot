package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

final class StashKitbotStatsWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashKitbotStats stats;
    private final StashKitbotDiscordWebhook discord;
    private final Callbacks callbacks;

    StashKitbotStatsWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashKitbotStats stats,
        StashKitbotDiscordWebhook discord,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.stats = stats;
        this.discord = discord;
        this.callbacks = callbacks;
    }

    StashKitbot.KitbotStatsSnapshot snapshot(boolean active) {
        StashKitbotStats.PersistentStats persistent = stats.snapshot(mc);
        KitRequest request = session.activeRequest();
        StashKitbot.CurrentRequest currentRequest = request == null ? null : new StashKitbot.CurrentRequest(
            request.requester,
            request.kitName,
            request.count,
            request.gather.gathered,
            request.delivery.delivered
        );

        return new StashKitbot.KitbotStatsSnapshot(
            active,
            session.phase().name().toLowerCase(Locale.ROOT),
            currentRequest,
            session.queuedCount(),
            persistent.completedDeliveries(),
            persistent.deliveredShulkers(),
            persistent.topRequesters()
        );
    }

    void delivered(KitRequest request, Settings settings) {
        stats.recordDelivery(mc, request);
        notifyDiscordDelivery(request, settings);
    }

    private void notifyDiscordDelivery(KitRequest request, Settings settings) {
        if (!settings.discordWebhook()) return;

        String url = settings.discordWebhookUrl();
        if (url == null || url.isBlank()) {
            callbacks.warning("Discord webhook is enabled but no webhook URL is configured.");
            return;
        }

        discord.send(url, request).whenComplete((sent, exception) -> {
            if (exception != null) {
                callbacks.warning("Failed to send Discord delivery webhook: %s.", exception.getMessage());
            }
            else if (!sent) {
                callbacks.warning("Discord delivery webhook returned a non-success status.");
            }
        });
    }

    interface Callbacks {
        void warning(String message, Object... args);
    }

    record Settings(boolean discordWebhook, String discordWebhookUrl) {
    }
}
