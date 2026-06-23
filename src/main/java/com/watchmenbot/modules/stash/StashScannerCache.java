package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.time.Instant;

final class StashScannerCache {
    private final MinecraftClient mc;
    private final StashScanSession session;
    private final StashInventoryCache cache;
    private final StashScannerEvents events;

    StashScannerCache(MinecraftClient mc, StashScanSession session, StashInventoryCache cache, StashScannerEvents events) {
        this.mc = mc;
        this.session = session;
        this.cache = cache;
        this.events = events;
    }

    void write(Instant finishedAt) {
        try {
            cache.write(mc, session, serverName(), StashClientUtils.dimensionId(mc), finishedAt);
        }
        catch (IOException exception) {
            events.warning("Failed to write stash inventory cache: %s.", exception.getMessage());
        }
    }

    void load(boolean notify) {
        try {
            CacheFile cacheFile = cache.read(mc);
            if (cacheFile == null) return;

            session.loadCached(cacheFile);
            if (notify) {
                events.info("Loaded %d cached stash container%s.", session.scannedCount(), session.scannedCount() == 1 ? "" : "s");
            }
        }
        catch (IOException exception) {
            if (notify) events.warning("Failed to load stash inventory cache: %s.", exception.getMessage());
        }
    }

    void clear() {
        try {
            boolean deleted = cache.delete(mc);
            session.reset(mc.player == null ? null : mc.player.getBlockPos());
            if (deleted) events.info("Cleared stash inventory cache.");
            else events.info("No stash inventory cache file existed; cleared loaded scanner state.");
        }
        catch (IOException exception) {
            events.warning("Failed to clear stash inventory cache: %s.", exception.getMessage());
        }
    }

    private String serverName() {
        if (mc.isInSingleplayer()) return "singleplayer";

        ServerInfo server = mc.getCurrentServerEntry();
        return server == null ? "unknown" : server.address;
    }
}
