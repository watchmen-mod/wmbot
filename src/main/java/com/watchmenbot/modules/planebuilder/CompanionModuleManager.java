package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CompanionModuleManager {
    private final CompanionModuleSpecProvider specProvider;
    private final PlaneModuleAccess moduleAccess;
    private final List<ManagedModuleState> sessionModules = new ArrayList<>();
    private final Set<Class<? extends Module>> temporarilySuspended = new HashSet<>();
    private boolean applied;

    CompanionModuleManager(PlaneBuilderSettings.CompanionModules settings) {
        this(new CompanionModuleSpecProvider(settings), new PlaneModuleAccess());
    }

    CompanionModuleManager(CompanionModuleSpecProvider specProvider, PlaneModuleAccess moduleAccess) {
        this.specProvider = specProvider;
        this.moduleAccess = moduleAccess;
    }

    void activate() {
        if (applied) return;

        sessionModules.clear();

        for (CompanionModuleSpecProvider.CompanionModuleSpec spec : specProvider.specs()) {
            captureSessionModule(spec);
        }

        applied = true;
    }

    void suspend() {
        if (!applied) return;

        for (ManagedModuleState state : sessionModules) {
            if (moduleAccess.active(state.module)) moduleAccess.toggle(state.module);
        }

        temporarilySuspended.clear();
        applied = false;
    }

    void resume() {
        if (applied) return;
        if (sessionModules.isEmpty()) {
            activate();
            return;
        }

        for (ManagedModuleState state : sessionModules) {
            if (!moduleAccess.active(state.module)) moduleAccess.toggle(state.module);
        }

        temporarilySuspended.clear();
        applied = true;
    }

    void suspend(Class<? extends Module> moduleClass) {
        ManagedModuleState state = managedState(moduleClass);
        if (state == null || !moduleAccess.active(state.module)) return;

        moduleAccess.toggle(state.module);
        temporarilySuspended.add(moduleClass);
    }

    void resume(Class<? extends Module> moduleClass) {
        ManagedModuleState state = managedState(moduleClass);
        if (state == null || !temporarilySuspended.remove(moduleClass)) return;
        if (!moduleAccess.active(state.module)) moduleAccess.toggle(state.module);
    }

    void clearInstantRebreakTarget() {
        ManagedModuleState state = managedState(InstantRebreak.class);
        if (state == null || !(state.module instanceof InstantRebreak instantRebreak)) return;

        instantRebreak.blockPos.set(0, -1, 0);
    }

    void restore() {
        for (ManagedModuleState state : sessionModules) {
            if (moduleAccess.active(state.module) != state.wasActive) moduleAccess.toggle(state.module);
            state.restoreSettings();
        }

        sessionModules.clear();
        temporarilySuspended.clear();
        applied = false;
    }

    private void captureSessionModule(CompanionModuleSpecProvider.CompanionModuleSpec spec) {
        if (!spec.enabled().get()) return;

        Module module = moduleAccess.get(spec.moduleClass());
        if (module == null) return;

        List<SettingSnapshot> settingSnapshots = spec.applySessionSettings(module);
        boolean wasActive = moduleAccess.active(module);
        sessionModules.add(new ManagedModuleState(module, wasActive, settingSnapshots));
        if (!wasActive) moduleAccess.toggle(module);
    }

    private ManagedModuleState managedState(Class<? extends Module> moduleClass) {
        for (ManagedModuleState state : sessionModules) {
            if (state.module.getClass() == moduleClass) return state;
        }

        return null;
    }

    private record ManagedModuleState(Module module, boolean wasActive, List<SettingSnapshot> settings) {
        void restoreSettings() {
            for (SettingSnapshot setting : settings) setting.restore();
        }
    }

    record SettingSnapshot(Setting<?> setting, NbtCompound tag) {
        void restore() {
            setting.fromTag(tag);
        }
    }

    static SettingSnapshot snapshot(Module module, String name) {
        Setting<?> setting = module.settings.get(name);
        if (setting == null) throw new IllegalStateException(module.name + " is missing setting: " + name);

        return new SettingSnapshot(setting, setting.toTag());
    }

    @SuppressWarnings("unchecked")
    static <T> Setting<T> setting(Module module, String name, Class<?> valueClass) {
        Setting<?> setting = module.settings.get(name);
        if (setting == null) throw new IllegalStateException(module.name + " is missing setting: " + name);
        if (!valueClass.isInstance(setting.get())) {
            throw new IllegalStateException(module.name + " setting has unexpected type: " + name);
        }

        return (Setting<T>) setting;
    }
}
