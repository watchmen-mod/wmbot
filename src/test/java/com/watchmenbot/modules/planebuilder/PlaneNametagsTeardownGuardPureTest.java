package com.watchmenbot.modules.planebuilder;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneNametagsTeardownGuardPureTest {
    private PlaneNametagsTeardownGuardPureTest() {
    }

    static void run() {
        activeNametagsIsSuspendedAndRestored();
        inactiveNametagsIsNotEnabledByRestore();
        missingNametagsIsNoOp();
        repeatedSuspendAndRestoreAreIdempotent();
        alreadyRestoredNametagsClearsSuspension();
    }

    private static void activeNametagsIsSuspendedAndRestored() {
        FakeModule nametags = new FakeModule(true);
        PlaneNametagsTeardownGuard guard = new PlaneNametagsTeardownGuard(new FakeAccess(nametags));

        guard.suspend();

        assertFalse(nametags.active, "active nametags is suspended");
        assertTrue(guard.suspended(), "guard records a personal suspension");

        guard.restore();

        assertTrue(nametags.active, "personally suspended nametags is restored");
        assertFalse(guard.suspended(), "guard clears suspension after restore");
        assertEquals(2, nametags.toggleCount, "nametags toggles off then on");
    }

    private static void inactiveNametagsIsNotEnabledByRestore() {
        FakeModule nametags = new FakeModule(false);
        PlaneNametagsTeardownGuard guard = new PlaneNametagsTeardownGuard(new FakeAccess(nametags));

        guard.suspend();
        guard.restore();

        assertFalse(nametags.active, "inactive nametags remains inactive");
        assertFalse(guard.suspended(), "guard does not record inactive nametags as suspended");
        assertEquals(0, nametags.toggleCount, "inactive nametags is never toggled");
    }

    private static void missingNametagsIsNoOp() {
        PlaneNametagsTeardownGuard guard = new PlaneNametagsTeardownGuard(new FakeAccess(null));

        guard.suspend();
        guard.restore();

        assertFalse(guard.suspended(), "missing nametags does not create a suspension");
    }

    private static void repeatedSuspendAndRestoreAreIdempotent() {
        FakeModule nametags = new FakeModule(true);
        PlaneNametagsTeardownGuard guard = new PlaneNametagsTeardownGuard(new FakeAccess(nametags));

        guard.suspend();
        guard.suspend();
        guard.restore();
        guard.restore();

        assertTrue(nametags.active, "nametags is restored after repeated calls");
        assertFalse(guard.suspended(), "guard is clear after repeated restores");
        assertEquals(2, nametags.toggleCount, "repeated calls only toggle off once and on once");
    }

    private static void alreadyRestoredNametagsClearsSuspension() {
        FakeModule nametags = new FakeModule(true);
        PlaneNametagsTeardownGuard guard = new PlaneNametagsTeardownGuard(new FakeAccess(nametags));

        guard.suspend();
        nametags.toggle();
        guard.restore();

        assertTrue(nametags.active, "manually restored nametags stays active");
        assertFalse(guard.suspended(), "guard clears suspension when module is already active");
        assertEquals(2, nametags.toggleCount, "guard does not toggle an already active module during restore");
    }

    private record FakeAccess(FakeModule nametags) implements PlaneNametagsTeardownGuard.ModuleAccess {
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
