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
import net.minecraft.entity.mob.MobEntity;

final class PlaneBowTargeting {
    static final double MELEE_PREP_RANGE = 4.5;
    static final double BOW_FALLBACK_MIN_RANGE = 6.0;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    Entity nearestSafeBowTarget(double range) {
        return TargetUtils.get(entity -> safeBowTarget(entity, range), SortPriority.LowestDistance);
    }

    Entity nearestCloseMeleeThreat() {
        return TargetUtils.get(this::closeMeleeThreat, SortPriority.LowestDistance);
    }

    Entity lockedTarget(int targetId) {
        return mc.world == null || targetId < 0 ? null : mc.world.getEntityById(targetId);
    }

    boolean safeBowTarget(Entity entity, double range) {
        TargetFacts facts = targetFacts(entity);
        if (facts == null) return false;

        return bowTargetPolicy(
            facts.distance(),
            range,
            facts.visible(),
            facts.aggroedOnBot()
        );
    }

    boolean closeMeleeThreat(Entity entity) {
        TargetFacts facts = targetFacts(entity);
        return facts != null && meleePrepPolicy(facts.distance(), facts.aggroedOnBot());
    }

    static boolean bowTargetPolicy(double distance, double maxRange, boolean visible, boolean aggroedOnBot) {
        if (!visible || distance > maxRange) return false;
        if (distance <= MELEE_PREP_RANGE) return false;

        return aggroedOnBot || distance >= BOW_FALLBACK_MIN_RANGE;
    }

    static boolean meleePrepPolicy(double distance, boolean aggroedOnBot) {
        return aggroedOnBot && distance <= MELEE_PREP_RANGE;
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
}
