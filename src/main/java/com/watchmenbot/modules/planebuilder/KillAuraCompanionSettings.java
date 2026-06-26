package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class KillAuraCompanionSettings {
    static final int MAX_TARGETS = 5;

    private static final String ENTITIES = "entities";
    private static final String AUTO_SWITCH = "auto-switch";
    private static final String SWAP_BACK = "swap-back";
    private static final String MAX_TARGETS_SETTING = "max-targets";
    private static final String MOB_AGE_FILTER = "mob-age-filter";

    private KillAuraCompanionSettings() {
    }

    static List<CompanionModuleManager.SettingSnapshot> apply(KillAura killAura) {
        List<CompanionModuleManager.SettingSnapshot> snapshots = List.of(
            snapshot(killAura, ENTITIES),
            snapshot(killAura, AUTO_SWITCH),
            snapshot(killAura, SWAP_BACK),
            snapshot(killAura, MAX_TARGETS_SETTING),
            snapshot(killAura, MOB_AGE_FILTER)
        );

        setting(killAura, ENTITIES, Set.class).set(entities());
        setting(killAura, AUTO_SWITCH, Boolean.class).set(true);
        setting(killAura, SWAP_BACK, Boolean.class).set(true);
        setting(killAura, MAX_TARGETS_SETTING, Integer.class).set(MAX_TARGETS);
        setting(killAura, MOB_AGE_FILTER, KillAura.EntityAge.class).set(KillAura.EntityAge.Both);

        return snapshots;
    }

    static Set<EntityType<?>> entities() {
        Set<EntityType<?>> entities = new HashSet<>();

        for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
            if (!isMobGroup(entityType.getSpawnGroup())) continue;
            if (!isAllowedMobEntity(
                EntityUtils.isAttackable(entityType),
                entityType == EntityType.PLAYER,
                entityType == EntityType.ENDERMAN
            )) continue;

            entities.add(entityType);
        }

        return entities;
    }

    static Set<EntityType<?>> hostileEntities() {
        Set<EntityType<?>> entities = new HashSet<>();

        for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
            if (!isHostileMobGroup(entityType.getSpawnGroup())) continue;
            if (!isAllowedMobEntity(
                EntityUtils.isAttackable(entityType),
                entityType == EntityType.PLAYER,
                entityType == EntityType.ENDERMAN
            )) continue;

            entities.add(entityType);
        }

        return entities;
    }

    static boolean isMobGroup(SpawnGroup group) {
        return group == SpawnGroup.MONSTER
            || group == SpawnGroup.CREATURE
            || group == SpawnGroup.AMBIENT
            || group == SpawnGroup.WATER_CREATURE
            || group == SpawnGroup.WATER_AMBIENT
            || group == SpawnGroup.UNDERGROUND_WATER_CREATURE
            || group == SpawnGroup.AXOLOTLS;
    }

    static boolean isHostileMobGroup(SpawnGroup group) {
        return group == SpawnGroup.MONSTER;
    }

    static boolean isAllowedMobEntity(boolean attackable, boolean player, boolean enderman) {
        return attackable && !player && !enderman;
    }

    static boolean isAggroedOnBot(boolean targetingBot, UUID angryAt, UUID botUuid) {
        return targetingBot || (angryAt != null && angryAt.equals(botUuid));
    }

    private static CompanionModuleManager.SettingSnapshot snapshot(KillAura killAura, String name) {
        Setting<?> setting = killAura.settings.get(name);
        if (setting == null) throw new IllegalStateException(killAura.name + " is missing setting: " + name);

        return new CompanionModuleManager.SettingSnapshot(setting, setting.toTag());
    }

    @SuppressWarnings("unchecked")
    private static <T> Setting<T> setting(KillAura killAura, String name, Class<?> valueClass) {
        Setting<?> setting = killAura.settings.get(name);
        if (setting == null) throw new IllegalStateException(killAura.name + " is missing setting: " + name);
        if (!valueClass.isInstance(setting.get())) {
            throw new IllegalStateException(killAura.name + " setting has unexpected type: " + name);
        }

        return (Setting<T>) setting;
    }
}
