package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

final class PlaneBowTargeting {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    Entity nearestSafeBowTarget(double range) {
        return TargetUtils.get(entity -> safeBowTarget(entity, range), SortPriority.LowestDistance);
    }

    Entity nearestSafeBowTarget(double range, int suppressedTargetId, int suppressionTicksRemaining) {
        return TargetUtils.get(
            entity -> safeBowTarget(entity, range)
                && !PlaneBowDefenseDecisions.suppressesTarget(suppressedTargetId, entity.getId(), suppressionTicksRemaining),
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
        if (!KillAuraCompanionSettings.isMobGroup(entity.getType().getSpawnGroup())) return false;
        if (!EntityUtils.isAttackable(entity.getType())) return false;
        if (entity.getType() == EntityType.PLAYER || entity.getType() == EntityType.ENDERMAN) return false;
        if (entity.hasCustomName()) return false;

        return PlayerUtils.canSeeEntity(entity);
    }
}
