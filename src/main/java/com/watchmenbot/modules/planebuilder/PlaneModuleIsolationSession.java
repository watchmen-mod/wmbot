package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.Module;

import java.util.ArrayList;
import java.util.List;

final class PlaneModuleIsolationSession {
    private final ModuleAccess moduleAccess;
    private final List<Object> originallyActive = new ArrayList<>();
    private boolean applied;

    PlaneModuleIsolationSession() {
        this(new MeteorModuleAccess(new PlaneModuleAccess()));
    }

    PlaneModuleIsolationSession(ModuleAccess moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    void start(Module owner) {
        start((Object) owner);
    }

    void start(Object owner) {
        if (applied) return;

        originallyActive.clear();
        for (Object module : moduleAccess.all()) {
            if (module == owner) continue;
            if (!moduleAccess.active(module)) continue;

            originallyActive.add(module);
            moduleAccess.toggle(module);
        }

        applied = true;
    }

    void restore(Module owner) {
        restore((Object) owner);
    }

    void restore(Object owner) {
        if (!applied) return;

        for (Object module : moduleAccess.all()) {
            if (module == owner) continue;
            if (moduleAccess.active(module) && !wasOriginallyActive(module)) {
                moduleAccess.toggle(module);
            }
        }

        for (Object module : originallyActive) {
            if (!moduleAccess.active(module)) moduleAccess.toggle(module);
        }

        originallyActive.clear();
        applied = false;
    }

    void suspendActiveNonOwnerModules(Module owner) {
        suspendActiveNonOwnerModules((Object) owner);
    }

    void suspendActiveNonOwnerModules(Object owner) {
        if (!applied) return;

        for (Object module : moduleAccess.all()) {
            if (module == owner) continue;
            if (moduleAccess.active(module)) moduleAccess.toggle(module);
        }
    }

    boolean active() {
        return applied;
    }

    private boolean wasOriginallyActive(Object module) {
        for (Object activeModule : originallyActive) {
            if (activeModule == module) return true;
        }

        return false;
    }

    interface ModuleAccess {
        Iterable<?> all();

        boolean active(Object module);

        void toggle(Object module);
    }

    private record MeteorModuleAccess(PlaneModuleAccess moduleAccess) implements ModuleAccess {
        @Override
        public Iterable<Module> all() {
            return moduleAccess.all();
        }

        @Override
        public boolean active(Object module) {
            return moduleAccess.active((Module) module);
        }

        @Override
        public void toggle(Object module) {
            moduleAccess.toggle((Module) module);
        }
    }
}
