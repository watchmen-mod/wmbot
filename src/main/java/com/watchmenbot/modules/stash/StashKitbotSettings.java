package com.watchmenbot.modules.stash;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;

record StashKitbotSettings(
    Setting<String> tier1Nicknames,
    Setting<Integer> tier1CooldownTicks,
    Setting<String> tier2Nicknames,
    Setting<Integer> tier2CooldownTicks,
    Setting<String> tier2KitWhitelist,
    Setting<String> replyCommand,
    Setting<Integer> maxQueuedRequests,
    Setting<Integer> interactionRange,
    Setting<Integer> pathTimeoutTicks,
    Setting<Integer> openTimeoutTicks,
    Setting<Integer> maxRequestCount,
    Setting<String> tpaCommand,
    Setting<StashKitbotReturnCommand.ReturnMethod> returnMethod,
    Setting<String> homeCommand,
    Setting<String> customReturnCommand,
    Setting<Integer> deliveryDistance,
    Setting<Integer> teleportTimeoutTicks,
    Setting<Integer> deliveryTimeoutTicks,
    Setting<Integer> requesterReacquireTimeoutTicks,
    Setting<Integer> requesterSearchRadius,
    Setting<Integer> throwDelayTicks,
    Setting<Integer> deliveryLookPitch,
    Setting<Boolean> discordWebhook,
    Setting<String> discordWebhookUrl,
    Setting<Integer> fallbackCooldownTicks,
    Setting<Boolean> traceLogs
) {
    static StashKitbotSettings create(
        SettingGroup request,
        SettingGroup gather,
        SettingGroup delivery,
        SettingGroup cooldowns,
        SettingGroup debug
    ) {
        Setting<String> tier1Nicknames = request.add(new StringSetting.Builder()
            .name("tier-1-nicknames")
            .description("Comma-separated trusted requesters allowed to request any kit.")
            .defaultValue("")
            .placeholder("PlayerOne, BotTwo")
            .build()
        );

        Setting<Integer> tier1CooldownTicks = request.add(new IntSetting.Builder()
            .name("tier-1-cooldown-ticks")
            .description("Cooldown in ticks for each tier 1 requester, tracked separately per user.")
            .defaultValue(0)
            .range(0, 12000)
            .sliderRange(0, 3600)
            .build()
        );

        Setting<String> tier2Nicknames = request.add(new StringSetting.Builder()
            .name("tier-2-nicknames")
            .description("Comma-separated limited requesters allowed to request whitelisted kit aliases.")
            .defaultValue("")
            .placeholder("PlayerThree, PlayerFour")
            .build()
        );

        Setting<Integer> tier2CooldownTicks = request.add(new IntSetting.Builder()
            .name("tier-2-cooldown-ticks")
            .description("Cooldown in ticks for each tier 2 requester, tracked separately per user.")
            .defaultValue(0)
            .range(0, 12000)
            .sliderRange(0, 3600)
            .build()
        );

        Setting<String> tier2KitWhitelist = request.add(new StringSetting.Builder()
            .name("tier-2-kit-whitelist")
            .description("Comma-separated kit request aliases accepted from tier 2 requesters.")
            .defaultValue(StashKitbotAccessPlanner.TIER_2_DEFAULT_KIT)
            .placeholder("echest")
            .build()
        );

        Setting<String> replyCommand = request.add(new StringSetting.Builder()
            .name("reply-command")
            .description("Command used to reply to requesters.")
            .defaultValue("/w")
            .placeholder("/w")
            .build()
        );

        Setting<Integer> maxQueuedRequests = request.add(new IntSetting.Builder()
            .name("max-queued-requests")
            .description("Maximum valid kit requests kept waiting while the kitbot is busy. Set to 0 to reject busy requests.")
            .defaultValue(10)
            .range(0, 50)
            .sliderRange(0, 50)
            .build()
        );

        Setting<Integer> interactionRange = gather.add(new IntSetting.Builder()
            .name("interaction-range")
            .description("Maximum distance used before opening a source container.")
            .defaultValue(4)
            .range(2, 6)
            .sliderRange(2, 6)
            .build()
        );

        Setting<Integer> pathTimeoutTicks = gather.add(new IntSetting.Builder()
            .name("path-timeout-ticks")
            .description("Ticks to allow Baritone pathing toward one source container.")
            .defaultValue(600)
            .range(40, 2400)
            .sliderRange(40, 1200)
            .build()
        );

        Setting<Integer> openTimeoutTicks = gather.add(new IntSetting.Builder()
            .name("open-timeout-ticks")
            .description("Ticks to wait for a source container screen after interacting.")
            .defaultValue(80)
            .range(10, 400)
            .sliderRange(10, 200)
            .build()
        );

        Setting<Integer> maxRequestCount = request.add(new IntSetting.Builder()
            .name("max-request-count")
            .description("Largest shulker count accepted in one request.")
            .defaultValue(27)
            .range(1, 36)
            .sliderRange(1, 36)
            .build()
        );

        Setting<String> tpaCommand = delivery.add(new StringSetting.Builder()
            .name("tpa-command")
            .description("Command used to teleport to the requester.")
            .defaultValue("/tpa")
            .placeholder("/tpa")
            .build()
        );

        Setting<StashKitbotReturnCommand.ReturnMethod> returnMethod = delivery.add(new EnumSetting.Builder<StashKitbotReturnCommand.ReturnMethod>()
            .name("return-method")
            .description("How the kitbot returns after delivery. /kill is reliable only when spawn or bed is safely set.")
            .defaultValue(StashKitbotReturnCommand.ReturnMethod.KILL)
            .build()
        );

        Setting<String> homeCommand = delivery.add(new StringSetting.Builder()
            .name("home-command")
            .description("Home command used when return-method is HOME.")
            .defaultValue(StashKitbotReturnCommand.DEFAULT_HOME_COMMAND)
            .placeholder(StashKitbotReturnCommand.DEFAULT_HOME_COMMAND)
            .visible(() -> returnMethod.get() == StashKitbotReturnCommand.ReturnMethod.HOME)
            .build()
        );

        Setting<String> customReturnCommand = delivery.add(new StringSetting.Builder()
            .name("custom-return-command")
            .description("Custom return command used when return-method is CUSTOM.")
            .defaultValue("")
            .placeholder("/spawn")
            .visible(() -> returnMethod.get() == StashKitbotReturnCommand.ReturnMethod.CUSTOM)
            .build()
        );

        Setting<Integer> deliveryDistance = delivery.add(new IntSetting.Builder()
            .name("delivery-distance")
            .description("Preferred distance in blocks from the requester before throwing kits.")
            .defaultValue(5)
            .range(4, 12)
            .sliderRange(4, 10)
            .build()
        );

        Setting<Integer> teleportTimeoutTicks = delivery.add(new IntSetting.Builder()
            .name("teleport-timeout-ticks")
            .description("Ticks to wait for the requester to accept TPA.")
            .defaultValue(1200)
            .range(100, 6000)
            .sliderRange(200, 2400)
            .build()
        );

        Setting<Integer> deliveryTimeoutTicks = delivery.add(new IntSetting.Builder()
            .name("delivery-timeout-ticks")
            .description("Ticks allowed for delivery movement, throwing, or recovery pathing.")
            .defaultValue(1200)
            .range(100, 6000)
            .sliderRange(200, 2400)
            .build()
        );

        Setting<Integer> requesterReacquireTimeoutTicks = delivery.add(new IntSetting.Builder()
            .name("requester-reacquire-timeout-ticks")
            .description("Ticks to wait for the requester entity to load after a cross-dimension teleport.")
            .defaultValue(200)
            .range(20, 1200)
            .sliderRange(20, 600)
            .build()
        );

        Setting<Integer> requesterSearchRadius = delivery.add(new IntSetting.Builder()
            .name("requester-search-radius")
            .description("Maximum distance to look for the requester after teleport.")
            .defaultValue(32)
            .range(4, 128)
            .sliderRange(8, 64)
            .build()
        );

        Setting<Integer> throwDelayTicks = delivery.add(new IntSetting.Builder()
            .name("throw-delay-ticks")
            .description("Ticks between each delivered shulker drop.")
            .defaultValue(8)
            .range(1, 40)
            .sliderRange(1, 20)
            .build()
        );

        Setting<Integer> deliveryLookPitch = delivery.add(new IntSetting.Builder()
            .name("delivery-look-pitch")
            .description("Downward pitch used during delivery idle moments to reduce enderman aggro risk.")
            .defaultValue(80)
            .range(45, 90)
            .sliderRange(60, 90)
            .build()
        );

        Setting<Boolean> discordWebhook = delivery.add(new BoolSetting.Builder()
            .name("discord-webhook")
            .description("Sends a Discord webhook message when a kit delivery completes.")
            .defaultValue(false)
            .build()
        );

        Setting<String> discordWebhookUrl = delivery.add(new StringSetting.Builder()
            .name("discord-webhook-url")
            .description("Discord webhook URL for delivery notifications.")
            .defaultValue("")
            .placeholder("https://discord.com/api/webhooks/...")
            .build()
        );

        Setting<Integer> fallbackCooldownTicks = cooldowns.add(new IntSetting.Builder()
            .name("fallback-cooldown-ticks")
            .description("Retry delay when a cooldown message has no parseable duration.")
            .defaultValue(1200)
            .range(20, 12000)
            .sliderRange(200, 3600)
            .build()
        );

        Setting<Boolean> traceLogs = debug.add(new BoolSetting.Builder()
            .name("trace-logs")
            .description("Logs stash kitbot phase transitions and key state counts.")
            .defaultValue(false)
            .build()
        );

        return new StashKitbotSettings(
            tier1Nicknames,
            tier1CooldownTicks,
            tier2Nicknames,
            tier2CooldownTicks,
            tier2KitWhitelist,
            replyCommand,
            maxQueuedRequests,
            interactionRange,
            pathTimeoutTicks,
            openTimeoutTicks,
            maxRequestCount,
            tpaCommand,
            returnMethod,
            homeCommand,
            customReturnCommand,
            deliveryDistance,
            teleportTimeoutTicks,
            deliveryTimeoutTicks,
            requesterReacquireTimeoutTicks,
            requesterSearchRadius,
            throwDelayTicks,
            deliveryLookPitch,
            discordWebhook,
            discordWebhookUrl,
            fallbackCooldownTicks,
            traceLogs
        );
    }
}
