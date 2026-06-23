package com.watchmenbot.modules.planebuilder;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;

final class PlaneBaritoneSafetyGuard {
    private BaritoneSafetySettings savedSettings;

    void apply() {
        Settings settings = BaritoneAPI.getSettings();
        if (savedSettings == null) {
            savedSettings = new BaritoneSafetySettings(
                settings.allowBreak.value,
                settings.allowPlace.value,
                settings.allowParkourPlace.value,
                settings.allowInventory.value,
                settings.autoTool.value,
                settings.rightClickContainerOnArrival.value,
                settings.allowDownward.value
            );
        }

        settings.allowBreak.value = false;
        settings.allowPlace.value = false;
        settings.allowParkourPlace.value = false;
        settings.allowInventory.value = false;
        settings.autoTool.value = false;
        settings.rightClickContainerOnArrival.value = false;
        settings.allowDownward.value = false;
    }

    void restore() {
        if (savedSettings == null) return;

        Settings settings = BaritoneAPI.getSettings();
        settings.allowBreak.value = savedSettings.allowBreak();
        settings.allowPlace.value = savedSettings.allowPlace();
        settings.allowParkourPlace.value = savedSettings.allowParkourPlace();
        settings.allowInventory.value = savedSettings.allowInventory();
        settings.autoTool.value = savedSettings.autoTool();
        settings.rightClickContainerOnArrival.value = savedSettings.rightClickContainerOnArrival();
        settings.allowDownward.value = savedSettings.allowDownward();
        savedSettings = null;
    }

    private record BaritoneSafetySettings(
        boolean allowBreak,
        boolean allowPlace,
        boolean allowParkourPlace,
        boolean allowInventory,
        boolean autoTool,
        boolean rightClickContainerOnArrival,
        boolean allowDownward
    ) {
    }
}
