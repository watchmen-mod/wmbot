package com.watchmenbot.modules.stash;

import com.watchmenbot.WMBot;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.time.Instant;
import java.util.List;

public class StashScanner extends Module {
    private final SettingGroup sgDiscovery = settings.getDefaultGroup();
    private final SettingGroup sgPathing = settings.createGroup("Pathing");
    private final SettingGroup sgSafetyCache = settings.createGroup("Safety / Cache");

    private final Setting<Integer> scanRadius = sgDiscovery.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("How far from the player to queue loaded stash containers.")
        .defaultValue(64)
        .range(8, 256)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Integer> rescanIntervalTicks = sgDiscovery.add(new IntSetting.Builder()
        .name("rescan-interval-ticks")
        .description("Ticks between loaded-container discovery passes.")
        .defaultValue(20)
        .range(1, 200)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> skipShulkers = sgDiscovery.add(new BoolSetting.Builder()
        .name("skip-shulkers")
        .description("Skips placed shulker boxes as scan targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> openTimeoutTicks = sgPathing.add(new IntSetting.Builder()
        .name("open-timeout-ticks")
        .description("Ticks to wait for a container screen after interacting.")
        .defaultValue(80)
        .range(10, 400)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<Boolean> baritonePathing = sgPathing.add(new BoolSetting.Builder()
        .name("baritone-pathing")
        .description("Uses Baritone to walk near containers. Protected by no-break packet cancellation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pathTimeoutTicks = sgPathing.add(new IntSetting.Builder()
        .name("path-timeout-ticks")
        .description("Ticks to allow safe Baritone pathing toward one container.")
        .defaultValue(600)
        .range(40, 2400)
        .sliderRange(40, 1200)
        .build()
    );

    private final Setting<Boolean> returnToStart = sgPathing.add(new BoolSetting.Builder()
        .name("return-to-start")
        .description("Returns to the position where scanning started after the scan finishes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> returnTimeoutTicks = sgPathing.add(new IntSetting.Builder()
        .name("return-timeout-ticks")
        .description("Ticks to allow returning to the scan start position.")
        .defaultValue(1200)
        .range(40, 4800)
        .sliderRange(40, 2400)
        .build()
    );

    private final Setting<Integer> interactionRange = sgPathing.add(new IntSetting.Builder()
        .name("interaction-range")
        .description("Maximum distance used before opening a container.")
        .defaultValue(4)
        .range(2, 6)
        .sliderRange(2, 6)
        .build()
    );

    private final Setting<Boolean> writeCacheEachContainer = sgSafetyCache.add(new BoolSetting.Builder()
        .name("write-cache-each-container")
        .description("Writes the cache after each scanned, skipped, or failed container.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearCache = sgSafetyCache.add(new BoolSetting.Builder()
        .name("clear-cache")
        .description("Momentary toggle that deletes the stash inventory cache and clears loaded scanner state.")
        .defaultValue(false)
        .onChanged(enabled -> {
            if (!enabled) return;

            clearCache();
        })
        .build()
    );

    private final StashScanSession session = new StashScanSession();
    private final StashTargetDiscovery discovery = new StashTargetDiscovery();
    private final StashNavigator navigator = new StashNavigator();
    private final StashSafetyGuard safetyGuard = new StashSafetyGuard();
    private final StashContainerReader reader = new StashContainerReader();
    private final StashContainerInteractor interactor = new StashContainerInteractor(mc, reader);
    private final StashInventoryCache cache = new StashInventoryCache();
    private final StashScannerEvents scannerEvents = new StashScannerEvents() {
        @Override
        public void info(String message, Object... args) {
            StashScanner.this.info(message, args);
        }

        @Override
        public void warning(String message, Object... args) {
            StashScanner.this.warning(message, args);
        }

        @Override
        public void writeCache(Instant finishedAt) {
            scannerCache.write(finishedAt);
        }

        @Override
        public void stopModule() {
            toggle();
        }
    };
    private final StashScannerCache scannerCache = new StashScannerCache(mc, session, cache, scannerEvents);
    private final StashScannerWorkflow workflow = new StashScannerWorkflow(mc, session, discovery, navigator, safetyGuard, reader, interactor, scannerEvents);

    public StashScanner() {
        super(WMBot.CATEGORY, "stash-scanner", "Safely scans stash storage and writes a lookup cache.");
        scannerCache.load(false);
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        tag.putBoolean("active", false);
        return super.fromTag(tag);
    }

    @Override
    public void onActivate() {
        if (!canScan()) {
            info("Stash scanner waits for manual activation after joining a server.");
            toggle();
            return;
        }

        session.reset(mc.player == null ? null : mc.player.getBlockPos());
        scannerCache.load(true);
        safetyGuard.apply();

        int discovered = discoverAndQueueTargets();
        if (discovered == 0) warnNoTargets();

        info("Safe mode active: block breaking and placing are blocked while scanning.");
        scannerCache.write(null);
    }

    @Override
    public void onDeactivate() {
        navigator.stop();
        safetyGuard.restore();
        safetyGuard.cancelBreaking(mc);
        StashClientUtils.closeContainerScreen(mc);
        scannerCache.write(Instant.now());
        session.clearCurrent();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        info("Disabling stash scanner on join. Re-enable it manually when ready to scan.");
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!canScan()) return;

        safetyGuard.apply();
        safetyGuard.cancelBreaking(mc);
        if (session.markSafeModeWarningShown()) {
            warning("Stash scanner safe mode: destroy-block packets are cancelled while active.");
        }

        tickDiscovery();
        workflow.tick(scannerSettings());
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;

        if (safetyGuard.cancelDestroyPacket(event)) {
            safetyGuard.cancelBreaking(mc);
            navigator.stop();
            warning("Cancelled a block-breaking packet during stash scan.");
        }
    }

    @Override
    public String getInfoString() {
        return "%d/%d".formatted(
            session.scannedCount(),
            session.scannedCount() + session.queuedCount() + (session.current() == null ? 0 : 1)
        );
    }

    public InventoryStats statsSnapshot() {
        return StashScannerStats.snapshot(session, isActive());
    }

    private boolean canScan() {
        return StashClientUtils.canUse(mc);
    }

    private void tickDiscovery() {
        if (!session.tickDiscoveryDelay()) return;

        session.discoveryDelay(rescanIntervalTicks.get());
        int discovered = discoverAndQueueTargets();
        if (discovered > 0) {
            session.clearNoTargetsWarning();
        }
        else if (session.shouldWarnNoTargets()) {
            warning("No stash containers found in loaded chunks within %d blocks.", scanRadius.get());
        }
    }

    private int discoverAndQueueTargets() {
        return workflow.discoverAndQueueTargets(scanRadius.get(), baritonePathing.get(), interactionRangeValue(), skipShulkers.get());
    }

    private void warnNoTargets() {
        if (baritonePathing.get()) warning("No stash containers found in loaded chunks within %d blocks.", scanRadius.get());
        else warning("No reachable stash containers found within %.1f blocks.", interactionRangeValue());
        session.markNoTargetsWarningShown();
    }

    private void clearCache() {
        navigator.stop();
        StashClientUtils.closeContainerScreen(mc);
        scannerCache.clear();
        clearCache.set(false);
    }

    private double interactionRangeValue() {
        return interactionRange.get() + 0.5;
    }

    private StashScannerWorkflow.Settings scannerSettings() {
        return new StashScannerWorkflow.Settings(
            interactionRange.get(),
            openTimeoutTicks.get(),
            baritonePathing.get(),
            pathTimeoutTicks.get(),
            returnToStart.get(),
            returnTimeoutTicks.get(),
            writeCacheEachContainer.get()
        );
    }

    public record InventoryStats(
        boolean active,
        String phase,
        int containersSearched,
        int containersQueued,
        int containersCurrent,
        int containersSkipped,
        int stacksSeen,
        List<ShulkerNameCount> topShulkers
    ) {
    }

    public record ShulkerNameCount(String name, int count) {
    }
}
