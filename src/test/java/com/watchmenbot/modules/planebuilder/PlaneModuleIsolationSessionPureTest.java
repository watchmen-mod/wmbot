package com.watchmenbot.modules.planebuilder;

import java.util.ArrayList;
import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneModuleIsolationSessionPureTest {
    private PlaneModuleIsolationSessionPureTest() {
    }

    static void run() {
        startDisablesOnlyOriginallyActiveNonOwnerModules();
        restoreReturnsToOriginalSnapshot();
        restoreDisablesModulesEnabledDuringSession();
        restoresOriginallyActiveCompanionAfterCompanionCleanup();
        startAndRestoreAreIdempotent();
    }

    private static void startDisablesOnlyOriginallyActiveNonOwnerModules() {
        FakeModule owner = new FakeModule(true);
        FakeModule active = new FakeModule(true);
        FakeModule inactive = new FakeModule(false);
        FakeAccess access = new FakeAccess(owner, active, inactive);
        PlaneModuleIsolationSession session = new PlaneModuleIsolationSession(access);

        session.start(owner);

        assertTrue(owner.active, "owner stays active");
        assertFalse(active.active, "active non-owner is disabled");
        assertFalse(inactive.active, "inactive module stays inactive");
        assertEquals(0, owner.toggleCount, "owner is never toggled");
        assertEquals(1, active.toggleCount, "active non-owner toggles off once");
        assertEquals(0, inactive.toggleCount, "inactive module is not toggled on start");
        assertTrue(session.active(), "session is marked active after start");
    }

    private static void restoreReturnsToOriginalSnapshot() {
        FakeModule owner = new FakeModule(true);
        FakeModule active = new FakeModule(true);
        FakeModule inactive = new FakeModule(false);
        PlaneModuleIsolationSession session = new PlaneModuleIsolationSession(new FakeAccess(owner, active, inactive));

        session.start(owner);
        session.restore(owner);

        assertTrue(owner.active, "owner remains active after restore");
        assertTrue(active.active, "originally active module is restored");
        assertFalse(inactive.active, "originally inactive module stays inactive");
        assertEquals(2, active.toggleCount, "originally active module toggles off then on");
        assertFalse(session.active(), "session is inactive after restore");
    }

    private static void restoreDisablesModulesEnabledDuringSession() {
        FakeModule owner = new FakeModule(true);
        FakeModule active = new FakeModule(true);
        FakeModule enabledDuringSession = new FakeModule(false);
        PlaneModuleIsolationSession session = new PlaneModuleIsolationSession(
            new FakeAccess(owner, active, enabledDuringSession)
        );

        session.start(owner);
        enabledDuringSession.toggle();
        session.restore(owner);

        assertTrue(active.active, "originally active module is restored");
        assertFalse(enabledDuringSession.active, "module enabled during session is disabled on restore");
        assertEquals(2, enabledDuringSession.toggleCount, "session-enabled module toggles on manually and off on restore");
    }

    private static void restoresOriginallyActiveCompanionAfterCompanionCleanup() {
        FakeModule owner = new FakeModule(true);
        FakeModule companion = new FakeModule(true);
        PlaneModuleIsolationSession session = new PlaneModuleIsolationSession(new FakeAccess(owner, companion));

        session.start(owner);
        companion.toggle();
        companion.toggle();
        session.restore(owner);

        assertTrue(companion.active, "originally active companion is restored after companion cleanup turns it off");
        assertEquals(4, companion.toggleCount, "companion toggles off, on for session, off for cleanup, on for restore");
    }

    private static void startAndRestoreAreIdempotent() {
        FakeModule owner = new FakeModule(true);
        FakeModule active = new FakeModule(true);
        PlaneModuleIsolationSession session = new PlaneModuleIsolationSession(new FakeAccess(owner, active));

        session.start(owner);
        session.start(owner);
        session.restore(owner);
        session.restore(owner);

        assertTrue(owner.active, "owner remains active after repeated calls");
        assertTrue(active.active, "active module is restored after repeated calls");
        assertEquals(0, owner.toggleCount, "owner is not toggled by repeated calls");
        assertEquals(2, active.toggleCount, "active module only toggles off once and on once");
    }

    private static final class FakeAccess implements PlaneModuleIsolationSession.ModuleAccess {
        private final List<FakeModule> modules;

        private FakeAccess(FakeModule... modules) {
            this.modules = new ArrayList<>(List.of(modules));
        }

        @Override
        public Iterable<?> all() {
            return modules;
        }

        @Override
        public boolean active(Object module) {
            return ((FakeModule) module).active;
        }

        @Override
        public void toggle(Object module) {
            ((FakeModule) module).toggle();
        }
    }

    private static final class FakeModule {
        private boolean active;
        private int toggleCount;

        private FakeModule(boolean active) {
            this.active = active;
        }

        private void toggle() {
            active = !active;
            toggleCount++;
        }
    }
}
