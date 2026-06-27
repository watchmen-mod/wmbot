package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.render.NoRender;

import java.util.List;

final class NoRenderCompanionSettings {
    private static final List<String> SAFE_PERFORMANCE_SETTINGS = List.of(
        "weather",
        "totem-animation",
        "eating-particles",
        "block-break-particles",
        "block-break-overlay",
        "falling-blocks",
        "firework-explosions"
    );

    private NoRenderCompanionSettings() {
    }

    static List<CompanionModuleManager.SettingSnapshot> apply(NoRender noRender) {
        List<CompanionModuleManager.SettingSnapshot> snapshots = SAFE_PERFORMANCE_SETTINGS.stream()
            .map(name -> CompanionModuleManager.snapshot(noRender, name))
            .toList();

        for (String name : SAFE_PERFORMANCE_SETTINGS) {
            CompanionModuleManager.setting(noRender, name, Boolean.class).set(true);
        }

        return snapshots;
    }
}
