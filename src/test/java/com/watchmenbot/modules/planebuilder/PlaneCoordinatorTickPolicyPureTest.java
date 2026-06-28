package com.watchmenbot.modules.planebuilder;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneCoordinatorTickPolicyPureTest {
    private PlaneCoordinatorTickPolicyPureTest() {
    }

    static void run() {
        selectsCoordinatorTickOwners();
    }

    private static void selectsCoordinatorTickOwners() {
        assertEquals(
            PlaneCoordinatorTickPolicy.TickOwner.REPLENISH,
            PlaneCoordinatorTickPolicy.owner(true, true, true, true),
            "replenish owns tick before bow defense"
        );
        assertEquals(
            PlaneCoordinatorTickPolicy.TickOwner.MELEE_DEFENSE,
            PlaneCoordinatorTickPolicy.owner(false, true, true, true),
            "close melee defense owns tick before bow defense and build"
        );
        assertEquals(
            PlaneCoordinatorTickPolicy.TickOwner.BOW_DEFENSE,
            PlaneCoordinatorTickPolicy.owner(false, false, true, false),
            "active bow defense owns tick before guard pause"
        );
        assertEquals(
            PlaneCoordinatorTickPolicy.TickOwner.GUARD_PAUSED,
            PlaneCoordinatorTickPolicy.owner(false, false, false, false),
            "guards pause world actions when no subsystem owns tick"
        );
        assertEquals(
            PlaneCoordinatorTickPolicy.TickOwner.BUILD_LOOP,
            PlaneCoordinatorTickPolicy.owner(false, false, false, true),
            "build loop owns normal ready tick"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldContinueAutoElytraLanding(Phase.AUTO_ELYTRA_LANDING),
            "auto elytra landing is protected before other coordinator owners"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldContinueAutoElytraLanding(Phase.AUTO_ELYTRA_FLYING),
            "auto elytra flying is not protected as an in-progress landing"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.WAITING_FOR_KITBOT_REFILL, true)),
            "explicit replenish result can allow bow defense"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.PLACING_ENDER_CHEST, false)),
            "ender chest placement suppresses bow defense while item placement owns the phase"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.PLACING_ENDER_CHEST_SHULKER, false)),
            "shulker placement suppresses bow defense while item placement owns the phase"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.PICKING_UP_REPLENISH_DROPS, false)),
            "cleanup pickup allows bow defense to pause movement"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldResetBowDefenseAfterReplenishTick(new ReplenishTickResult(Phase.PICKING_UP_REPLENISH_DROPS, false)),
            "cleanup pickup does not reset passive bow defense after the replenish phase is known"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER, false)),
            "missing shulker pickup allows bow defense to pause movement"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.BREAKING_ENDER_CHEST, false)),
            "ender chest breaking suppresses bow defense while pickaxe owns the phase"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldResetBowDefenseAfterReplenishTick(new ReplenishTickResult(Phase.BREAKING_ENDER_CHEST, false)),
            "protected ender chest breaking resets bow defense after the replenish phase is known"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.BREAKING_ENDER_CHEST_SHULKER, false)),
            "shulker breaking suppresses bow defense while pickaxe owns the phase"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.TAKING_ENDER_CHESTS_FROM_SHULKER, false)),
            "shulker extraction suppresses bow defense while inventory actions own the phase"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.MOVING_TO_TRASH_EDGE, false)),
            "trash edge movement allows bow defense to pause movement"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickBowDefenseDuringReplenish(new ReplenishTickResult(Phase.DROPPING_TRASH_OFF_EDGE, false)),
            "trash dropping suppresses bow defense while hotbar actions own the phase"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(Phase.PLACING_ENDER_CHEST, true, false),
            "active bow defense does not preempt ender chest placement"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(Phase.SERVICE_HOLE_OPEN, true, false),
            "active bow defense does not preempt the service-hole-open bridge phase"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(Phase.BREAKING_ENDER_CHEST, false, true),
            "active item use preempts replenish so Auto Eat can finish"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(Phase.WAITING_FOR_TRASH_TO_FALL),
            "trash fall wait can advance its timeout while safety preempts replenish"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(Phase.DROPPING_TRASH_OFF_EDGE),
            "trash dropping is protected from safety-preempted replenish ticks"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(Phase.MOVING_TO_TRASH_EDGE),
            "trash edge movement is protected from safety-preempted replenish ticks"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldTickReplenishDuringSafetyPreemption(Phase.PLACING_ENDER_CHEST),
            "ender chest placement is protected from safety-preempted replenish ticks"
        );
        for (Phase phase : Phase.values()) {
            if (!phase.replenishActive()) continue;

            if (passiveBowDefensePhase(phase)) {
                assertTrue(
                    PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(phase, true, false),
                    phase + " can be preempted by active bow defense"
                );
            } else {
                assertFalse(
                    PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(phase, true, false),
                    phase + " keeps protected replenish work ahead of active bow defense"
                );
            }
            assertTrue(
                PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(phase, false, true),
                phase + " can be preempted by Auto Eat item use"
            );
        }
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldPreemptReplenishForSafety(Phase.PLACING_ENDER_CHEST, false, false),
            "replenish continues when no safety action needs the tick"
        );
        assertTrue(
            PlaneActionGuards.safeToCloseManagedScreen(false, true),
            "managed screen with empty cursor can close for safety"
        );
        assertFalse(
            PlaneActionGuards.safeToCloseManagedScreen(false, false),
            "managed screen with cursor item stays paused instead of closing"
        );
        assertFalse(
            PlaneActionGuards.safeToCloseManagedScreen(true, true),
            "player inventory screen is not treated as a managed screen"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.CLOSING_SERVICE_HOLE),
            "normal service-hole closing allows hole escape before replenish"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL),
            "kitbot refill service-hole closing allows hole escape before replenish"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.MOVING_TO_TRASH_EDGE),
            "trash edge movement allows hole escape before direct walking"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.SERVICE_HOLE_BLOCKED),
            "blocked service-hole recovery allows hole escape before retrying replenish"
        );
        assertTrue(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.BREAKING_ENDER_CHEST_SHULKER),
            "shulker breaking allows hole escape before retrying replenish"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.PLACING_ENDER_CHEST),
            "ender chest farming does not release replenish for hole escape"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.TAKING_ENDER_CHESTS_FROM_SHULKER),
            "shulker extraction does not release replenish for hole escape"
        );
        assertFalse(
            PlaneCoordinatorTickPolicy.shouldCheckHoleEscapeDuringReplenish(Phase.WAITING_FOR_KITBOT_REFILL),
            "kitbot wait does not release replenish for hole escape"
        );
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
}
