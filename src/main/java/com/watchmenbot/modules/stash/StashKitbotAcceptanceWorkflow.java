package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class StashKitbotAcceptanceWorkflow {
    private final MinecraftClient mc;
    private final StashKitbotSession session;
    private final StashInventoryCache cache;
    private final StashKitbotInventory inventory;
    private final StashKitbotStockPlanner stockPlanner;
    private final Callbacks callbacks;

    StashKitbotAcceptanceWorkflow(
        MinecraftClient mc,
        StashKitbotSession session,
        StashInventoryCache cache,
        StashKitbotInventory inventory,
        StashKitbotStockPlanner stockPlanner,
        Callbacks callbacks
    ) {
        this.mc = mc;
        this.session = session;
        this.cache = cache;
        this.inventory = inventory;
        this.stockPlanner = stockPlanner;
        this.callbacks = callbacks;
    }

    boolean startRequest(KitbotRequesterAccess access, KitCommand command, boolean queued) {
        String requester = access.requester();
        CacheFile cacheFile;
        try {
            cacheFile = cache.read(mc);
        }
        catch (IOException exception) {
            callbacks.reply(requester, "Failed to read stash cache.");
            callbacks.warning("Failed to read stash inventory cache: %s.", exception.getMessage());
            return false;
        }

        Map<String, Integer> inventoryCounts = inventory.matchingKitCounts(command.name(), command.quotedSearch());
        if ((cacheFile == null || cacheFile.containers() == null || cacheFile.containers().isEmpty()) && inventoryCounts.isEmpty()) {
            callbacks.reply(requester, "No stash inventory cache is available.");
            return false;
        }

        StashKitbotStockPlanner.KitResolution resolution = stockPlanner.resolveKit(cacheFile, command.name(), command.quotedSearch(), inventoryCounts);
        if (resolution.ambiguous()) {
            replyAmbiguousChoices(requester, command, resolution.choices());
            return false;
        }
        if (!resolution.hasSelection()) {
            callbacks.reply(requester, "No shulkers match '%s' in inventory or cache.".formatted(command.name()));
            return false;
        }

        SelectedKit kit = resolution.selected();
        if (kit.totalCount() < command.count()) {
            callbacks.reply(requester, "Only %d '%s' shulkers are available across inventory and cache.".formatted(kit.totalCount(), kit.name()));
            return false;
        }

        int initialInventoryCount = inventory.matchingInventoryCount(kit.alias());
        int inventoryMatches = Math.min(command.count(), initialInventoryCount);
        int remainingToGather = command.count() - inventoryMatches;
        if (inventory.emptyInventorySlots() < remainingToGather) {
            callbacks.reply(requester, "Inventory needs %d empty slots for '%s'.".formatted(remainingToGather, kit.name()));
            return false;
        }

        List<KitSource> sources = stockPlanner.buildSources(cacheFile, kit.alias());
        if (remainingToGather > 0 && sources.isEmpty()) {
            callbacks.reply(requester, "No source containers found for '%s'.".formatted(kit.name()));
            return false;
        }

        sources.sort(Comparator.comparingDouble(source -> StashClientUtils.playerBlockDistanceSq(mc, source.interactionPos())));
        KitRequest request = new KitRequest(
            requester,
            kit.name(),
            kit.alias(),
            command.count(),
            sources,
            mc.player.getBlockPos().toImmutable()
        );
        request.gather.initialInventoryCount = initialInventoryCount;
        request.gather.gathered = StashKitbotGatherPlanner.confirmedGathered(command.count(), initialInventoryCount, initialInventoryCount);
        session.startRequest(request);

        String prefix = queued ? "Starting queued " : "Accepted ";
        if (inventoryMatches > 0 && remainingToGather > 0) {
            callbacks.reply(requester, "%s'%s' x%d. Using %d already in inventory; gathering %d more.".formatted(prefix, kit.name(), command.count(), inventoryMatches, remainingToGather));
        }
        else if (inventoryMatches >= command.count()) {
            session.phase(KitbotPhase.TPA_REQUEST);
            callbacks.queueReply(requester, "%s'%s' x%d. I already have it in inventory; sending TPA now.".formatted(prefix, kit.name(), command.count()));
            callbacks.info("%s'%s' x%d from current inventory for %s. Queued acceptance whisper after TPA to avoid command cooldown.", prefix, kit.name(), command.count(), requester);
        }
        else {
            callbacks.reply(requester, "%s'%s' x%d. Gathering now.".formatted(prefix, kit.name(), command.count()));
        }
        callbacks.info("Accepted stash kit request from %s: %s x%d (%d already in inventory, %d to gather).", requester, kit.name(), command.count(), inventoryMatches, remainingToGather);
        return true;
    }

    private void replyAmbiguousChoices(String requester, KitCommand command, List<SelectedKit> choices) {
        callbacks.reply(requester, stockPlanner.ambiguousChoicesMessage(command.name(), command.count(), choices));
    }

    interface Callbacks {
        void reply(String player, String message);

        void queueReply(String player, String message);

        void info(String message, Object... args);

        void warning(String message, Object... args);
    }
}
