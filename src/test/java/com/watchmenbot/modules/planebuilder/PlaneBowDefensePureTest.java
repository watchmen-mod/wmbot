package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

import java.util.ArrayList;
import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneBowDefensePureTest {
    private PlaneBowDefensePureTest() {
    }

    static void run() {
        gatesBowDefense();
        gatesBowDefenseRelease();
        gatesBowDefenseAimSuppression();
        keepsBowDefenseAvailableDuringReplenishPhases();
        latchesPassiveBowAimbotSession();
        keepsPassiveBowAimbotLatchedAcrossPassiveWindows();
        restoresOriginallyActivePassiveBowAimbotSession();
        restoresNormalShotSessionImmediately();
    }

    private static void gatesBowDefense() {
        assertTrue(
            PlaneBowDefenseDecisions.canRun(true, false, true, true, true, true),
            "enabled bow defense can run when every guard is satisfied"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(false, false, true, true, true, true),
            "disabled bow defense does not run"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(true, true, true, true, true, true),
            "raw bow defense gate still requires the coordinator to release replenish activity"
        );
        assertFalse(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.WAITING_FOR_KITBOT_REFILL, true),
            "kitbot wait releases active replenish for bow defense"
        );
        assertFalse(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.PICKING_UP_KITBOT_REFILL, true),
            "kitbot pickup movement releases active replenish for bow defense"
        );
        assertTrue(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.PLACING_ENDER_CHEST, true),
            "ender chest placement keeps active replenish closed for bow defense"
        );
        assertTrue(
            PlanePhasePolicy.bowDefenseReplenishActive(Phase.BREAKING_ENDER_CHEST, true),
            "ender chest breaking keeps active replenish closed for bow defense"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(true, false, false, true, true, true),
            "bow defense requires world action readiness"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(true, false, true, false, true, true),
            "bow defense requires a hotbar bow"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(true, false, true, true, false, true),
            "bow defense requires arrows"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canRun(true, false, true, true, true, false),
            "bow defense requires a visible target"
        );

        assertTrue(
            PlaneBowDefenseDecisions.canContinue(true, false),
            "active bow shot can continue while the controller owns use key"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canContinue(false, false),
            "active bow shot cancels when continue guard is not ready"
        );
        assertFalse(
            PlaneBowDefenseDecisions.canContinue(true, true),
            "raw bow continue gate still requires the coordinator to release replenish activity"
        );
    }

    private static void gatesBowDefenseRelease() {
        assertFalse(
            PlaneBowDefenseDecisions.shouldRelease(19, 20, true),
            "bow defense does not release before required charge"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldRelease(20, 20, false),
            "bow defense does not release without a direct-hit prediction"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldRelease(20, 20, true),
            "bow defense releases after charge when prediction hits locked target"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldCheckDirectHit(19, 20, 30, 3),
            "bow defense does not check direct-hit prediction before required charge"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldCheckDirectHit(20, 20, 2, 3),
            "bow defense lets aim settle before trusting direct-hit misses"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldCheckDirectHit(20, 20, 3, 3),
            "bow defense checks direct-hit prediction after charge and aim settle"
        );
        assertFalse(
            PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(29, 30),
            "bow defense keeps waiting before charged aim timeout"
        );
        assertTrue(
            PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(30, 30),
            "bow defense cancels at charged aim timeout"
        );
    }

    private static void gatesBowDefenseAimSuppression() {
        assertTrue(
            PlaneBowDefenseDecisions.suppressesTarget(42, 42, 60),
            "timed-out target is suppressed while cooldown remains"
        );
        assertFalse(
            PlaneBowDefenseDecisions.suppressesTarget(42, 43, 60),
            "different valid target remains selectable during suppression"
        );
        assertFalse(
            PlaneBowDefenseDecisions.suppressesTarget(42, 42, 0),
            "suppression expires when cooldown reaches zero"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldClearSuppression(0, true),
            "expired suppression clears even when target is still safe"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldClearSuppression(30, false),
            "suppression clears early when target becomes unsafe or disappears"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldClearSuppression(30, true),
            "active suppression remains while target is still safe"
        );
    }

    private static void keepsBowDefenseAvailableDuringReplenishPhases() {
        for (Phase phase : Phase.values()) {
            if (!phase.replenishActive()) continue;

            if (passiveBowDefensePhase(phase)) {
                assertTrue(
                    PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase),
                    phase + " keeps bow defense checks available"
                );
                assertFalse(
                    PlanePhasePolicy.bowDefenseReplenishActive(phase, true),
                    phase + " releases the raw replenish gate for bow defense"
                );
            } else {
                assertFalse(
                    PlanePhasePolicy.shouldKeepBowDefenseDuringReplenish(phase),
                    phase + " pauses bow defense checks during protected replenish work"
                );
                assertTrue(
                    PlanePhasePolicy.bowDefenseReplenishActive(phase, true),
                    phase + " keeps the raw replenish gate closed for bow defense"
                );
            }
        }
    }

    private static boolean passiveBowDefensePhase(Phase phase) {
        return phase == Phase.MISSING_OBSIDIAN
            || phase == Phase.SELECTING_SERVICE_HOLE
            || phase == Phase.SERVICE_HOLE_OPEN
            || phase == Phase.SELECTING_REPLENISH_SOURCE
            || phase == Phase.SERVICE_HOLE_BLOCKED
            || phase == Phase.MISSING_ENDER_CHEST
            || phase == Phase.MISSING_ENDER_CHEST_SHULKER
            || phase == Phase.WAITING_FOR_KITBOT_REFILL
            || phase == Phase.PICKING_UP_KITBOT_REFILL
            || phase == Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER
            || phase == Phase.PICKING_UP_REPLENISH_DROPS
            || phase == Phase.MOVING_TO_TRASH_EDGE
            || phase == Phase.WAITING_FOR_TRASH_TO_FALL;
    }

    private static void latchesPassiveBowAimbotSession() {
        FakeBowAimbotHandle bowAimbot = new FakeBowAimbotHandle(false);
        FakeHotbarSwapper hotbar = new FakeHotbarSwapper();
        PlaneBowModuleSession session = new PlaneBowModuleSession(bowAimbot, hotbar);

        assertTrue(session.startPassiveLatch(20.0), "passive latch starts when bow aimbot is available");
        assertTrue(bowAimbot.active, "passive latch enables inactive bow aimbot");
        assertEquals(1, bowAimbot.toggleCount, "passive latch toggles bow aimbot on once");
        assertEquals(1, bowAimbot.applySettingsCount, "passive latch applies settings once");

        assertTrue(session.startPassiveLatch(20.0), "second passive latch tick remains valid");
        assertEquals(1, bowAimbot.toggleCount, "second passive tick does not toggle again");
        assertEquals(1, bowAimbot.applySettingsCount, "second passive tick does not reapply settings");

        assertTrue(session.start(new FindItemResult(0, 1), 20.0, true), "passive shot can swap to bow");
        session.stopShot();
        assertTrue(bowAimbot.active, "stopping passive shot leaves latched bow aimbot active");
        assertEquals(1, hotbar.swapBackCount, "stopping passive shot swaps back once");
        assertEquals(0, bowAimbot.restoreCount(), "stopping passive shot does not restore passive settings");

        session.releasePassiveLatch();
        assertFalse(bowAimbot.active, "releasing passive latch restores inactive bow aimbot");
        assertEquals(2, bowAimbot.toggleCount, "passive latch toggles off on release");
        assertEquals(1, bowAimbot.restoreCount(), "passive latch restores settings on release");
    }

    private static void keepsPassiveBowAimbotLatchedAcrossPassiveWindows() {
        FakeBowAimbotHandle bowAimbot = new FakeBowAimbotHandle(false);
        PlaneBowModuleSession session = new PlaneBowModuleSession(bowAimbot, new FakeHotbarSwapper());

        assertTrue(session.startPassiveLatch(20.0), "first passive replenish window starts bow aimbot latch");
        assertTrue(session.startPassiveLatch(20.0), "next passive replenish window reuses existing bow aimbot latch");
        assertTrue(session.startPassiveLatch(20.0), "third passive replenish window still reuses existing bow aimbot latch");
        assertTrue(bowAimbot.active, "passive replenish windows keep bow aimbot active");
        assertEquals(1, bowAimbot.toggleCount, "passive-to-passive replenish windows do not toggle bow aimbot each tick");
        assertEquals(1, bowAimbot.applySettingsCount, "passive-to-passive replenish windows do not reapply settings each tick");

        session.releasePassiveLatch();
        assertFalse(bowAimbot.active, "protected replenish work can still release the passive latch");
        assertEquals(2, bowAimbot.toggleCount, "protected release toggles bow aimbot off once");
    }

    private static void restoresOriginallyActivePassiveBowAimbotSession() {
        FakeBowAimbotHandle bowAimbot = new FakeBowAimbotHandle(true);
        PlaneBowModuleSession session = new PlaneBowModuleSession(bowAimbot, new FakeHotbarSwapper());

        assertTrue(session.startPassiveLatch(20.0), "passive latch starts when bow aimbot is already active");
        assertTrue(bowAimbot.active, "passive latch keeps already active bow aimbot active");
        assertEquals(0, bowAimbot.toggleCount, "passive latch does not toggle already active bow aimbot");

        session.releasePassiveLatch();
        assertTrue(bowAimbot.active, "passive latch release preserves originally active bow aimbot");
        assertEquals(0, bowAimbot.toggleCount, "passive latch never toggles originally active bow aimbot");
        assertEquals(1, bowAimbot.restoreCount(), "passive latch restores settings for originally active bow aimbot");
    }

    private static void restoresNormalShotSessionImmediately() {
        FakeBowAimbotHandle bowAimbot = new FakeBowAimbotHandle(false);
        FakeHotbarSwapper hotbar = new FakeHotbarSwapper();
        PlaneBowModuleSession session = new PlaneBowModuleSession(bowAimbot, hotbar);

        assertTrue(session.start(new FindItemResult(0, 1), 20.0, false), "normal shot starts bow aimbot session");
        assertTrue(bowAimbot.active, "normal shot enables bow aimbot");
        assertEquals(1, bowAimbot.toggleCount, "normal shot toggles bow aimbot on");

        session.stopShot();
        assertFalse(bowAimbot.active, "normal shot restores inactive bow aimbot immediately");
        assertEquals(2, bowAimbot.toggleCount, "normal shot toggles bow aimbot off on stop");
        assertEquals(1, bowAimbot.restoreCount(), "normal shot restores settings on stop");
        assertEquals(1, hotbar.swapBackCount, "normal shot swaps back on stop");
    }

    private static final class FakeBowAimbotHandle implements PlaneBowModuleSession.BowAimbotHandle {
        private final List<FakeSettingSnapshot> snapshots = new ArrayList<>();
        private boolean active;
        private int toggleCount;
        private int applySettingsCount;

        private FakeBowAimbotHandle(boolean active) {
            this.active = active;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void toggle() {
            active = !active;
            toggleCount++;
        }

        @Override
        public List<PlaneBowModuleSession.RestorableSetting> applySettings(double range) {
            applySettingsCount++;
            FakeSettingSnapshot snapshot = new FakeSettingSnapshot();
            snapshots.add(snapshot);
            return List.of(snapshot);
        }

        private int restoreCount() {
            int restored = 0;
            for (FakeSettingSnapshot snapshot : snapshots) {
                if (snapshot.restored) restored++;
            }

            return restored;
        }
    }

    private static final class FakeSettingSnapshot implements PlaneBowModuleSession.RestorableSetting {
        private boolean restored;

        @Override
        public void restore() {
            restored = true;
        }
    }

    private static final class FakeHotbarSwapper implements PlaneBowModuleSession.HotbarSwapper {
        private int swapBackCount;

        @Override
        public boolean swapToHotbarSlot(int slot) {
            return true;
        }

        @Override
        public void swapBack() {
            swapBackCount++;
        }
    }
}
