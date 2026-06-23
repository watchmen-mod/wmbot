package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.combat.BowAimbot;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import net.minecraft.entity.EntityType;

import java.util.List;
import java.util.Set;

final class BowAimbotCompanionSettings {
    private static final String RANGE = "range";
    private static final String ENTITIES = "entities";
    private static final String PRIORITY = "priority";
    private static final String BABIES = "babies";
    private static final String NAMETAGGED = "nametagged";
    private static final String PAUSE_ON_COMBAT = "pause-on-combat";

    private BowAimbotCompanionSettings() {
    }

    static List<CompanionModuleManager.SettingSnapshot> apply(BowAimbot bowAimbot, double range) {
        List<CompanionModuleManager.SettingSnapshot> snapshots = List.of(
            CompanionModuleManager.snapshot(bowAimbot, RANGE),
            CompanionModuleManager.snapshot(bowAimbot, ENTITIES),
            CompanionModuleManager.snapshot(bowAimbot, PRIORITY),
            CompanionModuleManager.snapshot(bowAimbot, BABIES),
            CompanionModuleManager.snapshot(bowAimbot, NAMETAGGED),
            CompanionModuleManager.snapshot(bowAimbot, PAUSE_ON_COMBAT)
        );

        CompanionModuleManager.setting(bowAimbot, RANGE, Double.class).set(range);
        CompanionModuleManager.<Set<EntityType<?>>>setting(bowAimbot, ENTITIES, Set.class).set(KillAuraCompanionSettings.entities());
        CompanionModuleManager.setting(bowAimbot, PRIORITY, SortPriority.class).set(SortPriority.LowestDistance);
        CompanionModuleManager.setting(bowAimbot, BABIES, Boolean.class).set(true);
        CompanionModuleManager.setting(bowAimbot, NAMETAGGED, Boolean.class).set(false);
        CompanionModuleManager.setting(bowAimbot, PAUSE_ON_COMBAT, Boolean.class).set(false);

        return snapshots;
    }
}
