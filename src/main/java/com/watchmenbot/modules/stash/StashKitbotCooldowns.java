package com.watchmenbot.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.watchmenbot.util.AtomicJsonFile;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StashKitbotCooldowns {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SCHEMA_VERSION = 1;

    private final Map<String, CooldownEntry> entries = new LinkedHashMap<>();
    private boolean loaded;

    synchronized long remainingMillis(MinecraftClient mc, KitbotRequesterAccess access) {
        load(mc);
        prune(Instant.now().toEpochMilli());
        if (access == null) return 0L;

        CooldownEntry entry = entries.get(access.normalizedRequester());
        if (entry == null || entry.tier() != access.tier()) return 0L;
        return StashKitbotAccessPlanner.remainingCooldownMillis(Instant.now().toEpochMilli(), entry.expiresAtMillis());
    }

    synchronized void start(MinecraftClient mc, KitbotRequesterAccess access) {
        if (!StashKitbotAccessPlanner.shouldStartCooldown(access != null, access == null ? 0 : access.cooldownTicks())) return;

        load(mc);
        long now = Instant.now().toEpochMilli();
        entries.put(access.normalizedRequester(), new CooldownEntry(access.tier(), StashKitbotAccessPlanner.cooldownExpiryMillis(now, access.cooldownTicks())));
        prune(now);
        write(mc);
    }

    private void load(MinecraftClient mc) {
        if (loaded) return;

        Path path = cooldownPath(mc);
        if (path == null) return;

        CooldownState state = read(path, Instant.now().toEpochMilli());
        entries.clear();
        entries.putAll(state.entries());
        loaded = true;
    }

    private void write(MinecraftClient mc) {
        Path path = cooldownPath(mc);
        if (path == null) return;

        try {
            write(path, new CooldownState(entries));
        }
        catch (IOException ignored) {
            // Cooldowns should not interrupt accepted kit delivery.
        }
    }

    private void prune(long nowMillis) {
        entries.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAtMillis() <= nowMillis);
    }

    private static Path cooldownPath(MinecraftClient mc) {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("watchmenbot").resolve("stash_kitbot_cooldowns.json");
    }

    static CooldownState read(Path path, long nowMillis) {
        return AtomicJsonFile.readIfExists(
            path,
            new CooldownState(Map.of()),
            reader -> sanitize(GSON.fromJson(reader, CooldownFile.class), nowMillis)
        ).value();
    }

    static CooldownState fromJson(String json, long nowMillis) {
        if (json == null || json.isBlank()) return new CooldownState(Map.of());

        try {
            return sanitize(GSON.fromJson(json, CooldownFile.class), nowMillis);
        }
        catch (Exception ignored) {
            return new CooldownState(Map.of());
        }
    }

    static void write(Path path, CooldownState state) throws IOException {
        CooldownState sanitized = sanitize(state, 0L);
        AtomicJsonFile.write(path, writer -> {
            GSON.toJson(new CooldownFile(SCHEMA_VERSION, sanitized.entries()), writer);
        });
    }

    private static CooldownState sanitize(CooldownFile file, long nowMillis) {
        if (file == null) return new CooldownState(Map.of());
        return sanitize(new CooldownState(file.cooldowns()), nowMillis);
    }

    private static CooldownState sanitize(CooldownState state, long nowMillis) {
        if (state == null || state.entries() == null) return new CooldownState(Map.of());

        Map<String, CooldownEntry> sanitized = new LinkedHashMap<>();
        List<Map.Entry<String, CooldownEntry>> rawEntries = new ArrayList<>(state.entries().entrySet());
        rawEntries.sort(Comparator.comparing(entry -> entry.getKey() == null ? "" : entry.getKey()));
        for (Map.Entry<String, CooldownEntry> raw : rawEntries) {
            String requester = StashKitbotAccessPlanner.normalize(raw.getKey());
            CooldownEntry entry = raw.getValue();
            if (requester.isEmpty() || entry == null || entry.tier() == null || entry.expiresAtMillis() <= nowMillis) continue;
            sanitized.put(requester, entry);
        }

        return new CooldownState(sanitized);
    }

    record CooldownState(Map<String, CooldownEntry> entries) {
    }

    record CooldownEntry(KitbotTier tier, long expiresAtMillis) {
    }

    private record CooldownFile(int schemaVersion, Map<String, CooldownEntry> cooldowns) {
    }
}
