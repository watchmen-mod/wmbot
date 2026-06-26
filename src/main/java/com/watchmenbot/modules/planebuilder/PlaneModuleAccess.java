package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.nbt.NbtCompound;

import java.util.Collection;

final class PlaneModuleAccess {
    Module get(Class<? extends Module> moduleClass) {
        return Modules.get().get(moduleClass);
    }

    Collection<Module> all() {
        return Modules.get().getAll();
    }

    boolean active(Module module) {
        return module.isActive();
    }

    void toggle(Module module) {
        module.toggle();
    }

    PlaneModuleSettingSnapshot snapshot(Module module, String name) {
        Setting<?> setting = module.settings.get(name);
        if (setting == null) throw new IllegalStateException(module.name + " is missing setting: " + name);

        return new PlaneModuleSettingSnapshot(setting, setting.toTag());
    }

    @SuppressWarnings("unchecked")
    <T> Setting<T> setting(Module module, String name, Class<?> valueClass) {
        Setting<?> setting = module.settings.get(name);
        if (setting == null) throw new IllegalStateException(module.name + " is missing setting: " + name);
        if (!valueClass.isInstance(setting.get())) {
            throw new IllegalStateException(module.name + " setting has unexpected type: " + name);
        }

        return (Setting<T>) setting;
    }

    record PlaneModuleSettingSnapshot(Setting<?> setting, NbtCompound tag) {
        void restore() {
            setting.fromTag(tag);
        }
    }
}
