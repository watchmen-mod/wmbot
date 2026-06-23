package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StashKitbotStockPlanner {
    private static final int MAX_AMBIGUOUS_CHOICES = 5;
    static final int DEFAULT_KIT_LIST_MESSAGE_LENGTH = 180;
    private static final String EMPTY_KIT_LIST_MESSAGE = "No kits are currently available for your tier.";

    Optional<SelectedKit> selectKit(CacheFile cacheFile, String query) {
        return selectKit(cacheFile, query, false, Map.of());
    }

    Optional<SelectedKit> selectKit(CacheFile cacheFile, String query, Map<String, Integer> inventoryCounts) {
        return selectKit(cacheFile, query, false, inventoryCounts);
    }

    Optional<SelectedKit> selectKit(CacheFile cacheFile, String query, boolean quotedSearch, Map<String, Integer> inventoryCounts) {
        return selectMostAvailable(matchingTotals(cacheFile, query, quotedSearch, inventoryCounts));
    }

    KitResolution resolveKit(CacheFile cacheFile, String query, boolean quotedSearch, Map<String, Integer> inventoryCounts) {
        Map<String, AliasTotals> totals = matchingTotals(cacheFile, query, quotedSearch, inventoryCounts);
        if (totals.isEmpty()) return KitResolution.none();
        if (totals.size() == 1) return KitResolution.selected(totals.values().iterator().next().selectedKit());

        List<SelectedKit> choices = totals.values().stream()
            .map(AliasTotals::selectedKit)
            .sorted(Comparator.comparing(SelectedKit::name))
            .toList();
        return KitResolution.ambiguous(choices);
    }

    String ambiguousChoicesMessage(String query, int count, List<SelectedKit> choices) {
        List<String> examples = choices.stream()
            .limit(MAX_AMBIGUOUS_CHOICES)
            .map(choice -> "%s %d".formatted(quotedCommandName(choice.name()), count))
            .toList();
        String suffix = choices.size() > MAX_AMBIGUOUS_CHOICES ? ", ..." : "";
        return "Multiple kits match '%s'. Type one exactly: %s%s.".formatted(query, String.join(", ", examples), suffix);
    }

    List<String> availableKitNames(CacheFile cacheFile, Map<String, Integer> inventoryCounts, KitbotRequesterAccess access, String tier2Whitelist) {
        Map<String, AliasTotals> totals = allTotals(cacheFile, inventoryCounts);
        return totals.values().stream()
            .map(AliasTotals::selectedKit)
            .filter(kit -> canListKit(kit, access, tier2Whitelist))
            .map(SelectedKit::name)
            .sorted()
            .toList();
    }

    List<String> kitListMessages(List<String> kitNames) {
        return kitListMessages(kitNames, DEFAULT_KIT_LIST_MESSAGE_LENGTH);
    }

    List<String> kitListMessages(List<String> kitNames, int maxMessageLength) {
        if (kitNames == null || kitNames.isEmpty()) return List.of(EMPTY_KIT_LIST_MESSAGE);

        int safeLength = Math.max(40, maxMessageLength);
        String singlePrefix = "Available kits: ";
        String joined = String.join(", ", kitNames);
        if (singlePrefix.length() + joined.length() <= safeLength) return List.of(singlePrefix + joined);

        String conservativePrefix = "Available kits 999/999: ";
        int payloadLength = Math.max(1, safeLength - conservativePrefix.length());
        List<String> chunks = chunkNames(kitNames, payloadLength);
        List<String> messages = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            messages.add("Available kits %d/%d: %s".formatted(i + 1, chunks.size(), chunks.get(i)));
        }

        return messages;
    }

    private Map<String, AliasTotals> matchingTotals(CacheFile cacheFile, String query, boolean quotedSearch, Map<String, Integer> inventoryCounts) {
        if (StashShulkerContentClassifier.isEchestAlias(query)) return echestTotals(cacheFile, inventoryCounts);

        if (quotedSearch) {
            Map<String, AliasTotals> exactTotals = phraseMatchingTotals(cacheFile, query, inventoryCounts);
            if (!exactTotals.isEmpty()) return exactTotals;
        }

        String normalizedQuery = normalizedName(query, quotedSearch);
        Map<String, AliasTotals> totals = new LinkedHashMap<>();

        if (cacheFile != null && cacheFile.containers() != null) {
            for (StashCachedContainer container : cacheFile.containers()) {
                if (container.items() == null) continue;

                for (StashCachedItem item : container.items()) {
                    if (!item.isShulkerBox()) continue;

                    addMatchingTotal(totals, item.displayName(), item.count(), query, normalizedQuery, quotedSearch);
                }
            }
        }

        if (inventoryCounts == null) return totals;

        for (Map.Entry<String, Integer> entry : inventoryCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;

            addMatchingTotal(totals, entry.getKey(), entry.getValue(), query, normalizedQuery, quotedSearch);
        }

        return totals;
    }

    private Map<String, AliasTotals> phraseMatchingTotals(CacheFile cacheFile, String query, Map<String, Integer> inventoryCounts) {
        Map<String, AliasTotals> totals = new LinkedHashMap<>();

        if (cacheFile != null && cacheFile.containers() != null) {
            for (StashCachedContainer container : cacheFile.containers()) {
                if (container.items() == null) continue;

                for (StashCachedItem item : container.items()) {
                    if (!item.isShulkerBox()) continue;

                    addExactPhraseTotal(totals, item.displayName(), item.count(), query);
                }
            }
        }

        if (inventoryCounts == null) return totals;

        for (Map.Entry<String, Integer> entry : inventoryCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;

            addExactPhraseTotal(totals, entry.getKey(), entry.getValue(), query);
        }

        return totals;
    }

    private Map<String, AliasTotals> allTotals(CacheFile cacheFile, Map<String, Integer> inventoryCounts) {
        Map<String, AliasTotals> totals = new LinkedHashMap<>();

        if (cacheFile != null && cacheFile.containers() != null) {
            for (StashCachedContainer container : cacheFile.containers()) {
                if (container.items() == null) continue;

                for (StashCachedItem item : container.items()) {
                    if (item == null || !item.isShulkerBox() || item.count() <= 0) continue;

                    if (item.pureEchestShulker()) addTotal(totals, StashShulkerContentClassifier.ECHEST_ALIAS, item.count());
                    addTotal(totals, item.displayName(), item.count());
                }
            }
        }

        if (inventoryCounts == null) return totals;

        for (Map.Entry<String, Integer> entry : inventoryCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;

            addTotal(totals, entry.getKey(), entry.getValue());
        }

        return totals;
    }

    private Optional<SelectedKit> selectMostAvailable(Map<String, AliasTotals> totals) {
        return totals.values().stream()
            .map(AliasTotals::selectedKit)
            .max(Comparator.comparingInt(SelectedKit::totalCount).thenComparing(SelectedKit::name));
    }

    List<KitSource> buildSources(CacheFile cacheFile, String kitAlias) {
        List<KitSource> sources = new ArrayList<>();
        if (cacheFile == null || cacheFile.containers() == null) return sources;

        boolean echestAlias = StashShulkerContentClassifier.isEchestAlias(kitAlias);
        for (StashCachedContainer container : cacheFile.containers()) {
            if (container.items() == null || container.interactionPos() == null) continue;

            List<Integer> slots = new ArrayList<>();
            int count = 0;
            for (StashCachedItem item : container.items()) {
                if (!item.isShulkerBox()) continue;
                if (echestAlias) {
                    if (!item.pureEchestShulker()) continue;
                }
                else if (!StashKitNameNormalizer.matchesAliasKey(item.displayName(), kitAlias)) continue;

                slots.add(item.slot());
                count += item.count();
            }

            if (!slots.isEmpty()) {
                sources.add(new KitSource(container.id(), toBlockPos(container.interactionPos()), slots, count));
            }
        }

        return sources;
    }

    private BlockPos toBlockPos(PositionRecord pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private String normalizedName(String value, boolean quotedSearch) {
        return quotedSearch ? StashKitNameNormalizer.phraseAlias(value) : StashKitNameNormalizer.alias(value);
    }

    private void addMatchingTotal(Map<String, AliasTotals> totals, String name, int count, String query, String normalizedQuery, boolean quotedSearch) {
        if (normalizedQuery.isEmpty() || !StashKitNameNormalizer.matches(name, query, quotedSearch)) return;

        addTotal(totals, name, count);
    }

    private void addExactPhraseTotal(Map<String, AliasTotals> totals, String name, int count, String query) {
        if (!StashKitNameNormalizer.matchesExactPhrase(name, query)) return;

        addTotal(totals, name, count);
    }

    private Map<String, AliasTotals> echestTotals(CacheFile cacheFile, Map<String, Integer> inventoryCounts) {
        Map<String, AliasTotals> totals = new LinkedHashMap<>();

        if (cacheFile != null && cacheFile.containers() != null) {
            for (StashCachedContainer container : cacheFile.containers()) {
                if (container.items() == null) continue;

                for (StashCachedItem item : container.items()) {
                    if (item == null || !item.isShulkerBox() || !item.pureEchestShulker()) continue;

                    addTotal(totals, StashShulkerContentClassifier.ECHEST_ALIAS, item.count());
                }
            }
        }

        if (inventoryCounts == null) return totals;

        for (Map.Entry<String, Integer> entry : inventoryCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            if (!StashShulkerContentClassifier.isEchestAlias(entry.getKey())) continue;

            addTotal(totals, StashShulkerContentClassifier.ECHEST_ALIAS, entry.getValue());
        }

        return totals;
    }

    private void addTotal(Map<String, AliasTotals> totals, String name, int count) {
        if (count <= 0) return;

        String canonical = StashKitNameNormalizer.canonicalAlias(name);
        if (canonical.isEmpty()) return;

        totals.computeIfAbsent(canonical, AliasTotals::new).add(name, count);
    }

    private boolean canListKit(SelectedKit kit, KitbotRequesterAccess access, String tier2Whitelist) {
        if (kit == null || access == null) return false;
        if (access.tier() == KitbotTier.TIER_1) return true;
        return access.tier() == KitbotTier.TIER_2 && StashKitbotAccessPlanner.tier2KitAllowed(kit.alias(), tier2Whitelist);
    }

    private List<String> chunkNames(List<String> kitNames, int payloadLength) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String kitName : kitNames) {
            if (kitName == null || kitName.isBlank()) continue;

            if (current.isEmpty()) {
                current.append(kitName);
                continue;
            }

            int nextLength = current.length() + 2 + kitName.length();
            if (nextLength <= payloadLength) {
                current.append(", ").append(kitName);
                continue;
            }

            chunks.add(current.toString());
            current.setLength(0);
            current.append(kitName);
        }

        if (!current.isEmpty()) chunks.add(current.toString());
        return chunks;
    }

    private String quotedCommandName(String name) {
        String escaped = name.replace("\"", "\\\"");
        return escaped.indexOf(' ') >= 0 ? "\"%s\"".formatted(escaped) : escaped;
    }

    private static final class AliasTotals {
        private final String canonical;
        private int totalCount;

        private AliasTotals(String canonical) {
            this.canonical = canonical;
        }

        private void add(String rawName, int count) {
            totalCount += count;
        }

        private SelectedKit selectedKit() {
            return new SelectedKit(canonical, canonical, totalCount);
        }
    }

    record KitResolution(SelectedKit selected, List<SelectedKit> choices) {
        static KitResolution none() {
            return new KitResolution(null, List.of());
        }

        static KitResolution selected(SelectedKit selected) {
            return new KitResolution(selected, List.of());
        }

        static KitResolution ambiguous(List<SelectedKit> choices) {
            return new KitResolution(null, choices);
        }

        boolean hasSelection() {
            return selected != null;
        }

        boolean ambiguous() {
            return !choices.isEmpty();
        }
    }
}
