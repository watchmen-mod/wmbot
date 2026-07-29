package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
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
    static final double ATTACK_RANGE = 3.0;
    static final double WALLS_RANGE = 0.0;

    private static final String WEAPON = "weapon";
    private static final String ROTATE = "rotate";
    private static final String ENTITIES = "entities";
    private static final String AUTO_SWITCH = "auto-switch";
    private static final String SWAP_BACK = "swap-back";
    private static final String ONLY_ON_CLICK = "only-on-click";
    private static final String ONLY_ON_LOOK = "only-on-look";
    private static final String PRIORITY = "priority";
    private static final String RANGE = "range";
    private static final String WALLS_RANGE_SETTING = "walls-range";
    private static final String IGNORE_NAMED = "ignore-named";
    private static final String IGNORE_TAMED = "ignore-tamed";
    private static final String MAX_TARGETS_SETTING = "max-targets";
    private static final String MOB_AGE_FILTER = "mob-age-filter";

    private KillAuraCompanionSettings() {
    }

    static List<CompanionModuleManager.SettingSnapshot> apply(KillAura killAura) {
        List<CompanionModuleManager.SettingSnapshot> snapshots = sessionSettingNames().stream()
            .map(name -> snapshot(killAura, name))
            .toList();

        setting(killAura, WEAPON, KillAura.Weapon.class).set(sessionWeapon());
        setting(killAura, ROTATE, KillAura.RotationMode.class).set(KillAura.RotationMode.Always);
        setting(killAura, ENTITIES, Set.class).set(hostileEntities());
        setting(killAura, AUTO_SWITCH, Boolean.class).set(true);
        setting(killAura, SWAP_BACK, Boolean.class).set(true);
        setting(killAura, ONLY_ON_CLICK, Boolean.class).set(false);
        setting(killAura, ONLY_ON_LOOK, Boolean.class).set(false);
        setting(killAura, PRIORITY, SortPriority.class).set(SortPriority.LowestDistance);
        setting(killAura, RANGE, Double.class).set(ATTACK_RANGE);
        setting(killAura, WALLS_RANGE_SETTING, Double.class).set(WALLS_RANGE);
        setting(killAura, IGNORE_NAMED, Boolean.class).set(true);
        setting(killAura, IGNORE_TAMED, Boolean.class).set(true);
        setting(killAura, MAX_TARGETS_SETTING, Integer.class).set(MAX_TARGETS);
        setting(killAura, MOB_AGE_FILTER, KillAura.EntityAge.class).set(KillAura.EntityAge.Both);

        return snapshots;
    }

    static List<String> sessionSettingNames() {
        return List.of(
            WEAPON,
            ROTATE,
            ENTITIES,
            AUTO_SWITCH,
            SWAP_BACK,
            ONLY_ON_CLICK,
            ONLY_ON_LOOK,
            PRIORITY,
            RANGE,
            WALLS_RANGE_SETTING,
            IGNORE_NAMED,
            IGNORE_TAMED,
            MAX_TARGETS_SETTING,
            MOB_AGE_FILTER
        );
    }

    static KillAura.Weapon sessionWeapon() {
        return KillAura.Weapon.Sword;
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
