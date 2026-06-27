package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

final class AutoEatCompanionSettings {
    private static final String BLACKLIST = "blacklist";
    private static final String PAUSE_AURAS = "pause-auras";
    private static final String PAUSE_BARITONE = "pause-baritone";
    private static final String THRESHOLD_MODE = "threshold-mode";
    private static final String HEALTH_THRESHOLD = "health-threshold";
    private static final String HUNGER_THRESHOLD = "hunger-threshold";

    private AutoEatCompanionSettings() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static List<CompanionModuleManager.SettingSnapshot> apply(AutoEat autoEat) {
        List<CompanionModuleManager.SettingSnapshot> snapshots = List.of(
            CompanionModuleManager.snapshot(autoEat, BLACKLIST),
            CompanionModuleManager.snapshot(autoEat, PAUSE_AURAS),
            CompanionModuleManager.snapshot(autoEat, PAUSE_BARITONE),
            CompanionModuleManager.snapshot(autoEat, THRESHOLD_MODE),
            CompanionModuleManager.snapshot(autoEat, HEALTH_THRESHOLD),
            CompanionModuleManager.snapshot(autoEat, HUNGER_THRESHOLD)
        );

        Setting<List> blacklist = CompanionModuleManager.setting(autoEat, BLACKLIST, List.class);
        List foods = new ArrayList(blacklist.get());
        foods.remove(Items.GOLDEN_APPLE);
        foods.remove(Items.ENCHANTED_GOLDEN_APPLE);
        blacklist.set(foods);

        CompanionModuleManager.setting(autoEat, PAUSE_AURAS, Boolean.class).set(true);
        CompanionModuleManager.setting(autoEat, PAUSE_BARITONE, Boolean.class).set(true);
        CompanionModuleManager.setting(autoEat, THRESHOLD_MODE, AutoEat.ThresholdMode.class).set(AutoEat.ThresholdMode.Any);
        CompanionModuleManager.setting(autoEat, HEALTH_THRESHOLD, Double.class).set(16.0);
        CompanionModuleManager.setting(autoEat, HUNGER_THRESHOLD, Integer.class).set(18);

        return snapshots;
    }
}
