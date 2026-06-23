package com.watchmenbot;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

final class WMBotRegistry {
    private WMBotRegistry() {
    }

    static void registerModules(Module... modules) {
        for (Module module : modules) {
            Modules.get().add(module);
        }
    }

    static void registerCategory(Category category) {
        Modules.registerCategory(category);
    }

    @SafeVarargs
    static void registerHud(HudElementInfo<? extends HudElement>... elements) {
        for (HudElementInfo<? extends HudElement> element : elements) {
            Hud.get().register(element);
        }
    }
}
