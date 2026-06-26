package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

final class PlaneBowDefenseTargets {
    private PlaneBowDefenseTargets() {
    }

    static Set<EntityType<?>> entities() {
        return entities(Registries.ENTITY_TYPE);
    }

    static Set<EntityType<?>> entities(Iterable<EntityType<?>> registry) {
        Set<EntityType<?>> entities = new HashSet<>();

        for (EntityType<?> entityType : registry) {
            if (!isThreatGroup(entityType.getSpawnGroup())) continue;
            if (!isAllowedEntityType(entityType)) continue;

            entities.add(entityType);
        }

        return entities;
    }

    static boolean isThreatGroup(SpawnGroup group) {
        return group == SpawnGroup.MONSTER;
    }

    static boolean isAllowedEntityType(EntityType<?> entityType) {
        return EntityUtils.isAttackable(entityType)
            && entityType != EntityType.PLAYER
            && entityType != EntityType.ENDERMAN;
    }
}
