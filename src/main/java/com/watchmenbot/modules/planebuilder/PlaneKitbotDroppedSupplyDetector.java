package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.ItemEntity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class PlaneKitbotDroppedSupplyDetector {
    private final PlaneDroppedItemScanner scanner;

    PlaneKitbotDroppedSupplyDetector() {
        scanner = new PlaneDroppedItemScanner(PlanePickupSettings.KITBOT_REFILL_SCAN_RADIUS, this::matchesSupply);
    }

    ItemEntity nearestDroppedSupply() {
        return scanner.nearestMatchingDrop();
    }

    ItemEntity nearestDroppedSupplyExcluding(Set<UUID> excludedIds) {
        Set<UUID> safeExcludedIds = excludedIds == null ? Set.of() : excludedIds;
        return scanner.nearestMatchingDrop(item -> !safeExcludedIds.contains(item.getUuid()));
    }

    Set<UUID> droppedSupplyIds() {
        return scanner.matchingDrops()
            .stream()
            .map(ItemEntity::getUuid)
            .collect(Collectors.toSet());
    }

    boolean matchesSupply(ItemEntity item) {
        if (item == null || !item.isAlive() || item.isRemoved()) return false;
        return PlaneItemClassifier.isKitbotRefillDrop(item.getStack());
    }
}
