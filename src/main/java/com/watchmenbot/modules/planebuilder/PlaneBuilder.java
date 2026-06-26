package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.WMBot;
import com.watchmenbot.util.BaritoneCompatibility;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.NbtCompound;

public class PlaneBuilder extends Module {
    private static final int WORLD_READY_RESUME_TICKS = 3;

    private final SettingGroup sgCompanionModules = settings.createGroup("Companion Modules");
    private final SettingGroup sgReplenish = settings.createGroup("Replenish");
    private final SettingGroup sgBowDefense = settings.createGroup("Bow Defense");
    private final SettingGroup sgRecovery = settings.createGroup("Recovery");
    private final SettingGroup sgAutoWalk = settings.createGroup("Auto Walk");
    private final SettingGroup sgKitbotRefill = settings.createGroup("Kitbot Refill");
    private final SettingGroup sgPickaxeSafety = settings.createGroup("Pickaxe Safety");
    private final SettingGroup sgEndermanSafety = settings.createGroup("Enderman Safety");
    private final CompanionModuleManager companionModules = new CompanionModuleManager(
        PlaneBuilderSettings.companionModules(sgCompanionModules)
    );
    private final PlaneModuleIsolationSession moduleIsolation = new PlaneModuleIsolationSession();
    private final PlaneNametagsTeardownGuard nametagsTeardownGuard = new PlaneNametagsTeardownGuard();
    private final PlaneBuilderCoordinator coordinator = new PlaneBuilderCoordinator(
        companionModules,
        PlaneBuilderSettings.replenish(sgReplenish),
        PlaneBuilderSettings.bowDefense(sgBowDefense),
        PlaneBuilderSettings.autoWalk(sgAutoWalk),
        PlaneBuilderSettings.holeEscape(sgRecovery),
        PlaneBuilderSettings.kitbotRefill(sgKitbotRefill),
        PlaneBuilderSettings.pickaxeSafety(sgPickaxeSafety),
        PlaneBuilderSettings.endermanLookSafety(sgEndermanSafety),
        PlaneRuntimeConfig.DEFAULT,
        new PlaneClientContext(),
        new com.watchmenbot.util.WorkflowLogger() {
            @Override
            public void info(String message, Object... args) {
                PlaneBuilder.this.info(message, args);
            }

            @Override
            public void warning(String message, Object... args) {
                PlaneBuilder.this.warning(message, args);
            }
        }
    );
    private int worldReadyTicks;
    private boolean missingBaritoneWarningShown;

    public PlaneBuilder() {
        super(WMBot.CATEGORY, "plane-builder", "Builds an obsidian plane and replenishes from ender chests.");
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        tag.putBoolean("active", false);
        return super.fromTag(tag);
    }

    @Override
    public void onActivate() {
        moduleIsolation.start(this);
        coordinator.reset();
        coordinator.startStatsSession(System.currentTimeMillis());
        worldReadyTicks = 0;
        warnMissingBaritoneIfNeeded();
    }

    @Override
    public void onDeactivate() {
        coordinator.reset();
        worldReadyTicks = 0;
        companionModules.restore();
        nametagsTeardownGuard.restore();
        moduleIsolation.restore(this);
    }

    @Override
    public String getInfoString() {
        return null;
    }

    public String hudStatus() {
        if (!isActive()) return "inactive";
        if (mc.player == null || mc.world == null) return "waiting for world";

        return coordinator.phase().label();
    }

    public StatsSnapshot statsSnapshot() {
        long nowMillis = System.currentTimeMillis();
        String status;
        if (!isActive()) status = "inactive";
        else {
            coordinator.startStatsSession(nowMillis);
            if (mc.player == null || mc.world == null) status = "waiting for world";
            else status = "active";
        }

        PlaneBuilderStats.Snapshot stats = coordinator.statsSnapshot(nowMillis);
        return new StatsSnapshot(
            isActive(),
            status,
            coordinator.phase().label(),
            stats.runtimeMillis(),
            stats.placedThisSession(),
            stats.perSecond(),
            stats.perMinute(),
            stats.perHour()
        );
    }

    public record StatsSnapshot(
        boolean active,
        String status,
        String phase,
        long runtimeMillis,
        long placedThisSession,
        double perSecond,
        double perMinute,
        double perHour
    ) {
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        nametagsTeardownGuard.suspend();
        coordinator.reset();
        worldReadyTicks = 0;
        companionModules.suspend();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        nametagsTeardownGuard.restore();
        worldReadyTicks = 0;
        if (isActive()) coordinator.startStatsSession(System.currentTimeMillis());
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        PlaneKitbotTeleportAcceptWorkflow.AcceptResult accept = coordinator.handleMessage(message);
        if (accept.accepted()) {
            logTeleportAcceptResult(accept);
            return;
        }

        PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignored = coordinator.ignoredTeleportPrompt(message);
        if (ignored != null) {
            warning(
                "Ignored kitbot teleport prompt from %s because kitbot-nickname is configured as %s.",
                ignored.requester(),
                ignored.configuredNickname()
            );
        }
    }

    private void logTeleportAcceptResult(PlaneKitbotTeleportAcceptWorkflow.AcceptResult accept) {
        switch (accept.status()) {
            case ARMED -> info("Armed proactive kitbot teleport accept retry: %s", accept.command());
            case QUEUED -> info("Queued kitbot teleport accept from prompt for next tick: %s", accept.command());
            case SENT -> {
                if (accept.source() == PlaneKitbotTeleportAcceptWorkflow.AcceptSource.PROACTIVE) {
                    info("Sent proactive kitbot teleport accept retry: %s", accept.command());
                } else {
                    info("Sent kitbot teleport accept from prompt: %s", accept.command());
                }
            }
            case FAILED_CLIENT_NOT_READY -> warning("Kitbot teleport accept could not be sent.");
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            nametagsTeardownGuard.suspend();
            coordinator.reset();
            worldReadyTicks = 0;
            companionModules.suspend();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            coordinator.reset();
            worldReadyTicks = 0;
            companionModules.suspend();
            return;
        }

        if (worldReadyTicks < WORLD_READY_RESUME_TICKS) {
            worldReadyTicks++;
            if (worldReadyTicks < WORLD_READY_RESUME_TICKS) return;
        }

        companionModules.resume();
        warnMissingBaritoneIfNeeded();
        coordinator.tick();
        logTeleportAcceptResult(coordinator.consumeTeleportAcceptResult());
    }

    private void warnMissingBaritoneIfNeeded() {
        if (BaritoneCompatibility.available() || missingBaritoneWarningShown) return;

        warning(BaritoneCompatibility.missingMessage());
        missingBaritoneWarningShown = true;
    }
}
