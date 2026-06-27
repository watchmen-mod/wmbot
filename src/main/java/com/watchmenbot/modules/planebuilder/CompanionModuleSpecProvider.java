package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;

import java.util.List;

final class CompanionModuleSpecProvider {
    private final PlaneBuilderSettings.CompanionModules settings;

    CompanionModuleSpecProvider(PlaneBuilderSettings.CompanionModules settings) {
        this.settings = settings;
    }

    List<CompanionModuleSpec> specs() {
        return List.of(
            new CompanionModuleSpec(AutoTotem.class, settings.autoTotem(), module -> List.of()),
            new CompanionModuleSpec(AutoEat.class, settings.autoEat(), module -> AutoEatCompanionSettings.apply((AutoEat) module)),
            new CompanionModuleSpec(Velocity.class, settings.velocity(), module -> List.of()),
            new CompanionModuleSpec(InstantRebreak.class, settings.instantRebreak(), module -> List.of()),
            new CompanionModuleSpec(KillAura.class, settings.killAura(), module -> KillAuraCompanionSettings.apply((KillAura) module)),
            new CompanionModuleSpec(NoRender.class, settings.noRender(), module -> NoRenderCompanionSettings.apply((NoRender) module))
        );
    }

    record CompanionModuleSpec(
        Class<? extends Module> moduleClass,
        Setting<Boolean> enabled,
        SessionSettingsApplier settingsApplier
    ) {
        List<CompanionModuleManager.SettingSnapshot> applySessionSettings(Module module) {
            return settingsApplier.apply(module);
        }
    }

    interface SessionSettingsApplier {
        List<CompanionModuleManager.SettingSnapshot> apply(Module module);
    }
}
