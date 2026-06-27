package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneBowDefensePureTest {
    private PlaneBowDefensePureTest() {
    }

    static void run() {
        gatesBowDefense();
        gatesBowDefenseRelease();
        gatesReliableBowFiringPolicy();
        solvesBowAimForStationaryTargets();
        selectsCenterMassAimPoint();
        leadsMovingBowTargets();
        rejectsImpossibleBowAim();
        keepsBowDefenseAvailableDuringReplenishPhases();
        startsShotSessionWithoutBowAimbot();
        restoresShotSession();
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
            PlaneBowDefenseDecisions.shouldRelease(24, 25, 2, 2),
            "bow defense holds past vanilla full charge for a reliable full-draw release"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldRelease(25, 25, 1, 2),
            "bow defense does not release before the direct-hit prediction is stable"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldRelease(25, 25, 2, 2),
            "bow defense releases after buffered full charge when prediction is stable on the locked target"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldReleaseWhenPredictionUnavailable(24, 25, 8, 8),
            "unavailable prediction fallback still waits for buffered full draw"
        );
        assertFalse(
            PlaneBowDefenseDecisions.shouldReleaseWhenPredictionUnavailable(25, 25, 7, 8),
            "unavailable prediction fallback requires sustained valid aim"
        );
        assertTrue(
            PlaneBowDefenseDecisions.shouldReleaseWhenPredictionUnavailable(25, 25, 8, 8),
            "unavailable prediction fallback releases only after sustained valid full-draw aim"
        );
        assertFalse(
            PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(29, 30),
            "bow defense keeps trying direct-hit shots before aim timeout"
        );
        assertTrue(
            PlaneBowDefenseDecisions.timedOutWaitingForDirectHit(30, 30),
            "bow defense cancels the current shot at aim timeout"
        );
    }

    private static void gatesReliableBowFiringPolicy() {
        assertFalse(PlaneBowFiringPolicy.drawStarted(false, 30), "draw is not confirmed without using-item state");
        assertFalse(PlaneBowFiringPolicy.drawStarted(true, 0), "draw is not confirmed before use ticks advance");
        assertTrue(PlaneBowFiringPolicy.drawStarted(true, 1), "draw is confirmed from real use ticks");
        assertTrue(PlaneBowFiringPolicy.drawStartTimedOut(PlaneBowFiringPolicy.DRAW_START_TIMEOUT_TICKS), "draw start timeout trips at the configured limit");
        assertFalse(PlaneBowFiringPolicy.enoughDraw(29), "reliable draw does not release before 30 real use ticks");
        assertTrue(PlaneBowFiringPolicy.enoughDraw(30), "reliable draw uses 30 real use ticks");
        assertTrue(PlaneBowFiringPolicy.drawStalled(false, 10, 9, 0), "draw stalls when item use stops");
        assertTrue(PlaneBowFiringPolicy.drawStalled(true, 10, 10, PlaneBowFiringPolicy.DRAW_STALL_TIMEOUT_TICKS), "draw stalls when use ticks stop advancing");

        PlaneBowAimController.Aim previous = new PlaneBowAimController.Aim(10.0, -5.0, Vec3d.ZERO);
        PlaneBowAimController.Aim stable = new PlaneBowAimController.Aim(11.0, -4.5, Vec3d.ZERO);
        PlaneBowAimController.Aim unstable = new PlaneBowAimController.Aim(15.0, -4.5, Vec3d.ZERO);
        assertTrue(PlaneBowFiringPolicy.aimStable(previous, stable), "small aim changes count as settled");
        assertFalse(PlaneBowFiringPolicy.aimStable(previous, unstable), "large aim changes reset settle confidence");
        assertFalse(
            PlaneBowFiringPolicy.shouldReleaseDirect(30, PlaneBowFiringPolicy.REQUIRED_AIM_SETTLE_TICKS - 1, PlaneBowFiringPolicy.REQUIRED_DIRECT_HIT_TICKS),
            "direct release waits for rotation settle"
        );
        assertTrue(
            PlaneBowFiringPolicy.shouldReleaseDirect(30, PlaneBowFiringPolicy.REQUIRED_AIM_SETTLE_TICKS, PlaneBowFiringPolicy.REQUIRED_DIRECT_HIT_TICKS),
            "direct release requires draw, settle, and direct-hit confidence"
        );
        assertFalse(
            PlaneBowFiringPolicy.shouldReleaseFallback(
                30,
                PlaneBowFiringPolicy.REQUIRED_AIM_SETTLE_TICKS,
                PlaneBowFiringPolicy.REQUIRED_FALLBACK_AIM_TICKS,
                PlaneBowFiringPolicy.MAX_FALLBACK_TARGET_SPEED_SQUARED + 0.01
            ),
            "fallback release rejects fast-moving targets"
        );
        assertTrue(
            PlaneBowFiringPolicy.shouldReleaseFallback(
                30,
                PlaneBowFiringPolicy.REQUIRED_AIM_SETTLE_TICKS,
                PlaneBowFiringPolicy.REQUIRED_FALLBACK_AIM_TICKS,
                0.0
            ),
            "fallback release allows stable targets after strict confidence gates"
        );
    }

    private static void solvesBowAimForStationaryTargets() {
        Vec3d shooter = new Vec3d(0.0, 65.6, 0.0);

        assertValidAim(shooter, new Vec3d(4.6, 65.6, 0.0), "close stationary target has a bow aim solution");
        assertValidAim(shooter, new Vec3d(12.0, 65.6, 0.0), "mid-range stationary target has a bow aim solution");
        assertValidAim(shooter, new Vec3d(20.0, 65.6, 0.0), "long stationary target has a bow aim solution");
    }

    private static void selectsCenterMassAimPoint() {
        Vec3d point = PlaneBowAimController.aimPoint(new Box(1.0, 64.0, 2.0, 2.0, 66.0, 3.0));
        assertEquals(new Vec3d(1.5, 65.1, 2.5), point, "bow aim point targets center mass instead of feet or head");
    }

    private static void leadsMovingBowTargets() {
        Vec3d shooter = new Vec3d(0.0, 65.6, 0.0);
        Vec3d target = new Vec3d(12.0, 65.6, 0.0);

        PlaneBowAimController.Aim stationary = PlaneBowAimController.solve(shooter, target, Vec3d.ZERO, 20).orElseThrow();
        PlaneBowAimController.Aim moving = PlaneBowAimController.solve(shooter, target, new Vec3d(0.2, 0.0, 0.0), 20).orElseThrow();

        assertTrue(
            moving.aimedTarget().x > stationary.aimedTarget().x,
            "moving target aim leads the target along its velocity"
        );
    }

    private static void rejectsImpossibleBowAim() {
        Optional<PlaneBowAimController.Aim> aim = PlaneBowAimController.solve(
            new Vec3d(0.0, 65.6, 0.0),
            new Vec3d(100.0, 95.6, 0.0),
            Vec3d.ZERO,
            5
        );

        assertTrue(aim.isEmpty(), "impossible low-charge high-distance shot returns no bow aim");
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

    private static void startsShotSessionWithoutBowAimbot() {
        FakeHotbarSwapper hotbar = new FakeHotbarSwapper();
        PlaneBowModuleSession session = new PlaneBowModuleSession(hotbar);

        assertTrue(session.start(new FindItemResult(0, 1)), "normal shot starts without requiring bow aimbot");
        assertTrue(session.active(), "normal shot session is active after swapping to the bow");
        assertEquals(0, hotbar.swapBackCount, "start does not immediately swap back");
    }

    private static void restoresShotSession() {
        FakeHotbarSwapper hotbar = new FakeHotbarSwapper();
        PlaneBowModuleSession session = new PlaneBowModuleSession(hotbar);

        assertTrue(session.start(new FindItemResult(0, 1)), "normal shot starts bow session");

        session.stopShot();
        assertFalse(session.active(), "normal shot session is inactive after stop");
        assertEquals(1, hotbar.swapBackCount, "normal shot swaps back on stop");
    }

    private static void assertValidAim(Vec3d shooter, Vec3d target, String message) {
        Optional<PlaneBowAimController.Aim> aim = PlaneBowAimController.solve(shooter, target, Vec3d.ZERO, 20);
        assertTrue(aim.isPresent(), message);
        assertTrue(!Double.isNaN(aim.get().yaw()) && !Double.isNaN(aim.get().pitch()), message + " with finite rotation");
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
