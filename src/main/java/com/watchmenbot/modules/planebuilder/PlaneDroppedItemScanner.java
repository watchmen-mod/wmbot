package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

final class PlaneDroppedItemScanner {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneWorldAccess world = new PlaneWorldAccess();
    private final double scanRadius;
    private final Predicate<ItemEntity> predicate;

    PlaneDroppedItemScanner(double scanRadius, Predicate<ItemEntity> predicate) {
        this.scanRadius = scanRadius;
        this.predicate = predicate;
    }

    ItemEntity nearestMatchingDrop() {
        return nearestMatchingDrop(item -> true);
    }

    ItemEntity nearestMatchingDrop(Predicate<ItemEntity> extraPredicate) {
        if (mc.player == null || mc.world == null) return null;

        return matchingDrops()
            .stream()
            .filter(extraPredicate)
            .min(Comparator.comparingDouble(item -> item.squaredDistanceTo(mc.player)))
            .orElse(null);
    }

    List<ItemEntity> matchingDrops() {
        if (mc.player == null || mc.world == null) return List.of();

        Box scanBox = mc.player.getBoundingBox().expand(scanRadius);
        return world.itemEntities(scanBox, predicate);
    }
}
