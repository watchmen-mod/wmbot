package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;

final class PlaneNametagsTeardownGuard {
    private final ModuleAccess moduleAccess;
    private Object suspendedModule;

    PlaneNametagsTeardownGuard() {
        this(new MeteorModuleAccess());
    }

    PlaneNametagsTeardownGuard(ModuleAccess moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    void suspend() {
        if (suspendedModule != null) return;

        Object module = moduleAccess.nametags();
        if (module == null || !moduleAccess.active(module)) return;

        moduleAccess.toggle(module);
        suspendedModule = module;
    }

    void restore() {
        if (suspendedModule == null) return;

        if (!moduleAccess.active(suspendedModule)) moduleAccess.toggle(suspendedModule);
        suspendedModule = null;
    }

    boolean suspended() {
        return suspendedModule != null;
    }

    interface ModuleAccess {
        Object nametags();

        boolean active(Object module);

        void toggle(Object module);
    }

    private static final class MeteorModuleAccess implements ModuleAccess {
        @Override
        public Object nametags() {
            return Modules.get().get(Nametags.class);
        }

        @Override
        public boolean active(Object module) {
            return ((Module) module).isActive();
        }

        @Override
        public void toggle(Object module) {
            ((Module) module).toggle();
        }
    }
}
