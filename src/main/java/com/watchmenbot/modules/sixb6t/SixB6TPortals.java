package com.watchmenbot.modules.sixb6t;

import com.watchmenbot.WMBot;
import com.watchmenbot.util.TickTimer;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

public class SixB6TPortals extends Module {
    private final SettingGroup sgPortalChain = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Boolean> autoStart = sgPortalChain.add(new BoolSetting.Builder()
        .name("auto-start")
        .description("Allows 6b6t Login to start lobby portal walking after login.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> portalCount = sgPortalChain.add(new IntSetting.Builder()
        .name("portal-count")
        .description("How many lobby portals to walk through after logging in.")
        .defaultValue(2)
        .range(1, 5)
        .sliderRange(1, 3)
        .build()
    );

    private final Setting<Integer> portalRadius = sgPortalChain.add(new IntSetting.Builder()
        .name("portal-radius")
        .description("How far to scan for lobby portal blocks.")
        .defaultValue(24)
        .range(4, 64)
        .sliderRange(4, 48)
        .build()
    );

    private final Setting<Integer> nextPortalDelay = sgTiming.add(new IntSetting.Builder()
        .name("next-portal-delay")
        .description("Ticks to wait after entering a portal before looking for the next lobby portal.")
        .defaultValue(40)
        .range(0, 400)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Integer> portalTimeout = sgTiming.add(new IntSetting.Builder()
        .name("portal-timeout")
        .description("Ticks to keep trying to walk into the lobby portal.")
        .defaultValue(400)
        .range(20, 1200)
        .sliderRange(20, 600)
        .build()
    );

    private final PortalWalker walker = new PortalWalker(mc);

    public SixB6TPortals() {
        super(WMBot.CATEGORY, "6b6t-portals", "Walks through 6b6t lobby portals after login.");
    }

    public static void requestAutoStart(int delayTicks) {
        SixB6TPortals module = Modules.get().get(SixB6TPortals.class);
        if (module != null) module.requestStart(delayTicks);
    }

    @Override
    public void onActivate() {
        if (SixB6TSession.shouldPreservePortalState(portalCount.get())) {
            ensureNextPortalQueued();
            return;
        }

        SixB6TSession.resetPortals();
        walker.stop();
    }

    @Override
    public void onDeactivate() {
        if (!SixB6TSession.shouldPreservePortalState(portalCount.get())) {
            SixB6TSession.resetPortals();
        }

        walker.stop();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        stopCurrentWalk();

        if (!SixB6TSession.portalChainStarted() || SixB6TSession.portalsEntered() >= portalCount.get()) return;

        if (SixB6TSession.portalsEntered() > 0) {
            scheduleNextPortalWalk();
        }
        else {
            schedulePortalWalk(nextPortalDelay.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickPortalStart();
        tickPendingNextPortal();
        ensureNextPortalQueued();
        tickPortalWalk();
    }

    private void requestStart(int delayTicks) {
        if (!isActive() || !autoStart.get()) return;

        schedulePortalWalk(delayTicks);
    }

    private void tickPortalStart() {
        if (SixB6TSession.portalStartTicks() < 0) return;

        if (TickTimer.tickDelay(SixB6TSession::portalStartTicks, SixB6TSession::portalStartTicks)) return;

        SixB6TSession.portalStartTicks(SixB6TSession.INACTIVE_TICKS);
        startPortalWalk();
    }

    private void schedulePortalWalk(int delayTicks) {
        if (!canSchedulePortal()) return;

        SixB6TSession.portalChainStarted(true);
        int currentDelay = SixB6TSession.portalStartTicks();
        SixB6TSession.portalStartTicks(currentDelay < 0 ? delayTicks : Math.min(currentDelay, delayTicks));
    }

    private void scheduleNextPortalWalk() {
        if (!canSchedulePortal()) return;

        SixB6TSession.portalChainStarted(true);
        SixB6TSession.pendingNextPortal(true);
        SixB6TSession.nextPortalTicks(nextPortalDelay.get());
        SixB6TSession.portalStartTicks(SixB6TSession.INACTIVE_TICKS);
        info("Queued lobby portal %d/%d.", SixB6TSession.portalsEntered() + 1, portalCount.get());
    }

    private boolean canSchedulePortal() {
        return isActive()
            && autoStart.get()
            && SixB6TSession.portalsEntered() < portalCount.get()
            && !SixB6TSession.pendingNextPortal()
            && SixB6TSession.portalTicksLeft() < 0;
    }

    private void tickPendingNextPortal() {
        if (!SixB6TSession.pendingNextPortal() || SixB6TSession.portalsEntered() >= portalCount.get()) return;

        if (!walker.canWalk()) {
            walker.pause();
            return;
        }

        if (TickTimer.tickDelay(SixB6TSession::nextPortalTicks, SixB6TSession::nextPortalTicks)) return;

        SixB6TSession.pendingNextPortal(false);
        startPortalWalk();
    }

    private void startPortalWalk() {
        if (!isActive() || !autoStart.get() || SixB6TSession.portalsEntered() >= portalCount.get()) return;

        SixB6TSession.portalChainStarted(true);
        SixB6TSession.portalTicksLeft(portalTimeout.get());
        walker.stop();
        info("Looking for lobby portal %d/%d.", SixB6TSession.portalsEntered() + 1, portalCount.get());
    }

    private void tickPortalWalk() {
        if (SixB6TSession.portalTicksLeft() < 0) return;

        if (!walker.canWalk()) {
            walker.pause();
            return;
        }

        if (SixB6TSession.portalTicksLeft() <= 0) {
            warning("Stopped looking for lobby portal.");
            stopCurrentWalk();
            return;
        }

        SixB6TSession.portalTicksLeft(SixB6TSession.portalTicksLeft() - 1);

        if (walker.isInsidePortal()) {
            finishPortalWalk();
            return;
        }

        boolean hadTarget = walker.hasTarget();
        if (!walker.updateTarget(portalRadius.get())) {
            walker.pause();
            return;
        }

        if (!hadTarget) {
            info("Walking to lobby portal %d/%d.", SixB6TSession.portalsEntered() + 1, portalCount.get());
        }

        walker.walkToTarget();
    }

    private void finishPortalWalk() {
        SixB6TSession.incrementPortalsEntered();
        info("Entered lobby portal %d/%d.", SixB6TSession.portalsEntered(), portalCount.get());
        stopCurrentWalk();

        if (SixB6TSession.portalsEntered() < portalCount.get()) {
            scheduleNextPortalWalk();
        }
        else {
            info("Finished lobby portals.");
        }
    }

    private void stopCurrentWalk() {
        SixB6TSession.portalTicksLeft(SixB6TSession.INACTIVE_TICKS);
        walker.stop();
    }

    private void ensureNextPortalQueued() {
        if (!isActive()
            || !autoStart.get()
            || !SixB6TSession.portalChainStarted()
            || SixB6TSession.portalsEntered() <= 0
            || SixB6TSession.portalsEntered() >= portalCount.get()
            || SixB6TSession.pendingNextPortal()
            || SixB6TSession.portalStartTicks() >= 0
            || SixB6TSession.portalTicksLeft() >= 0) {
            return;
        }

        scheduleNextPortalWalk();
    }
}
