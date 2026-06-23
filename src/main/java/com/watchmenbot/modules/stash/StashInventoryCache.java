package com.watchmenbot.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.watchmenbot.util.AtomicJsonFile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StashInventoryCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    CacheFile read(MinecraftClient mc) throws IOException {
        Path input = cachePath(mc);
        AtomicJsonFile.ReadResult<CacheFile> result = AtomicJsonFile.readIfExists(input, null, reader -> GSON.fromJson(reader, CacheFile.class));
        if (!result.failed()) return result.value();
        if (result.error() instanceof IOException exception) throw exception;
        throw new IOException("Failed to read stash inventory cache.", result.error());
    }

    void write(MinecraftClient mc, StashScanSession session, String serverName, String dimensionId, Instant finishedAt) throws IOException {
        if (mc.runDirectory == null) return;

        CacheFile cache = new CacheFile(
            1,
            Instant.now().toString(),
            serverName,
            dimensionId,
            new ScanSummary(
                session.scanStartedAt() == null ? Instant.now().toString() : session.scanStartedAt().toString(),
                finishedAt == null ? null : finishedAt.toString(),
                session.seenCount(),
                session.scannedCount(),
                session.skippedCount(),
                session.skippedCount()
            ),
            sortedContainers(session.containers()),
            buildItemIndex(session.containers()),
            session.skipped()
        );

        writeFile(mc, cache);
    }

    void updateContainer(MinecraftClient mc, StashCachedContainer updatedContainer) throws IOException {
        CacheFile existing = read(mc);
        if (existing == null) return;

        writeFile(mc, withUpdatedContainer(existing, updatedContainer, Instant.now()));
    }

    void depleteSourceSlot(MinecraftClient mc, String containerId, int slot, String kitAlias, int count) throws IOException {
        CacheFile existing = read(mc);
        if (existing == null) return;

        writeFile(mc, withDepletedSourceSlot(existing, containerId, slot, kitAlias, count, Instant.now()));
    }

    static CacheFile withUpdatedContainer(CacheFile existing, StashCachedContainer updatedContainer, Instant updatedAt) {
        if (existing == null || updatedContainer == null || updatedContainer.id() == null) return existing;

        List<StashCachedContainer> containers = new ArrayList<>();
        boolean replaced = false;
        if (existing.containers() != null) {
            for (StashCachedContainer container : existing.containers()) {
                if (container == null || container.id() == null) continue;

                if (container.id().equals(updatedContainer.id())) {
                    containers.add(updatedContainer);
                    replaced = true;
                }
                else {
                    containers.add(container);
                }
            }
        }

        if (!replaced) containers.add(updatedContainer);

        return new CacheFile(
            existing.schemaVersion(),
            updatedAt.toString(),
            existing.server(),
            existing.dimension(),
            existing.scan(),
            sortedContainers(containers),
            buildItemIndex(containers),
            existing.skipped()
        );
    }

    static CacheFile withDepletedSourceSlot(CacheFile existing, String containerId, int slot, String kitAlias, int count, Instant updatedAt) {
        if (existing == null || containerId == null || slot < 0 || count <= 0) return existing;
        if (existing.containers() == null) return existing;

        for (StashCachedContainer container : existing.containers()) {
            if (container == null || !containerId.equals(container.id()) || container.items() == null) continue;

            List<StashCachedItem> updatedItems = new ArrayList<>();
            boolean depleted = false;
            for (StashCachedItem item : container.items()) {
                if (!depleted && matchesDepletedSourceItem(item, slot, kitAlias)) {
                    int remaining = item.count() - count;
                    if (remaining > 0) updatedItems.add(withCount(item, remaining));
                    depleted = true;
                }
                else {
                    updatedItems.add(item);
                }
            }

            if (!depleted) return existing;

            StashCachedContainer updatedContainer = new StashCachedContainer(
                container.id(),
                container.type(),
                container.size(),
                container.positions(),
                container.interactionPos(),
                updatedAt.toString(),
                updatedItems
            );
            return withUpdatedContainer(existing, updatedContainer, updatedAt);
        }

        return existing;
    }

    private static boolean matchesDepletedSourceItem(StashCachedItem item, int slot, String kitAlias) {
        if (item == null || item.slot() != slot || !item.isShulkerBox()) return false;
        if (StashShulkerContentClassifier.isEchestAlias(kitAlias)) return item.pureEchestShulker();

        return StashKitNameNormalizer.matchesAliasKey(item.displayName(), kitAlias);
    }

    private static StashCachedItem withCount(StashCachedItem item, int count) {
        return new StashCachedItem(
            item.slot(),
            item.itemId(),
            item.displayName(),
            count,
            item.components(),
            item.isShulkerBox(),
            item.pureEchestShulker(),
            item.containedEnderChests()
        );
    }

    private void writeFile(MinecraftClient mc, CacheFile cache) throws IOException {
        Path output = cachePath(mc);
        if (output == null) return;

        AtomicJsonFile.write(output, writer -> GSON.toJson(cache, writer));
    }

    boolean delete(MinecraftClient mc) throws IOException {
        Path output = cachePath(mc);
        if (output == null) return false;

        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        boolean deleted = Files.deleteIfExists(output);
        Files.deleteIfExists(temp);
        return deleted;
    }

    private Path cachePath(MinecraftClient mc) {
        if (mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("watchmenbot").resolve("stash_inventory_cache.json");
    }

    static StashSkippedContainer skipped(StashTarget target, String reason) {
        return new StashSkippedContainer(target.id(), target.type(), positions(target.positions()), reason);
    }

    static List<PositionRecord> positions(List<BlockPos> positions) {
        return positions.stream().map(StashInventoryCache::position).toList();
    }

    static PositionRecord position(BlockPos pos) {
        return new PositionRecord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static List<StashCachedContainer> sortedContainers(List<StashCachedContainer> containers) {
        Map<String, StashCachedContainer> unique = new LinkedHashMap<>();
        for (StashCachedContainer container : containers) {
            if (container == null || container.id() == null) continue;
            unique.put(container.id(), container);
        }

        List<StashCachedContainer> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(StashCachedContainer::id));
        return sorted;
    }

    private static Map<String, ItemIndexEntry> buildItemIndex(List<StashCachedContainer> containers) {
        Map<String, ItemIndexEntry> index = new LinkedHashMap<>();

        for (StashCachedContainer container : sortedContainers(containers)) {
            if (container.items() == null) continue;

            for (StashCachedItem item : container.items()) {
                if (item == null) continue;

                ItemIndexEntry entry = index.computeIfAbsent(item.itemId(), id -> new ItemIndexEntry(item.displayName()));
                entry.totalCount += item.count();
                entry.locations.add(new ItemLocation(
                    container.id(),
                    container.type(),
                    container.positions(),
                    item.slot(),
                    item.count()
                ));
            }
        }

        return index;
    }
}

record CacheFile(
    int schemaVersion,
    String updatedAt,
    String server,
    String dimension,
    ScanSummary scan,
    List<StashCachedContainer> containers,
    Map<String, ItemIndexEntry> itemIndex,
    List<StashSkippedContainer> skipped
) {
}

record ScanSummary(
    String startedAt,
    String finishedAt,
    int containersSeen,
    int containersScanned,
    int containersSkipped,
    int containersFailed
) {
}

record StashCachedContainer(
    String id,
    String type,
    int size,
    List<PositionRecord> positions,
    PositionRecord interactionPos,
    String scannedAt,
    List<StashCachedItem> items
) {
}

record StashCachedItem(
    int slot,
    String itemId,
    String displayName,
    int count,
    String components,
    boolean isShulkerBox,
    boolean pureEchestShulker,
    int containedEnderChests
) {
}

final class ItemIndexEntry {
    int totalCount;
    final String displayName;
    final List<ItemLocation> locations = new ArrayList<>();

    ItemIndexEntry(String displayName) {
        this.displayName = displayName;
    }
}

record ItemLocation(
    String containerId,
    String containerType,
    List<PositionRecord> positions,
    int slot,
    int count
) {
}

record StashSkippedContainer(
    String id,
    String type,
    List<PositionRecord> positions,
    String reason
) {
}

record PositionRecord(int x, int y, int z) {
}
