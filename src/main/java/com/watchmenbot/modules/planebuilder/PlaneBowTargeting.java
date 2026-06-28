package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitchEntity;

final class PlaneBowTargeting {
    static final double MELEE_PREP_RANGE = 4.5;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    Entity nearestSafeBowTarget(double range) {
        Entity prioritized = highestPriorityThreat(entity -> safeBowTarget(entity, range));
        return prioritized == null ? TargetUtils.get(entity -> safeBowTarget(entity, range), SortPriority.LowestDistance) : prioritized;
    }

    Entity nearestCloseMeleeThreat() {
        Entity prioritized = highestPriorityThreat(this::closeMeleeThreat);
        return prioritized == null ? TargetUtils.get(this::closeMeleeThreat, SortPriority.LowestDistance) : prioritized;
    }

    boolean nearbyHostileThreat(double range) {
        return highestPriorityThreat(entity -> {
            TargetFacts facts = targetFacts(entity);
            return facts != null && facts.distance() <= range && (facts.visible() || facts.aggroedOnBot());
        }) != null;
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
        if (facts.distance() <= MELEE_PREP_RANGE) return BowTargetStatus.MELEE_HANDOFF;

        return BowTargetStatus.READY;
    }

    boolean closeMeleeThreat(Entity entity) {
        TargetFacts facts = targetFacts(entity);
        return facts != null && meleePrepPolicy(facts.distance(), facts.aggroedOnBot());
    }

    static boolean bowTargetPolicy(double distance, double maxRange, boolean visible, boolean aggroedOnBot) {
        if (!visible || distance > maxRange) return false;
        if (distance <= MELEE_PREP_RANGE) return false;

        return true;
    }

    static boolean meleePrepPolicy(double distance, boolean aggroedOnBot) {
        return distance <= MELEE_PREP_RANGE;
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

        boolean aggroedOnBot = KillAuraCompanionSettings.isAggroedOnBot(
            mob.getTarget() == mc.player,
            entity instanceof Angerable angerable ? angerable.getAngryAt() : null,
            mc.player.getUuid()
        );
        return new TargetFacts(mc.player.distanceTo(entity), PlayerUtils.canSeeEntity(entity), aggroedOnBot);
    }

    private record TargetFacts(double distance, boolean visible, boolean aggroedOnBot) {
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
