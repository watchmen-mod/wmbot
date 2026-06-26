package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneRefactorPureTest {
    private PlaneRefactorPureTest() {
    }

    static void run() {
        exposesBuildConfigDefaults();
        exposesRuntimeConfigDefaults();
        controlsAutoWalkWithoutMinecraftRuntime();
        recoversPlacementWithBoundedNudge();
        controlsHoleEscapeWithoutMinecraftRuntime();
        controlsServiceHoleExitWithoutMinecraftRuntime();
        selectsBuildLoopResults();
        classifiesBuildBlockPreparationStates();
        classifiesInventoryPreparationDecisions();
        classifiesPickaxeDurabilityThresholds();
        classifiesPickaxeEligibility();
        classifiesPickaxePreparationDecisions();
        classifiesBowDurabilityThresholds();
        classifiesBowEligibility();
        classifiesBowPreparationDecisions();
        classifiesSwordDurabilityThresholds();
        classifiesSwordEligibility();
        classifiesSwordPreparationDecisions();
        validatesPlacementRequestsInTwoStages();
        projectsWorkflowResults();
        PlaneCoordinatorTickPolicyPureTest.run();
        coversReplenishTransitionPhases();
    }

    private static void exposesBuildConfigDefaults() {
        PlaneBuildConfig config = PlaneBuildConfig.DEFAULT;

        assertEquals(319, config.buildY(), "build config preserves plane height");
        assertEquals(-10000, config.minX(), "build config preserves minimum x");
        assertEquals(10000, config.maxZ(), "build config preserves maximum z");
        assertEquals(4, config.scanRadius(), "build config preserves scan radius");
        assertEquals(8, config.autoWalkLaneSpacing(), "build config preserves lane spacing");
        assertEquals(32, config.replenishMinBuildBlocks(), "build config preserves replenish threshold");
        assertEquals(128, config.replenishTargetBuildBlocks(), "build config preserves replenish target");
        assertEquals(2368, PlaneBuilderSettings.REPLENISH_MAX_OBSIDIAN, "settings cap preserves maximum carryable obsidian target");
    }

    private static void exposesRuntimeConfigDefaults() {
        PlaneRuntimeConfig config = PlaneRuntimeConfig.DEFAULT;

        assertEquals(PlaneBuildConfig.DEFAULT, config.build(), "runtime config wraps default build config");
        assertEquals(319, config.buildY(), "runtime config exposes plane height");
        assertEquals(4, config.scanRadius(), "runtime config exposes scan radius");
        assertEquals(32, config.replenishMinBuildBlocks(), "runtime config exposes replenish threshold");
    }

    private static void controlsAutoWalkWithoutMinecraftRuntime() {
        RecordingNavigator navigator = new RecordingNavigator();
        PlaneAutoWalkController disabled = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(false, 2),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 8, 0, 8), 1, 2),
            navigator
        );

        assertEquals(Phase.IDLE, disabled.tick(new BlockPos(0, 0, 0)), "disabled auto-walk stays idle");
        assertTrue(navigator.stopped, "disabled auto-walk stops movement");

        RecordingNavigator walkingNavigator = new RecordingNavigator();
        PlaneAutoWalkController walking = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 8, 0, 8), 1, 2),
            walkingNavigator
        );

        assertEquals(Phase.AUTO_WALKING, walking.tick(new BlockPos(4, 0, 4)), "enabled auto-walk starts walking");
        assertTrue(walkingNavigator.target != null, "enabled auto-walk emits a local target");

        RecordingNavigator reachedNavigator = new RecordingNavigator();
        PlaneAutoWalkController reached = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 16),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 0, 0, 0), 1, 1),
            reachedNavigator
        );

        assertEquals(Phase.IDLE, reached.tick(new BlockPos(0, 0, 0)), "reached final waypoint returns idle");
        assertTrue(reachedNavigator.stopped, "reached final waypoint stops movement");

        FixedAutoElytraWorld elytraWorld = new FixedAutoElytraWorld();
        for (int x = 5; x <= 25; x++) {
            elytraWorld.block(new BlockPos(x, 319, 0));
        }
        PlaneAutoElytraScanner elytraScanner = new PlaneAutoElytraScanner(
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            elytraWorld
        );
        RecordingNavigator elytraNavigator = new RecordingNavigator();
        PlaneAutoWalkController elytraWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            elytraNavigator,
            elytraScanner
        );
        assertEquals(Phase.AUTO_ELYTRA_FLYING, elytraWalk.tick(new BlockPos(4, 320, 0)), "blocked route starts auto elytra flight");
        assertTrue(elytraNavigator.flying, "auto elytra flight uses the navigator fly path");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(5, 0), elytraNavigator.target, "auto elytra starts with a route target on the snake lane");

        elytraWorld.blocking.clear();
        elytraWorld.solid.add(new BlockPos(4, 319, 0));
        assertEquals(
            Phase.AUTO_ELYTRA_FLYING,
            elytraWalk.tick(new BlockPos(4, 320, 3)),
            "clear route keeps flying until the navigator reports stable flight"
        );
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(5, 0), elytraNavigator.target, "off-route elytra travel is corrected back toward the same lane");
        assertEquals(null, elytraNavigator.landingTarget, "landing is not requested during the minimum flight window");

        elytraNavigator.readyToLand = true;
        assertEquals(
            Phase.AUTO_ELYTRA_FLYING,
            elytraWalk.tick(new BlockPos(4, 320, 3)),
            "stable clear route keeps correcting flight while outside the route corridor"
        );
        assertEquals(null, elytraNavigator.landingTarget, "off-route elytra travel does not land early");

        assertEquals(Phase.AUTO_ELYTRA_LANDING, elytraWalk.tick(new BlockPos(4, 320, 0)), "stable clear route starts landing once back inside the route corridor");
        assertEquals(new BlockPos(4, 320, 0), elytraNavigator.landingTarget, "landing targets the safe low plane position");

        elytraNavigator.landingComplete = true;
        assertEquals(Phase.IDLE, elytraWalk.tick(new BlockPos(4, 320, 0)), "completed landing releases auto elytra ownership");
        assertFalse(elytraNavigator.flying, "completed landing restores the navigator to non-flying state");

        for (int x = 5; x <= 25; x++) {
            elytraWorld.block(new BlockPos(x, 319, 0));
        }
        assertEquals(
            Phase.AUTO_WALKING,
            elytraWalk.tick(new BlockPos(4, 320, 0)),
            "post-landing cooldown walks instead of immediately relaunching on uncertain terrain"
        );
        assertFalse(elytraNavigator.flying, "post-landing cooldown does not call the elytra flight path");
        for (int i = 0; i < 39; i++) {
            assertEquals(
                Phase.AUTO_WALKING,
                elytraWalk.tick(new BlockPos(4, 320, 0)),
                "post-landing cooldown keeps walking until the grounded cooldown expires"
            );
        }
        assertEquals(
            Phase.AUTO_ELYTRA_FLYING,
            elytraWalk.tick(new BlockPos(4, 320, 0)),
            "stable blocked terrain can trigger auto elytra again after the grounded cooldown expires"
        );
        assertTrue(elytraNavigator.flying, "expired post-landing cooldown allows the navigator fly path again");

        FixedAutoElytraWorld hazardOnlyWorld = new FixedAutoElytraWorld();
        hazardOnlyWorld.hazard.add(new BlockPos(5, 319, 0));
        PlaneAutoElytraScanner hazardOnlyScanner = new PlaneAutoElytraScanner(
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            hazardOnlyWorld
        );
        RecordingNavigator hazardOnlyNavigator = new RecordingNavigator();
        PlaneAutoWalkController hazardOnlyWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            hazardOnlyNavigator,
            hazardOnlyScanner
        );
        assertEquals(Phase.AUTO_ELYTRA_FLYING, hazardOnlyWalk.tick(new BlockPos(4, 320, 0)), "single route-ahead hazard starts auto elytra flight");
        assertTrue(hazardOnlyNavigator.flying, "single hazard uses the navigator fly path");

        FixedAutoElytraWorld rerouteWorld = new FixedAutoElytraWorld();
        rerouteWorld.solid.add(new BlockPos(18, 319, 1));
        PlaneAutoElytraScanner rerouteScanner = new PlaneAutoElytraScanner(
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 8, 1, 8, 50, 32, 128), null),
            rerouteWorld
        );
        RecordingNavigator rerouteNavigator = new RecordingNavigator();
        rerouteNavigator.flying = true;
        PlaneAutoWalkController rerouteWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 8, 1, 8, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 8), 1, 8),
            rerouteNavigator,
            rerouteScanner
        );
        assertEquals(
            Phase.AUTO_ELYTRA_FLYING,
            rerouteWalk.tick(new BlockPos(10, 320, 1)),
            "airborne auto elytra keeps flying when no nearby landing target exists"
        );
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(18, 1),
            rerouteNavigator.target,
            "airborne auto elytra reroutes to the nearest safe forward continuation instead of hovering on the corrected route point"
        );

        FixedAutoElytraWorld failedElytraWorld = new FixedAutoElytraWorld();
        failedElytraWorld.hazard.add(new BlockPos(5, 319, 0));
        PlaneAutoElytraScanner failedElytraScanner = new PlaneAutoElytraScanner(
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            failedElytraWorld
        );
        MutableAutoWalkConfig failedElytraConfig = new MutableAutoWalkConfig(true, 1, true, 20);
        RecordingNavigator failedElytraNavigator = new RecordingNavigator();
        failedElytraNavigator.flySucceeds = false;
        PlaneAutoWalkController failedElytraWalk = new PlaneAutoWalkController(
            failedElytraConfig,
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            failedElytraNavigator,
            failedElytraScanner
        );
        assertEquals(Phase.AUTO_ELYTRA_FLYING, failedElytraWalk.tick(new BlockPos(4, 320, 0)), "failed elytra launch still reports the attempted auto elytra phase for this tick");
        assertFalse(failedElytraConfig.autoElytraFlyEnabled(), "failed elytra launch disables the auto elytra setting");
        assertFalse(failedElytraNavigator.flying, "failed elytra launch does not leave navigator flying");

        FixedAutoElytraWorld hazardCooldownWorld = new FixedAutoElytraWorld();
        for (int x = 5; x <= 25; x++) {
            hazardCooldownWorld.block(new BlockPos(x, 319, 0));
        }
        PlaneAutoElytraScanner hazardCooldownScanner = new PlaneAutoElytraScanner(
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            hazardCooldownWorld
        );
        RecordingNavigator hazardCooldownNavigator = new RecordingNavigator();
        PlaneAutoWalkController hazardCooldownWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            hazardCooldownNavigator,
            hazardCooldownScanner
        );
        assertEquals(Phase.AUTO_ELYTRA_FLYING, hazardCooldownWalk.tick(new BlockPos(4, 320, 0)), "blocked route starts setup flight for hazard cooldown test");
        hazardCooldownWorld.blocking.clear();
        hazardCooldownWorld.solid.clear();
        hazardCooldownWorld.solid.add(new BlockPos(4, 319, 0));
        hazardCooldownNavigator.readyToLand = true;
        assertEquals(Phase.AUTO_ELYTRA_LANDING, hazardCooldownWalk.tick(new BlockPos(4, 320, 0)), "hazard cooldown setup starts landing");
        hazardCooldownNavigator.landingComplete = true;
        assertEquals(Phase.IDLE, hazardCooldownWalk.tick(new BlockPos(4, 320, 0)), "hazard cooldown setup completes landing");
        hazardCooldownWorld.hazard.add(new BlockPos(5, 319, 0));
        assertEquals(
            Phase.AUTO_ELYTRA_FLYING,
            hazardCooldownWalk.tick(new BlockPos(4, 320, 0)),
            "single route-ahead hazard bypasses post-landing grounded cooldown"
        );
        assertTrue(hazardCooldownNavigator.flying, "hazard cooldown bypass uses the auto elytra flight path");

        RecordingNavigator lockedNavigator = new RecordingNavigator();
        PlaneAutoWalkController lockedWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            lockedNavigator,
            elytraScanner
        );
        lockedWalk.lockAutoElytra(PlaneAutoWalkController.LockoutReason.BOW_DEFENSE);
        for (int x = 5; x <= 25; x++) {
            elytraWorld.block(new BlockPos(x, 319, 0));
        }
        elytraWorld.hazard.add(new BlockPos(5, 319, 0));
        assertEquals(Phase.AUTO_WALKING, lockedWalk.tick(new BlockPos(4, 320, 0)), "mission lockout falls back to normal walking instead of relaunching elytra");
        assertFalse(lockedNavigator.flying, "locked out auto-walk does not call the elytra flight path");
        lockedWalk.releaseAutoElytraLockout();
        assertEquals(Phase.AUTO_ELYTRA_FLYING, lockedWalk.tick(new BlockPos(4, 320, 0)), "released lockout allows route-corrected elytra flight again");

        RecordingNavigator missionNavigator = new RecordingNavigator();
        PlaneAutoWalkController missionLanding = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1, true, 20),
            new PlaneRuntimeConfig(new PlaneBuildConfig(319, 0, 40, 0, 0, 1, 1, 50, 32, 128), null),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 0), 1, 1),
            missionNavigator,
            elytraScanner
        );
        missionNavigator.flying = true;
        missionNavigator.readyToLand = true;
        missionNavigator.landingComplete = true;
        assertEquals(
            Phase.IDLE,
            missionLanding.landBeforeWorldAction(new BlockPos(4, 320, 0), PlaneAutoWalkController.LockoutReason.GUARD_PAUSED),
            "completed mission landing releases flight ownership immediately"
        );
        assertTrue(missionLanding.autoElytraLockedOut(), "completed mission landing keeps auto elytra locked out until traversal resumes");

        RecordingNavigator nudgeNavigator = new RecordingNavigator();
        PlaneAutoWalkController nudgingWalk = new PlaneAutoWalkController(
            new FixedAutoWalkConfig(true, 1),
            new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 8, 0, 8), 1, 2),
            nudgeNavigator
        );
        BlockPos placementTarget = new BlockPos(3, 319, 4);
        nudgingWalk.nudgeTowardPlacementTarget(placementTarget);
        assertEquals(placementTarget, nudgeNavigator.nudgeTarget, "placement recovery can request a grounded nudge without advancing the route");
        assertEquals(1, nudgeNavigator.nudgeCount, "placement recovery nudge is delegated to the navigator");

        nudgeNavigator.flying = true;
        nudgingWalk.nudgeTowardPlacementTarget(new BlockPos(4, 319, 4));
        assertEquals(1, nudgeNavigator.nudgeCount, "placement recovery does not nudge while auto elytra is still airborne");
    }

    private static void recoversPlacementWithBoundedNudge() {
        PlanePlacementRecovery recovery = new PlanePlacementRecovery();
        BlockPos target = new BlockPos(1, 319, 1);
        BlockPos otherTarget = new BlockPos(2, 319, 1);

        recovery.targetObserved(target);
        recovery.placementDispatched(target);
        recovery.placementDispatched(target);
        assertEquals(null, recovery.activeNudgeTarget(target), "two same-target attempts do not nudge yet");

        recovery.placementDispatched(target);
        assertTrue(recovery.nudging(), "third same-target attempt arms placement recovery");
        for (int i = 0; i < PlanePlacementRecovery.NUDGE_TICKS; i++) {
            assertEquals(target, recovery.activeNudgeTarget(target), "armed recovery nudges for the configured tick window");
        }
        assertEquals(null, recovery.activeNudgeTarget(target), "placement recovery stops after the bounded nudge window");

        recovery.placementDispatched(target);
        recovery.placementDispatched(target);
        recovery.targetObserved(otherTarget);
        recovery.placementDispatched(otherTarget);
        assertEquals(null, recovery.activeNudgeTarget(otherTarget), "changing targets resets repeated-attempt recovery");

        recovery.placementDispatched(otherTarget);
        recovery.placementDispatched(otherTarget);
        assertTrue(recovery.nudging(), "recovery can arm again after a new target repeats");
        recovery.reset();
        assertEquals(null, recovery.activeNudgeTarget(otherTarget), "reset clears active placement recovery nudges");
    }

    private static void controlsHoleEscapeWithoutMinecraftRuntime() {
        BlockPos playerPos = new BlockPos(0, 64, 0);
        FixedHoleEscapeWorld trappedWorld = FixedHoleEscapeWorld.trapped(playerPos);
        RecordingHoleEscapeNavigator navigator = new RecordingHoleEscapeNavigator();
        PlaneHoleEscapeController enabled = new PlaneHoleEscapeController(
            new FixedHoleEscapeConfig(true),
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-4, 4, -4, 4), trappedWorld),
            navigator
        );

        assertEquals(Phase.HOLE_ESCAPE, enabled.tick(playerPos), "enabled hole escape starts pathing when trapped");
        assertEquals(new BlockPos(0, 65, -1), navigator.target, "enabled hole escape emits adjacent standing target");

        RecordingHoleEscapeNavigator disabledNavigator = new RecordingHoleEscapeNavigator();
        PlaneHoleEscapeController disabled = new PlaneHoleEscapeController(
            new FixedHoleEscapeConfig(false),
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-4, 4, -4, 4), trappedWorld),
            disabledNavigator
        );

        assertEquals(Phase.IDLE, disabled.tick(playerPos), "disabled hole escape stays idle");
        assertTrue(disabledNavigator.stopped, "disabled hole escape stops navigator");

        trappedWorld.openNorth(playerPos);
        assertEquals(Phase.IDLE, enabled.tick(playerPos), "leaving the hole stops escape");
        assertTrue(navigator.stopped, "leaving the hole stops navigator");
    }

    private static void controlsServiceHoleExitWithoutMinecraftRuntime() {
        BlockPos serviceHole = new BlockPos(0, 64, 0);
        PlaneAreaBounds bounds = new PlaneAreaBounds(-4, 4, -4, 4);
        FixedServiceHoleExitWorld northOpen = FixedServiceHoleExitWorld.withFloors(
            serviceHole.add(0, 0, -1)
        );
        ServiceHoleExitPlanner planner = new ServiceHoleExitPlanner(bounds, northOpen);

        assertEquals(
            serviceHole.add(0, 1, -1),
            planner.exitTarget(serviceHole, serviceHole),
            "player inside selected service hole exits to north standing target"
        );
        assertEquals(
            null,
            planner.exitTarget(serviceHole.add(1, 1, 0), serviceHole),
            "player outside selected service hole does not need service-hole exit"
        );
        assertEquals(
            null,
            planner.exitTarget(serviceHole.up(), serviceHole),
            "player standing above the selected service hole is no longer treated as inside it"
        );

        FixedServiceHoleExitWorld diagonalFallback = FixedServiceHoleExitWorld.withFloors(
            serviceHole.add(0, 0, -1),
            serviceHole.add(1, 0, 0),
            serviceHole.add(0, 0, 1),
            serviceHole.add(-1, 0, 0),
            serviceHole.add(1, 0, -1)
        );
        diagonalFallback.block(serviceHole.add(0, 1, -1));
        diagonalFallback.block(serviceHole.add(1, 1, 0));
        diagonalFallback.block(serviceHole.add(0, 1, 1));
        diagonalFallback.block(serviceHole.add(-1, 1, 0));
        assertEquals(
            serviceHole.add(1, 1, -1),
            new ServiceHoleExitPlanner(bounds, diagonalFallback).exitTarget(serviceHole, serviceHole),
            "blocked cardinal exits fall back to diagonal standing target"
        );

        assertEquals(
            null,
            new ServiceHoleExitPlanner(bounds, FixedServiceHoleExitWorld.withFloors()).exitTarget(serviceHole, serviceHole),
            "missing adjacent standing target reports no service-hole exit target"
        );

        RecordingHoleEscapeNavigator navigator = new RecordingHoleEscapeNavigator();
        ServiceHoleExitWorkflow workflow = new ServiceHoleExitWorkflow(planner, navigator);
        ServiceHoleExitWorkflow.ExitResult active = workflow.tick(serviceHole, serviceHole);
        assertTrue(active.active(), "service-hole exit workflow pauses placement while player is inside hole");
        assertEquals(serviceHole.add(0, 1, -1), navigator.target, "service-hole exit workflow paths to adjacent target");
        ServiceHoleExitWorkflow.ExitResult outside = workflow.tick(serviceHole.add(1, 1, 0), serviceHole);
        assertFalse(outside.active(), "service-hole exit workflow releases placement once player is outside hole");
        assertTrue(navigator.stopped, "service-hole exit workflow stops navigator after player leaves hole");
    }

    private static void selectsBuildLoopResults() {
        BuildTickResult lowSupply = PlaneBuildLoopDecisions.resultFor(31, 32, true, true, Phase.IDLE);
        assertEquals(BuildTickResult.startingReplenish(), lowSupply, "low build block count starts replenish");
        assertTrue(lowSupply.startReplenish(), "low build block count is a replenish transition");

        BuildTickResult unpreparedWithSupply = PlaneBuildLoopDecisions.resultFor(32, 32, false, true, Phase.IDLE);
        assertEquals(BuildTickResult.missingObsidian(), unpreparedWithSupply, "missing prepared block reports missing obsidian");
        assertFalse(unpreparedWithSupply.startReplenish(), "sufficient supply without a prepared block stays recoverable");

        assertEquals(
            BuildTickResult.phase(Phase.AUTO_WALKING),
            PlaneBuildLoopDecisions.resultFor(32, 32, true, false, Phase.AUTO_WALKING),
            "missing target returns auto-walk phase"
        );
        assertEquals(
            BuildTickResult.phase(Phase.PLACING_OBSIDIAN),
            PlaneBuildLoopDecisions.resultFor(32, 32, true, true, Phase.IDLE),
            "valid target returns placement phase"
        );
    }

    private static void classifiesBuildBlockPreparationStates() {
        FindItemResult hotbar = new FindItemResult(0, 64);
        BuildBlockPreparation alreadyUsable = BuildBlockPreparation.alreadyUsable(hotbar);
        assertEquals(hotbar, alreadyUsable.result(), "already usable preparation preserves the usable slot");
        assertTrue(alreadyUsable.alreadyUsable(), "already usable preparation records hotbar or offhand availability");
        assertFalse(alreadyUsable.hotbarPromotionAttempted(), "already usable preparation does not promote");
        assertTrue(alreadyUsable.buildBlockFound(), "already usable preparation found build blocks");

        FindItemResult missingResult = new FindItemResult(-1, 0);
        BuildBlockPreparation missing = BuildBlockPreparation.missing(missingResult);
        assertEquals(missingResult, missing.result(), "missing preparation preserves the failed lookup");
        assertFalse(missing.buildBlockFound(), "missing preparation records absent build blocks");

        FindItemResult promotedResult = new FindItemResult(4, 64);
        BuildBlockPreparation promoted = BuildBlockPreparation.afterHotbarPromotion(promotedResult);
        assertEquals(promotedResult, promoted.result(), "promoted preparation returns the post-promotion lookup");
        assertTrue(promoted.mainInventorySourceFound(), "promoted preparation records a main inventory source");
        assertTrue(promoted.hotbarPromotionAttempted(), "promoted preparation records the hotbar mutation attempt");
        assertTrue(promoted.buildBlockFound(), "promoted preparation found build blocks");
    }

    private static void classifiesInventoryPreparationDecisions() {
        FindItemResult hotbar = new FindItemResult(1, 64);
        assertEquals(
            BuildBlockPreparation.alreadyUsable(hotbar),
            PlaneInventoryPreparation.buildBlockPreparation(hotbar, true, false, new FindItemResult(2, 64)),
            "already usable hotbar result wins without promotion"
        );

        FindItemResult missing = new FindItemResult(-1, 0);
        assertEquals(
            BuildBlockPreparation.missing(missing),
            PlaneInventoryPreparation.buildBlockPreparation(missing, false, false, new FindItemResult(2, 64)),
            "missing main inventory source reports missing build block"
        );

        FindItemResult promoted = new FindItemResult(2, 64);
        assertEquals(
            BuildBlockPreparation.afterHotbarPromotion(promoted),
            PlaneInventoryPreparation.buildBlockPreparation(missing, false, true, promoted),
            "main inventory source promotes and returns post-promotion lookup"
        );
    }

    private static void classifiesPickaxePreparationDecisions() {
        FindItemResult hotbar = new FindItemResult(1, 1);
        assertEquals(
            hotbar,
            PlaneInventoryPreparation.pickaxePreparation(hotbar, true, false, new FindItemResult(2, 1)),
            "safe hotbar pickaxe wins without promotion"
        );

        FindItemResult missing = null;
        assertEquals(
            null,
            PlaneInventoryPreparation.pickaxePreparation(missing, false, false, new FindItemResult(2, 1)),
            "unsafe hotbar pickaxe without safe main inventory replacement returns missing"
        );

        FindItemResult promoted = new FindItemResult(2, 1);
        assertEquals(
            promoted,
            PlaneInventoryPreparation.pickaxePreparation(missing, false, true, promoted),
            "unsafe hotbar pickaxe promotes a safe main inventory pickaxe"
        );
    }

    private static void classifiesBowDurabilityThresholds() {
        assertTrue(
            PlaneItemClassifier.remainingDurabilityPercent(100, 90) >= 10,
            "10 percent remaining with threshold 10 is usable for bows"
        );
        assertFalse(
            PlaneItemClassifier.remainingDurabilityPercent(100, 91) >= 10,
            "9 percent remaining with threshold 10 is unsafe for bows"
        );
    }

    private static void classifiesBowEligibility() {
        assertFalse(
            PlaneItemClassifier.isUsableBow(false, 100, 0, 10),
            "non-bow is rejected"
        );
        assertTrue(
            PlaneItemClassifier.isUsableBow(true, 100, 90, 10),
            "bow exactly at threshold is accepted"
        );
        assertFalse(
            PlaneItemClassifier.isUsableBow(true, 100, 91, 10),
            "bow below threshold is rejected"
        );
        assertFalse(
            PlaneItemClassifier.isUsableBow(true, 0, 0, 10),
            "non-damageable bow state is rejected"
        );
    }

    private static void classifiesBowPreparationDecisions() {
        FindItemResult hotbar = new FindItemResult(1, 1);
        assertEquals(
            hotbar,
            PlaneInventoryPreparation.bowPreparation(hotbar, true, false, new FindItemResult(2, 1)),
            "usable hotbar bow wins without promotion"
        );

        FindItemResult missing = null;
        assertEquals(
            null,
            PlaneInventoryPreparation.bowPreparation(missing, false, false, new FindItemResult(2, 1)),
            "unsafe hotbar bow without usable main inventory replacement returns missing"
        );

        FindItemResult promoted = new FindItemResult(2, 1);
        assertEquals(
            promoted,
            PlaneInventoryPreparation.bowPreparation(missing, false, true, promoted),
            "unsafe hotbar bow promotes a usable main inventory bow"
        );
    }

    private static void classifiesSwordDurabilityThresholds() {
        assertTrue(
            PlaneItemClassifier.remainingDurabilityPercent(100, 90) >= 10,
            "10 percent remaining with threshold 10 is usable for swords"
        );
        assertFalse(
            PlaneItemClassifier.remainingDurabilityPercent(100, 91) >= 10,
            "9 percent remaining with threshold 10 is unsafe for swords"
        );
    }

    private static void classifiesSwordEligibility() {
        assertFalse(
            PlaneItemClassifier.isUsableSword(false, 100, 0, 10),
            "non-sword is rejected"
        );
        assertTrue(
            PlaneItemClassifier.isUsableSword(true, 100, 90, 10),
            "sword exactly at threshold is accepted"
        );
        assertFalse(
            PlaneItemClassifier.isUsableSword(true, 100, 91, 10),
            "sword below threshold is rejected"
        );
        assertFalse(
            PlaneItemClassifier.isUsableSword(true, 0, 0, 10),
            "non-damageable sword state is rejected"
        );
    }

    private static void classifiesSwordPreparationDecisions() {
        FindItemResult hotbar = new FindItemResult(1, 1);
        assertEquals(
            hotbar,
            PlaneInventoryPreparation.swordPreparation(hotbar, true, false, new FindItemResult(2, 1)),
            "usable hotbar sword wins without promotion"
        );

        FindItemResult missing = null;
        assertEquals(
            null,
            PlaneInventoryPreparation.swordPreparation(missing, false, false, new FindItemResult(2, 1)),
            "unsafe hotbar sword without usable main inventory replacement returns missing"
        );

        FindItemResult promoted = new FindItemResult(2, 1);
        assertEquals(
            promoted,
            PlaneInventoryPreparation.swordPreparation(missing, false, true, promoted),
            "unsafe hotbar sword promotes a usable main inventory sword"
        );
    }

    private static void classifiesPickaxeDurabilityThresholds() {
        assertTrue(
            PlaneItemClassifier.remainingDurabilityPercent(100, 89) >= 10,
            "11 percent remaining with threshold 10 is usable"
        );
        assertFalse(
            PlaneItemClassifier.remainingDurabilityPercent(100, 91) >= 10,
            "9 percent remaining with threshold 10 is unsafe"
        );
        assertTrue(
            PlaneItemClassifier.remainingDurabilityPercent(100, 90) >= 10,
            "exactly 10 percent remaining with threshold 10 is usable"
        );
    }

    private static void classifiesPickaxeEligibility() {
        assertFalse(
            PlaneItemClassifier.isUsablePickaxe(false, false, 100, 0, 10),
            "non-pickaxe is rejected"
        );
        assertFalse(
            PlaneItemClassifier.isUsablePickaxe(true, true, 100, 0, 10),
            "Silk Touch pickaxe is rejected"
        );
        assertTrue(
            PlaneItemClassifier.isUsablePickaxe(true, false, 100, 89, 10),
            "non-Silk Touch pickaxe above threshold is accepted"
        );
        assertFalse(
            PlaneItemClassifier.isUsablePickaxe(true, false, 100, 91, 10),
            "non-Silk Touch pickaxe below threshold is rejected"
        );
    }

    private static void validatesPlacementRequestsInTwoStages() {
        BlockPos target = new BlockPos(1, 2, 3);
        BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false);
        PlacementRequest request = new PlacementRequest(
            target,
            new FindItemResult(0, 1),
            null,
            Vec3d.ofCenter(target),
            hitResult,
            Hand.MAIN_HAND,
            () -> true,
            () -> false
        );

        assertTrue(request.valid(), "pre-swap placement validation can pass before hand is prepared");
        assertFalse(request.preparedValid(), "prepared validation fails until the selected hand matches");
    }

    private static void projectsWorkflowResults() {
        assertTrue(new BowDefenseTickResult(true).active(), "bow result projects active state");
        assertEquals(
            Phase.WAITING_FOR_KITBOT_REFILL,
            new ReplenishTickResult(Phase.WAITING_FOR_KITBOT_REFILL, true).phase(),
            "replenish result projects display phase"
        );
        assertTrue(
            new ReplenishTickResult(Phase.WAITING_FOR_KITBOT_REFILL, true).bowDefenseAllowed(),
            "replenish result projects bow allowance"
        );
    }

    private static void coversReplenishTransitionPhases() {
        Set<Phase> transitionPhases = PlaneReplenishWorkflow.transitionPhases();

        for (Phase phase : Phase.values()) {
            if (!phase.replenishActive()) continue;

            assertTrue(transitionPhases.contains(phase), phase + " has a registered replenish transition");
        }
    }

    private record FixedAutoWalkConfig(
        boolean enabled,
        int waypointRadius,
        boolean autoElytraFlyEnabled,
        int autoElytraSolidLookahead
    ) implements PlaneAutoWalkController.AutoWalkConfig {
        private FixedAutoWalkConfig(boolean enabled, int waypointRadius) {
            this(enabled, waypointRadius, false, 20);
        }
    }

    private static final class MutableAutoWalkConfig implements PlaneAutoWalkController.AutoWalkConfig {
        private final boolean enabled;
        private final int waypointRadius;
        private final int autoElytraSolidLookahead;
        private boolean autoElytraFlyEnabled;

        private MutableAutoWalkConfig(boolean enabled, int waypointRadius, boolean autoElytraFlyEnabled, int autoElytraSolidLookahead) {
            this.enabled = enabled;
            this.waypointRadius = waypointRadius;
            this.autoElytraFlyEnabled = autoElytraFlyEnabled;
            this.autoElytraSolidLookahead = autoElytraSolidLookahead;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public int waypointRadius() {
            return waypointRadius;
        }

        @Override
        public boolean autoElytraFlyEnabled() {
            return autoElytraFlyEnabled;
        }

        @Override
        public void disableAutoElytraFly() {
            autoElytraFlyEnabled = false;
        }

        @Override
        public int autoElytraSolidLookahead() {
            return autoElytraSolidLookahead;
        }
    }

    private record FixedHoleEscapeConfig(boolean enabled) implements PlaneHoleEscapeController.Config {
    }

    private static final class RecordingNavigator implements PlaneAutoWalkController.Navigator {
        private PlaneAutoWalkPlanner.Waypoint target;
        private BlockPos landingTarget;
        private boolean stopped;
        private boolean flying;
        private boolean readyToLand;
        private boolean landingComplete;
        private boolean flySucceeds = true;
        private BlockPos nudgeTarget;
        private int nudgeCount;

        @Override
        public void walkTo(PlaneAutoWalkPlanner.Waypoint nextTarget) {
            target = nextTarget;
            flying = false;
        }

        @Override
        public boolean flyTo(PlaneAutoWalkPlanner.Waypoint nextTarget, double minY, double maxY) {
            if (!flySucceeds) return false;

            target = nextTarget;
            flying = true;
            return true;
        }

        @Override
        public boolean landAt(BlockPos target) {
            landingTarget = target;
            if (landingComplete) flying = false;
            return landingComplete;
        }

        @Override
        public boolean flying() {
            return flying;
        }

        @Override
        public boolean readyToLand() {
            return readyToLand;
        }

        @Override
        public void nudgeToward(BlockPos target) {
            nudgeTarget = target;
            nudgeCount++;
        }

        @Override
        public void stop() {
            stopped = true;
            flying = false;
        }
    }

    private static final class FixedAutoElytraWorld implements PlaneAutoElytraScanner.BlockView {
        private final Set<BlockPos> blocking = new java.util.HashSet<>();
        private final Set<BlockPos> solid = new java.util.HashSet<>();
        private final Set<BlockPos> hazard = new java.util.HashSet<>();

        void block(BlockPos pos) {
            blocking.add(pos);
            solid.add(pos);
        }

        @Override
        public boolean blocking(BlockPos pos) {
            return blocking.contains(pos) || hazard.contains(pos);
        }

        @Override
        public boolean solid(BlockPos pos) {
            return solid.contains(pos);
        }

        @Override
        public boolean hazard(BlockPos pos) {
            return hazard.contains(pos);
        }

        @Override
        public boolean passable(BlockPos pos) {
            return !solid.contains(pos);
        }
    }

    private static final class RecordingHoleEscapeNavigator implements PlaneHoleEscapeController.Navigator {
        private BlockPos target;
        private boolean stopped;

        @Override
        public void pathTo(BlockPos target) {
            this.target = target;
            stopped = false;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private static final class FixedHoleEscapeWorld implements PlaneHoleEscapePlanner.BlockView {
        private final Set<BlockPos> solid;

        private FixedHoleEscapeWorld(Set<BlockPos> solid) {
            this.solid = solid;
        }

        static FixedHoleEscapeWorld trapped(BlockPos playerPos) {
            Set<BlockPos> solid = new java.util.HashSet<>();
            solid.add(playerPos.down());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    solid.add(playerPos.add(dx, 0, dz));
                }
            }

            return new FixedHoleEscapeWorld(solid);
        }

        void openNorth(BlockPos playerPos) {
            solid.remove(playerPos.add(0, 0, -1));
        }

        @Override
        public boolean passable(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean solid(BlockPos pos) {
            return solid.contains(pos);
        }
    }

    private static final class FixedServiceHoleExitWorld implements ServiceHoleExitPlanner.BlockView {
        private final Set<BlockPos> solid = new java.util.HashSet<>();

        static FixedServiceHoleExitWorld withFloors(BlockPos... floors) {
            FixedServiceHoleExitWorld world = new FixedServiceHoleExitWorld();
            for (BlockPos floor : floors) {
                world.solid.add(floor);
            }

            return world;
        }

        void block(BlockPos pos) {
            solid.add(pos);
        }

        @Override
        public boolean passable(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean solid(BlockPos pos) {
            return solid.contains(pos);
        }
    }
}
