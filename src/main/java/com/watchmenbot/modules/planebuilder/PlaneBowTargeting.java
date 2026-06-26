package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.Set;

final class PlaneBowTargeting {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    Entity nearestSafeBowTarget(double range) {
        return TargetUtils.get(entity -> safeBowTarget(entity, range), SortPriority.LowestDistance);
    }

    Entity nearestSafeBowTarget(double range, Set<Integer> suppressedTargetIds) {
        return TargetUtils.get(
            entity -> safeBowTarget(entity, range)
                && (suppressedTargetIds == null || !suppressedTargetIds.contains(entity.getId())),
            SortPriority.LowestDistance
        );
    }

    Entity lockedTarget(int targetId) {
        return mc.world == null || targetId < 0 ? null : mc.world.getEntityById(targetId);
    }

    boolean safeBowTarget(Entity entity, double range) {
        if (entity == null || entity == mc.player || entity == mc.cameraEntity) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (!entity.isAlive() || living.isDead()) return false;
        if (!PlayerUtils.isWithin(entity, range)) return false;
        if (!PlaneBowDefenseTargets.isThreatGroup(entity.getType().getSpawnGroup())) return false;
        if (!PlaneBowDefenseTargets.isAllowedEntityType(entity.getType())) return false;
        if (entity.hasCustomName()) return false;

        return PlayerUtils.canSeeEntity(entity);
    }
}
