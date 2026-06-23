package com.watchmenbot.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.watchmenbot.util.AtomicJsonFile;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StashKitbotStats {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SCHEMA_VERSION = 1;

    private long completedDeliveries;
    private long deliveredShulkers;
    private final Map<String, Long> requesterShulkers = new LinkedHashMap<>();
    private boolean loaded;

    synchronized PersistentStats snapshot(MinecraftClient mc) {
        load(mc);

        return snapshot(new CounterState(completedDeliveries, deliveredShulkers, requesterShulkers));
    }

    synchronized void recordDelivery(MinecraftClient mc, KitRequest request) {
        if (request == null || request.requester == null || request.requester.isBlank()) return;

        load(mc);
        CounterState next = recordDelivery(
            new CounterState(completedDeliveries, deliveredShulkers, requesterShulkers),
            request.requester,
            request.delivery.delivered
        );

        completedDeliveries = next.completedDeliveries();
        deliveredShulkers = next.deliveredShulkers();
        requesterShulkers.clear();
        requesterShulkers.putAll(next.requesterShulkers());
        write(mc);
    }

    private void load(MinecraftClient mc) {
        if (loaded) return;

        Path path = statsPath(mc);
        if (path == null) return;

        CounterState state = read(path);
        completedDeliveries = state.completedDeliveries();
        deliveredShulkers = state.deliveredShulkers();
        requesterShulkers.clear();
        requesterShulkers.putAll(state.requesterShulkers());
        loaded = true;
    }

    private void write(MinecraftClient mc) {
        Path path = statsPath(mc);
        if (path == null) return;

        try {
            write(path, new CounterState(completedDeliveries, deliveredShulkers, requesterShulkers));
        }
        catch (IOException ignored) {
            // HUD stats should never interrupt kit delivery.
        }
    }

    private static Path statsPath(MinecraftClient mc) {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("watchmenbot").resolve("stash_kitbot_stats.json");
    }

    static CounterState recordDelivery(CounterState state, String requester, long shulkersDelivered) {
        if (state == null) state = new CounterState(0, 0, Map.of());
        long sanitizedShulkers = Math.max(0, shulkersDelivered);
        Map<String, Long> requesterTotals = new LinkedHashMap<>(state.requesterShulkers());
        requesterTotals.merge(requester, sanitizedShulkers, Long::sum);

        return new CounterState(
            Math.max(0, state.completedDeliveries()) + 1,
            Math.max(0, state.deliveredShulkers()) + sanitizedShulkers,
            requesterTotals
        );
    }

    static PersistentStats snapshot(CounterState state) {
        CounterState sanitized = sanitize(state);
        List<StashKitbot.RequesterDeliveryCount> topRequesters = sanitized.requesterShulkers().entrySet().stream()
            .map(entry -> new StashKitbot.RequesterDeliveryCount(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(StashKitbot.RequesterDeliveryCount::shulkersDelivered).reversed().thenComparing(StashKitbot.RequesterDeliveryCount::requester))
            .limit(5)
            .toList();

        return new PersistentStats(sanitized.completedDeliveries(), sanitized.deliveredShulkers(), topRequesters);
    }

    static CounterState read(Path path) {
        return AtomicJsonFile.readIfExists(
            path,
            new CounterState(0, 0, Map.of()),
            reader -> sanitize(GSON.fromJson(reader, StatsFile.class))
        ).value();
    }

    static CounterState fromJson(String json) {
        if (json == null || json.isBlank()) return new CounterState(0, 0, Map.of());

        try {
            return sanitize(GSON.fromJson(json, StatsFile.class));
        }
        catch (Exception ignored) {
            return new CounterState(0, 0, Map.of());
        }
    }

    static void write(Path path, CounterState state) throws IOException {
        CounterState sanitized = sanitize(state);
        AtomicJsonFile.write(path, writer -> {
            GSON.toJson(new StatsFile(SCHEMA_VERSION, sanitized.completedDeliveries(), sanitized.deliveredShulkers(), sanitized.requesterShulkers()), writer);
        });
    }

    private static CounterState sanitize(StatsFile stats) {
        if (stats == null) return new CounterState(0, 0, Map.of());
        return sanitize(new CounterState(stats.completedDeliveries(), stats.deliveredShulkers(), stats.requesterShulkers()));
    }

    private static CounterState sanitize(CounterState state) {
        if (state == null) return new CounterState(0, 0, Map.of());

        Map<String, Long> requesterTotals = new LinkedHashMap<>();
        if (state.requesterShulkers() != null) {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(state.requesterShulkers().entrySet());
            entries.sort(Comparator.comparing(entry -> entry.getKey() == null ? "" : entry.getKey()));
            for (Map.Entry<String, Long> entry : entries) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
                requesterTotals.put(entry.getKey(), Math.max(0, entry.getValue()));
            }
        }

        return new CounterState(
            Math.max(0, state.completedDeliveries()),
            Math.max(0, state.deliveredShulkers()),
            requesterTotals
        );
    }

    record PersistentStats(long completedDeliveries, long deliveredShulkers, List<StashKitbot.RequesterDeliveryCount> topRequesters) {
    }

    record CounterState(long completedDeliveries, long deliveredShulkers, Map<String, Long> requesterShulkers) {
    }

    private record StatsFile(int schemaVersion, long completedDeliveries, long deliveredShulkers, Map<String, Long> requesterShulkers) {
    }
}
