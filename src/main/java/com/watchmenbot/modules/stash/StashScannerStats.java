package com.watchmenbot.modules.stash;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StashScannerStats {
    private static CachedSnapshot cachedSnapshot;

    private StashScannerStats() {
    }

    static StashScanner.InventoryStats snapshot(StashScanSession session, boolean active) {
        long contentVersion = session.contentVersion();
        String phase = session.phase().name().toLowerCase();
        int scannedCount = session.scannedCount();
        int queuedCount = session.queuedCount();
        int currentCount = session.current() == null ? 0 : 1;
        int skippedCount = session.skippedCount();
        CachedSnapshot cached = cachedSnapshot;
        if (cached != null
            && cached.session == session
            && cached.contentVersion == contentVersion
            && cached.active == active
            && cached.phase.equals(phase)
            && cached.scannedCount == scannedCount
            && cached.queuedCount == queuedCount
            && cached.currentCount == currentCount
            && cached.skippedCount == skippedCount) {
            return cached.stats;
        }

        Map<String, Integer> shulkerCounts = new LinkedHashMap<>();
        int stacksSeen = 0;

        for (StashCachedContainer container : session.containers()) {
            for (StashCachedItem item : container.items()) {
                stacksSeen++;
                if (!item.isShulkerBox()) continue;

                String alias = StashKitNameNormalizer.canonicalAlias(item.displayName());
                if (alias.isEmpty()) alias = item.displayName();
                shulkerCounts.merge(alias, item.count(), Integer::sum);
            }
        }

        List<StashScanner.ShulkerNameCount> topShulkers = shulkerCounts.entrySet().stream()
            .map(entry -> new StashScanner.ShulkerNameCount(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(StashScanner.ShulkerNameCount::count).reversed().thenComparing(StashScanner.ShulkerNameCount::name))
            .limit(5)
            .toList();

        StashScanner.InventoryStats stats = new StashScanner.InventoryStats(
            active,
            phase,
            scannedCount,
            queuedCount,
            currentCount,
            skippedCount,
            stacksSeen,
            topShulkers
        );
        cachedSnapshot = new CachedSnapshot(
            session,
            contentVersion,
            active,
            phase,
            scannedCount,
            queuedCount,
            currentCount,
            skippedCount,
            stats
        );
        return stats;
    }

    private record CachedSnapshot(
        StashScanSession session,
        long contentVersion,
        boolean active,
        String phase,
        int scannedCount,
        int queuedCount,
        int currentCount,
        int skippedCount,
        StashScanner.InventoryStats stats
    ) {
    }
}
