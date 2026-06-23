package com.watchmenbot.modules.stash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class StashKitbotAccessPlanner {
    static final String TIER_2_DEFAULT_KIT = "echest";

    private StashKitbotAccessPlanner() {
    }

    static Optional<KitbotRequesterAccess> resolveAccess(
        String requester,
        String tier1Nicknames,
        int tier1CooldownTicks,
        String tier2Nicknames,
        int tier2CooldownTicks
    ) {
        String normalizedRequester = normalize(requester);
        if (normalizedRequester.isEmpty()) return Optional.empty();

        List<String> tier1 = splitList(tier1Nicknames);
        for (int i = 0; i < tier1.size(); i++) {
            if (tier1.get(i).equals(normalizedRequester)) {
                return Optional.of(new KitbotRequesterAccess(requester, normalizedRequester, KitbotTier.TIER_1, sanitizeCooldown(tier1CooldownTicks)));
            }
        }

        List<String> tier2 = splitList(tier2Nicknames);
        for (int i = 0; i < tier2.size(); i++) {
            if (tier2.get(i).equals(normalizedRequester)) {
                return Optional.of(new KitbotRequesterAccess(requester, normalizedRequester, KitbotTier.TIER_2, sanitizeCooldown(tier2CooldownTicks)));
            }
        }

        return Optional.empty();
    }

    static boolean tier2KitAllowed(String query, String whitelist) {
        String normalizedQuery = normalize(query);
        return !normalizedQuery.isEmpty() && splitSet(whitelist).contains(normalizedQuery);
    }

    static boolean requestAllowed(KitbotRequesterAccess access, KitCommand command, String tier2Whitelist) {
        if (access == null || command == null) return false;
        if (access.tier() == KitbotTier.TIER_1) return true;
        return access.tier() == KitbotTier.TIER_2 && tier2KitAllowed(command.name(), tier2Whitelist);
    }

    static long cooldownExpiryMillis(long nowMillis, int cooldownTicks) {
        return nowMillis + Math.max(0, cooldownTicks) * 50L;
    }

    static boolean shouldStartCooldown(boolean requestAccepted, int cooldownTicks) {
        return requestAccepted && cooldownTicks > 0;
    }

    static long remainingCooldownMillis(long nowMillis, long expiresAtMillis) {
        return Math.max(0L, expiresAtMillis - nowMillis);
    }

    static String formatCooldown(long remainingMillis) {
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long leftoverSeconds = seconds % 60L;
        if (minutes <= 0L) return "%ds".formatted(seconds);
        if (leftoverSeconds == 0L) return "%dm".formatted(minutes);
        return "%dm %ds".formatted(minutes, leftoverSeconds);
    }

    static List<String> splitList(String value) {
        List<String> entries = new ArrayList<>();
        if (value == null || value.isBlank()) return entries;

        for (String part : value.split(",")) {
            String normalized = normalize(part);
            if (!normalized.isEmpty()) entries.add(normalized);
        }

        return entries;
    }

    static Set<String> splitSet(String value) {
        return new HashSet<>(splitList(value));
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int sanitizeCooldown(int cooldownTicks) {
        return Math.max(0, cooldownTicks);
    }
}
