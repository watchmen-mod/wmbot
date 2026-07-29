package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.util.math.Box;

final class PlaneBowTargeting {
    static final double MELEE_PREP_RANGE = KillAuraCompanionSettings.ATTACK_RANGE;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    Entity nearestSafeBowTarget(double range) {
        Entity prioritized = highestPriorityThreat(entity -> safeBowTarget(entity, range));
        return prioritized == null ? TargetUtils.get(entity -> safeBowTarget(entity, range), SortPriority.LowestDistance) : prioritized;
    }

    Entity nearestCloseMeleeThreat() {
        Entity prioritized = highestPriorityThreat(this::closeMeleeThreat);
        return prioritized == null ? TargetUtils.get(this::closeMeleeThreat, SortPriority.LowestDistance) : prioritized;
    }

    Entity lockedTarget(int targetId) {
        return mc.world == null || targetId < 0 ? null : mc.world.getEntityById(targetId);
    }

    boolean safeBowTarget(Entity entity, double range) {
        return bowTargetStatus(entity, range) == BowTargetStatus.READY;
    }

    BowTargetStatus bowTargetStatus(Entity entity, double range) {
        TargetFacts facts = targetFacts(entity);
        if (facts == null) return BowTargetStatus.INVALID;
        if (!facts.visible()) return BowTargetStatus.NOT_VISIBLE;
        if (facts.distance() > range) return BowTargetStatus.OUT_OF_RANGE;
        if (facts.meleeDistance() <= MELEE_PREP_RANGE) return BowTargetStatus.MELEE_HANDOFF;

        return BowTargetStatus.READY;
    }

    boolean closeMeleeThreat(Entity entity) {
        TargetFacts facts = targetFacts(entity);
        return facts != null && meleeTargetPolicy(facts.meleeDistance(), facts.visible());
    }

    static boolean bowTargetPolicy(double distance, double maxRange, boolean visible, boolean aggroedOnBot) {
        if (!visible || distance > maxRange) return false;
        if (distance <= MELEE_PREP_RANGE) return false;

        return true;
    }

    static boolean meleeTargetPolicy(double distance, boolean visible) {
        return visible && distance <= MELEE_PREP_RANGE;
    }

    static double distanceToHitbox(double x, double y, double z, Box hitbox) {
        double nearestX = Math.max(hitbox.minX, Math.min(x, hitbox.maxX));
        double nearestY = Math.max(hitbox.minY, Math.min(y, hitbox.maxY));
        double nearestZ = Math.max(hitbox.minZ, Math.min(z, hitbox.maxZ));
        double dx = x - nearestX;
        double dy = y - nearestY;
        double dz = z - nearestZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static int threatPriority(boolean witch, boolean skeleton, boolean creeper, boolean hostile) {
        if (witch) return 400;
        if (skeleton) return 300;
        if (creeper) return 200;
        return hostile ? 100 : 0;
    }

    private Entity highestPriorityThreat(java.util.function.Predicate<Entity> predicate) {
        if (mc.world == null || mc.player == null) return null;

        Entity best = null;
        int bestPriority = 0;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!predicate.test(entity)) continue;

            int priority = threatPriority(entity);
            double distance = mc.player.distanceTo(entity);
            if (priority > bestPriority || (priority == bestPriority && distance < bestDistance)) {
                best = entity;
                bestPriority = priority;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static int threatPriority(Entity entity) {
        return threatPriority(
            entity instanceof WitchEntity,
            entity instanceof SkeletonEntity,
            entity instanceof CreeperEntity,
            entity instanceof MobEntity
        );
    }

    private TargetFacts targetFacts(Entity entity) {
        if (entity == null || mc.player == null || entity == mc.player || entity == mc.cameraEntity) return null;
        if (!(entity instanceof MobEntity mob)) return null;

        LivingEntity living = mob;
        if (!entity.isAlive() || living.isDead()) return null;
        if (!KillAuraCompanionSettings.isHostileMobGroup(entity.getType().getSpawnGroup())) return null;
        if (!KillAuraCompanionSettings.isAllowedMobEntity(
            EntityUtils.isAttackable(entity.getType()),
            entity.getType() == EntityType.PLAYER,
            entity.getType() == EntityType.ENDERMAN
        )) return null;
        if (entity.hasCustomName()) return null;

        return new TargetFacts(
            mc.player.distanceTo(entity),
            distanceToHitbox(mc.player.getX(), mc.player.getY(), mc.player.getZ(), entity.getBoundingBox()),
            PlayerUtils.canSeeEntity(entity)
        );
    }

    private record TargetFacts(double distance, double meleeDistance, boolean visible) {
    }

    enum BowTargetStatus {
        READY("target ready"),
        INVALID("target lost"),
        NOT_VISIBLE("visibility lost"),
        OUT_OF_RANGE("target out of bow range"),
        MELEE_HANDOFF("target entered melee handoff range");

        private final String logReason;

        BowTargetStatus(String logReason) {
            this.logReason = logReason;
        }

        String logReason() {
            return logReason;
        }
    }
}
