package com.watchmenbot.modules.planebuilder;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneUtilityPureTest {
    private PlaneUtilityPureTest() {
    }

    static void run() {
        selectsHotbarSwapTargets();
        boundsMainInventorySlots();
        classifiesUsableElytra();
        computesBuildAreaAndScanBounds();
        validatesMovementSafetyPolicy();
        budgetsHotbarMutations();
        budgetsShulkerExtractionPhases();
        plansAutoWalkSnakeWaypoints();
        correctsAutoElytraTargetsToSnakeRoute();
        classifiesServiceHoleSupport();
        classifiesServiceHoleCandidates();
        selectsHoleEscapeTargets();
        selectsTrashEdgeTargets();
        selectsKillAuraMobGroups();
        selectsBowDefenseMobTargets();
        detectsUnsafeEndermanLooks();
        detectsAutoElytraObstructionsAndLandingTargets();
    }

    private static void selectsHotbarSwapTargets() {
        assertEquals(
            4,
            PlaneInventoryMover.hotbarSwapTarget(4, hotbarEmpties(4)),
            "empty selected hotbar slot is preferred"
        );
        assertEquals(
            2,
            PlaneInventoryMover.hotbarSwapTarget(4, hotbarEmpties(2, 7)),
            "first empty hotbar slot is fallback"
        );
        assertEquals(
            4,
            PlaneInventoryMover.hotbarSwapTarget(4, hotbarEmpties()),
            "selected hotbar slot is overwrite fallback"
        );
        assertEquals(
            -1,
            PlaneInventoryMover.hotbarSwapTarget(12, hotbarEmpties()),
            "invalid selected slot is rejected when hotbar is full"
        );
    }

    private static void boundsMainInventorySlots() {
        assertFalse(PlaneInventoryView.isMainInventorySlot(8, 41), "hotbar slots are not main inventory promotion sources");
        assertTrue(PlaneInventoryView.isMainInventorySlot(9, 41), "first main inventory slot is a promotion source");
        assertTrue(PlaneInventoryView.isMainInventorySlot(35, 41), "last main inventory slot is a promotion source");
        assertFalse(PlaneInventoryView.isMainInventorySlot(36, 41), "armor slots are not main inventory promotion sources");
        assertFalse(PlaneInventoryView.isMainInventorySlot(40, 41), "offhand slot is not a main inventory promotion source");
        assertEquals(36, PlaneInventoryView.mainInventoryEnd(41), "combined inventories stop main scans before armor and offhand");
        assertEquals(27, PlaneInventoryView.mainInventoryEnd(27), "short inventories keep their real end");
    }

    private static void classifiesUsableElytra() {
        assertTrue(PlaneItemClassifier.isUsableElytra(true, 432, 0), "fresh elytra is usable");
        assertTrue(PlaneItemClassifier.isUsableElytra(true, 432, 430), "elytra with two durability remaining is usable");
        assertFalse(PlaneItemClassifier.isUsableElytra(true, 432, 431), "elytra with one durability remaining is treated as broken");
        assertFalse(PlaneItemClassifier.isUsableElytra(false, 432, 0), "non-elytra item is not usable elytra");
        assertFalse(PlaneItemClassifier.isUsableElytra(true, 0, 0), "non-damageable elytra-like item is not usable");
    }

    private static void computesBuildAreaAndScanBounds() {
        PlaneAreaBounds buildArea = PlaneAreaBounds.buildArea();
        assertTrue(buildArea.contains(0, 0), "origin is inside build area");
        assertTrue(buildArea.contains(-10000, 10000), "configured edges are inside build area");
        assertFalse(buildArea.contains(-10001, 0), "x below minimum is outside build area");
        assertFalse(buildArea.contains(0, 10001), "z above maximum is outside build area");

        assertEquals(
            new PlaneAreaBounds(-4, 4, -4, 4),
            PlaneAreaBounds.scanWindow(0, 0, 4),
            "scan window centers around player"
        );
        assertEquals(
            new PlaneAreaBounds(9996, 10000, 9996, 10000),
            PlaneAreaBounds.scanWindow(10000, 10000, 4),
            "scan window clamps at positive build edge"
        );
        assertEquals(
            new PlaneAreaBounds(-10000, -9996, -10000, -9996),
            PlaneAreaBounds.scanWindow(-10000, -10000, 4),
            "scan window clamps at negative build edge"
        );
    }

    private static void validatesMovementSafetyPolicy() {
        PlaneRuntimeConfig config = new PlaneRuntimeConfig(
            new PlaneBuildConfig(10, -5, 5, -5, 5, 4, 8, -50, 32, 128),
            null
        );
        PlaneMovementSafetyPolicy policy = new PlaneMovementSafetyPolicy(config, 6);
        BlockPos player = new BlockPos(0, 10, 0);

        assertTrue(policy.validatePlatformGoal(player, new BlockPos(3, 10, 3)).accepted(), "in-bounds platform goal is accepted");
        assertEquals(
            PlaneMovementSafetyPolicy.RejectReason.BELOW_PLATFORM,
            policy.validatePlatformGoal(player, new BlockPos(3, 9, 3)).reason(),
            "below-platform goal is rejected"
        );
        assertEquals(
            PlaneMovementSafetyPolicy.RejectReason.OUTSIDE_BUILD_AREA,
            policy.validatePlatformGoal(player, new BlockPos(6, 10, 0)).reason(),
            "out-of-bounds goal is rejected"
        );
        assertEquals(
            PlaneMovementSafetyPolicy.RejectReason.TOO_FAR,
            policy.validatePlatformGoal(player, new BlockPos(5, 10, 5)).reason(),
            "distant goal is rejected"
        );
        assertTrue(PlaneMovementSafetyPolicy.isSnowHazardId("minecraft:snow"), "snow layers are hazardous");
        assertTrue(PlaneMovementSafetyPolicy.isSnowHazardId("minecraft:powder_snow"), "powder snow is hazardous");
        assertFalse(PlaneMovementSafetyPolicy.isSnowHazardId("minecraft:obsidian"), "build blocks are not snow hazards");
    }

    private static void budgetsHotbarMutations() {
        PlaneHotbarMutationGuard guard = new PlaneHotbarMutationGuard(2, 1);
        assertTrue(guard.allow("pickaxe", 12, 0, 0), "first hotbar mutation is allowed");
        assertTrue(guard.allow("pickaxe", 12, 0, 0), "second repeated hotbar mutation is allowed");
        assertFalse(guard.allow("pickaxe", 12, 0, 0), "third repeated hotbar mutation is blocked");
        assertFalse(guard.allow("pickaxe", 12, 0, 0), "cooldown tick blocks immediate retry");
        assertTrue(guard.allow("pickaxe", 12, 0, 0), "mutation gets a fresh budget after cooldown");
    }

    private static void budgetsShulkerExtractionPhases() {
        EnderChestShulkerExtractionBudget budget = new EnderChestShulkerExtractionBudget(2, 2);
        assertFalse(budget.timedOut(Phase.OPENING_ENDER_CHEST_SHULKER), "first phase tick is within budget");
        assertFalse(budget.timedOut(Phase.OPENING_ENDER_CHEST_SHULKER), "second phase tick is within budget");
        assertTrue(budget.timedOut(Phase.OPENING_ENDER_CHEST_SHULKER), "third phase tick times out");

        budget.reset();
        assertFalse(budget.stalledTake(1), "first stalled take tick is allowed");
        assertFalse(budget.stalledTake(1), "second stalled take tick is allowed");
        assertTrue(budget.stalledTake(1), "third stalled take tick is blocked");
        assertFalse(budget.stalledTake(2), "increased loose ender chest count resets stall tracking");
    }

    private static void plansAutoWalkSnakeWaypoints() {
        PlaneAutoWalkPlanner planner = new PlaneAutoWalkPlanner();
        PlaneAutoWalkPlanner samePlanner = new PlaneAutoWalkPlanner();
        var waypoints = planner.waypoints();

        assertEquals(waypoints, samePlanner.waypoints(), "same configured plane produces identical auto-walk waypoints");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(-9996, -9996), waypoints.get(0), "first snake waypoint is inset from min edge");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(9996, -9996), waypoints.get(1), "first lane crosses east");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(9996, -9988), waypoints.get(2), "second lane starts from prior side");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(-9996, -9988), waypoints.get(3), "second lane crosses west");
        assertEquals(9996, waypoints.get(waypoints.size() - 1).z(), "last lane is inset from max edge");
        assertEquals(PlaneBuilderSettings.MIN_X, waypoints.get(0).x() - PlaneBuilderSettings.SCAN_RADIUS, "inset first waypoint still covers configured minimum x edge");
        assertEquals(PlaneBuilderSettings.MAX_X, waypoints.get(1).x() + PlaneBuilderSettings.SCAN_RADIUS, "inset first lane endpoint still covers configured maximum x edge");

        for (int i = 2; i < waypoints.size(); i += 2) {
            int previousLaneZ = waypoints.get(i - 2).z();
            int laneZ = waypoints.get(i).z();
            assertTrue(laneZ - previousLaneZ <= PlaneBuilderSettings.AUTO_WALK_LANE_SPACING, "lane spacing never exceeds scan coverage");
        }

        PlaneAutoWalkPlanner.AutoWalkState middleState = planner.initialState(0, 0);
        assertFalse(middleState.index() == 0, "initial auto walk state is selected near the player instead of waypoint zero");

        PlaneAutoWalkPlanner.AutoWalkState firstLaneState = planner.initialState(9000, -9995);
        assertEquals(1, firstLaneState.index(), "near the first lane end starts with that local lane target");
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(9004, -9996),
            planner.localTarget(firstLaneState, 9000, -9996, PlaneBuilderSettings.SCAN_RADIUS),
            "local target advances within the scan horizon instead of jumping to the far endpoint"
        );
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(9003, -9996),
            planner.localTarget(firstLaneState, 9000, -9994, PlaneBuilderSettings.SCAN_RADIUS),
            "east-west drift keeps the deterministic lane z and spends movement budget correcting"
        );

        PlaneAutoWalkPlanner.Segment firstLaneSegment = planner.segment(firstLaneState);
        assertEquals(
            new PlaneAutoWalkPlanner.Segment(
                new PlaneAutoWalkPlanner.Waypoint(-9996, -9996),
                new PlaneAutoWalkPlanner.Waypoint(9996, -9996),
                PlaneAutoWalkPlanner.SegmentAxis.X
            ),
            firstLaneSegment,
            "segment metadata is derived from deterministic route waypoints"
        );
        assertFalse(
            planner.endpointReached(firstLaneState, 3000, -9996, 2),
            "mid-lane position does not count as reaching the far edge endpoint"
        );
        assertTrue(
            planner.compatibleWithSegment(firstLaneState, 3000, -9988, PlaneBuilderSettings.SCAN_RADIUS * 3),
            "side drift near a neighboring lane is still compatible with the active long lane"
        );
        assertFalse(
            planner.compatibleWithSegment(firstLaneState, 3000, -9970, PlaneBuilderSettings.SCAN_RADIUS * 3),
            "large off-lane movement is not compatible with the active long lane"
        );

        PlaneAutoWalkPlanner.AutoWalkState nextLaneState = planner.advance(new PlaneAutoWalkPlanner.AutoWalkState(1, 1));
        assertEquals(new PlaneAutoWalkPlanner.AutoWalkState(2, 1), nextLaneState, "edge advances to the adjacent lane connector");
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(9996, -9992),
            planner.localTarget(nextLaneState, 9996, -9996, PlaneBuilderSettings.SCAN_RADIUS),
            "edge connector steps toward the next lane within the scan horizon"
        );
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(9996, -9993),
            planner.localTarget(nextLaneState, 9998, -9996, PlaneBuilderSettings.SCAN_RADIUS),
            "connector drift keeps the deterministic edge x and spends movement budget correcting"
        );

        PlaneAutoWalkPlanner.Segment connectorSegment = planner.segment(nextLaneState);
        assertEquals(
            new PlaneAutoWalkPlanner.Segment(
                new PlaneAutoWalkPlanner.Waypoint(9996, -9996),
                new PlaneAutoWalkPlanner.Waypoint(9996, -9988),
                PlaneAutoWalkPlanner.SegmentAxis.Z
            ),
            connectorSegment,
            "connector segment keeps the predetermined edge x"
        );

        PlaneAutoWalkPlanner customPlanner = new PlaneAutoWalkPlanner(new PlaneAreaBounds(10, 30, 100, 116), 2, 4);
        assertEquals(
            List.of(
                new PlaneAutoWalkPlanner.Waypoint(12, 102),
                new PlaneAutoWalkPlanner.Waypoint(28, 102),
                new PlaneAutoWalkPlanner.Waypoint(28, 106),
                new PlaneAutoWalkPlanner.Waypoint(12, 106),
                new PlaneAutoWalkPlanner.Waypoint(12, 110),
                new PlaneAutoWalkPlanner.Waypoint(28, 110),
                new PlaneAutoWalkPlanner.Waypoint(28, 114),
                new PlaneAutoWalkPlanner.Waypoint(12, 114)
            ),
            customPlanner.waypoints(),
            "explicit bounds, scan radius, and lane spacing fully determine route geometry"
        );

        PlaneAutoWalkPlanner.AutoWalkState reversedAtEnd = planner.advance(new PlaneAutoWalkPlanner.AutoWalkState(waypoints.size() - 1, 1));
        assertEquals(new PlaneAutoWalkPlanner.AutoWalkState(waypoints.size() - 2, -1), reversedAtEnd, "route reverses at the final waypoint");

        PlaneAutoWalkPlanner.AutoWalkState reversedAtStart = planner.advance(new PlaneAutoWalkPlanner.AutoWalkState(0, -1));
        assertEquals(new PlaneAutoWalkPlanner.AutoWalkState(1, 1), reversedAtStart, "route reverses forward before the first waypoint");

        PlaneAutoWalkPlanner tinyPlanner = new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 3, 0, 3), 4, 8);
        assertEquals(1, tinyPlanner.waypoints().size(), "tiny bounds produce one non-duplicated waypoint");
        assertEquals(new PlaneAutoWalkPlanner.Waypoint(1, 1), tinyPlanner.waypoints().get(0), "tiny waypoint is centered in the bounded area");
    }

    private static void classifiesServiceHoleSupport() {
        assertTrue(PlaneAreaScanner.serviceSupportUsable(false, true), "solid occupied support is usable");
        assertFalse(PlaneAreaScanner.serviceSupportUsable(true, true), "replaceable support should be filled first");
        assertFalse(PlaneAreaScanner.serviceSupportUsable(false, false), "non-solid occupied support is blocked");
    }

    private static void classifiesServiceHoleCandidates() {
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.OPEN_SUPPORTED,
            PlaneAreaScanner.serviceHoleCandidateKind(false, false, true, true, false),
            "already-open supported service hole is selectable"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.CAPPED_SUPPORTED,
            PlaneAreaScanner.serviceHoleCandidateKind(true, false, false, true, false),
            "obsidian capped service hole with valid support is selectable"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.CAPPED_NEEDS_SUPPORT,
            PlaneAreaScanner.serviceHoleCandidateKind(true, false, false, false, true),
            "obsidian capped service hole with replaceable support is selectable after support placement"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.CAPPED_SUPPORTED,
            PlaneAreaScanner.serviceHoleCandidateKind(false, true, false, true, false),
            "breakable terrain capped service hole with valid support is selectable"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.CAPPED_NEEDS_SUPPORT,
            PlaneAreaScanner.serviceHoleCandidateKind(false, true, false, false, true),
            "breakable terrain capped service hole with replaceable support is selectable after support placement"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.NONE,
            PlaneAreaScanner.serviceHoleCandidateKind(true, false, false, false, false),
            "non-solid occupied support is rejected"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.NONE,
            PlaneAreaScanner.serviceHoleCandidateKind(false, false, true, false, true),
            "open replaceable hole without valid support is rejected"
        );
        assertEquals(
            PlaneAreaScanner.ServiceHoleCandidate.NONE,
            PlaneAreaScanner.serviceHoleCandidateKind(false, false, false, true, false),
            "arbitrary supported non-service block is rejected"
        );
        assertTrue(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, true, true, true, false, false),
            "generic solid breakable center is a service hole cap"
        );
        assertFalse(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, true, true, false, false, false),
            "unbreakable center is rejected as a service hole cap"
        );
        assertFalse(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, false, true, true, false, false),
            "non-solid occupied center is rejected as a service hole cap"
        );
        assertFalse(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, true, true, true, true, false),
            "ender chest center is handled by workflow rather than terrain cap selection"
        );
        assertFalse(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, true, true, true, false, true),
            "shulker center is handled by workflow rather than terrain cap selection"
        );
        assertFalse(
            PlaneAreaScanner.breakableServiceHoleCap(false, false, true, false, true, false, false),
            "fluid center is rejected as a service hole cap"
        );

        assertTrue(
            PlaneAreaScanner.ServiceHoleCandidate.OPEN_SUPPORTED.priority()
                < PlaneAreaScanner.ServiceHoleCandidate.CAPPED_SUPPORTED.priority(),
            "already-open supported service holes are preferred over capped supported holes"
        );
        assertTrue(
            PlaneAreaScanner.ServiceHoleCandidate.CAPPED_SUPPORTED.priority()
                < PlaneAreaScanner.ServiceHoleCandidate.CAPPED_NEEDS_SUPPORT.priority(),
            "capped supported service holes are preferred over holes that still need support"
        );
    }

    private static void correctsAutoElytraTargetsToSnakeRoute() {
        PlaneAutoWalkPlanner planner = new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 40, 0, 8), 1, 8);
        PlaneAutoWalkPlanner.AutoWalkState eastLane = new PlaneAutoWalkPlanner.AutoWalkState(1, 1);

        PlaneAutoWalkPlanner.RouteCorrection xDrift = planner.correctedTarget(eastLane, 10, 3, 4, 1);
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(14, 1),
            xDrift.target(),
            "elytra correction keeps the original x-lane z instead of drifting to another lane"
        );
        assertEquals(2, xDrift.offAxisDistance(), "x-lane correction reports z drift from the route");
        assertFalse(xDrift.withinCorridor(), "large x-lane drift is outside the route corridor");

        PlaneAutoWalkPlanner.RouteCorrection xAligned = planner.correctedTarget(eastLane, 10, 1, 4, 1);
        assertEquals(
            planner.localTarget(eastLane, 10, 1, 4),
            xAligned.target(),
            "aligned elytra correction preserves normal local-target behavior"
        );
        assertTrue(xAligned.withinCorridor(), "aligned x-lane travel is inside the route corridor");

        PlaneAutoWalkPlanner.AutoWalkState connector = new PlaneAutoWalkPlanner.AutoWalkState(2, 1);
        PlaneAutoWalkPlanner.RouteCorrection zDrift = planner.correctedTarget(connector, 37, 2, 4, 1);
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(39, 6),
            zDrift.target(),
            "connector correction keeps the original connector x instead of cutting toward another lane"
        );
        assertEquals(2, zDrift.offAxisDistance(), "connector correction reports x drift from the route");
        assertFalse(zDrift.withinCorridor(), "large connector drift is outside the route corridor");
    }

    private static void selectsHoleEscapeTargets() {
        BlockPos playerPos = new BlockPos(0, 64, 0);
        TestBlockView serviceHole = TestBlockView.oneDeepHole(playerPos);
        PlaneHoleEscapePlanner serviceHolePlanner = new PlaneHoleEscapePlanner(new PlaneAreaBounds(-8, 8, -8, 8), serviceHole);

        assertEquals(
            new BlockPos(0, 65, -1),
            serviceHolePlanner.escapeTarget(playerPos),
            "service-hole escape stands above the adjacent rim block"
        );

        TestBlockView genericHole = TestBlockView.oneDeepHole(playerPos);
        genericHole.solid.add(new BlockPos(0, 65, -1));

        assertEquals(
            new BlockPos(1, 65, 0),
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-8, 8, -8, 8), genericHole).escapeTarget(playerPos),
            "blocked preferred cardinal target falls through to the next valid cardinal target"
        );

        TestBlockView openSide = TestBlockView.oneDeepHole(playerPos);
        openSide.solid.remove(new BlockPos(0, 64, -1));
        assertEquals(
            null,
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-8, 8, -8, 8), openSide).escapeTarget(playerPos),
            "already open side is not treated as a trapped hole"
        );

        TestBlockView outsideTargets = TestBlockView.oneDeepHole(playerPos);
        assertEquals(
            null,
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(0, 0, 0, 0), outsideTargets).escapeTarget(playerPos),
            "targets outside the configured build area are rejected"
        );

        TestBlockView blockedHeadroom = TestBlockView.oneDeepHole(playerPos);
        blockedHeadroom.solid.add(new BlockPos(0, 66, -1));
        blockedHeadroom.solid.add(new BlockPos(1, 66, 0));
        blockedHeadroom.solid.add(new BlockPos(0, 66, 1));
        blockedHeadroom.solid.add(new BlockPos(-1, 66, 0));
        blockedHeadroom.solid.add(new BlockPos(1, 66, -1));
        blockedHeadroom.solid.add(new BlockPos(1, 66, 1));
        blockedHeadroom.solid.add(new BlockPos(-1, 66, 1));
        blockedHeadroom.solid.add(new BlockPos(-1, 66, -1));
        assertEquals(
            null,
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-8, 8, -8, 8), blockedHeadroom).escapeTarget(playerPos),
            "escape targets without headroom are rejected"
        );

        TestBlockView twoDeepHole = TestBlockView.oneDeepHole(playerPos);
        twoDeepHole.solid.add(new BlockPos(0, 65, -1));
        twoDeepHole.solid.add(new BlockPos(1, 65, 0));
        twoDeepHole.solid.add(new BlockPos(0, 65, 1));
        twoDeepHole.solid.add(new BlockPos(-1, 65, 0));
        twoDeepHole.solid.add(new BlockPos(1, 65, -1));
        twoDeepHole.solid.add(new BlockPos(1, 65, 1));
        twoDeepHole.solid.add(new BlockPos(-1, 65, 1));
        twoDeepHole.solid.add(new BlockPos(-1, 65, -1));
        assertEquals(
            null,
            new PlaneHoleEscapePlanner(new PlaneAreaBounds(-8, 8, -8, 8), twoDeepHole).escapeTarget(playerPos),
            "sealed two-deep hole has no safe no-break escape target"
        );
    }

    private static void selectsTrashEdgeTargets() {
        BlockPos playerPos = new BlockPos(0, 320, 0);
        TestTrashBlockView view = TestTrashBlockView.filledPlane(-4, 4);
        view.buildBlocks.remove(new BlockPos(0, 319, -1));
        PlaneTrashEdgePlanner planner = new PlaneTrashEdgePlanner(new PlaneAreaBounds(-4, 4, -4, 4), 319, 4, view);

        assertEquals(
            new PlaneTrashEdgePlanner.Target(new BlockPos(0, 319, 0), Direction.NORTH),
            planner.select(playerPos),
            "trash edge selects nearest local plane edge"
        );
        Vec3d dropTarget = PlaneTrashEdgePlanner.dropTarget(planner.select(playerPos));
        assertTrue(
            near(dropTarget.x, 0.5) && near(dropTarget.y, 318.75) && near(dropTarget.z, -1.0),
            "trash drop target aims outward and down off the edge"
        );

        TestTrashBlockView nearestBlockedHeadroom = TestTrashBlockView.filledPlane(-4, 4);
        nearestBlockedHeadroom.buildBlocks.remove(new BlockPos(0, 319, -1));
        nearestBlockedHeadroom.replaceable.remove(new BlockPos(0, 320, 0));
        nearestBlockedHeadroom.buildBlocks.remove(new BlockPos(0, 319, 2));
        assertEquals(
            new PlaneTrashEdgePlanner.Target(new BlockPos(0, 319, 1), Direction.SOUTH),
            new PlaneTrashEdgePlanner(new PlaneAreaBounds(-4, 4, -4, 4), 319, 4, nearestBlockedHeadroom).select(playerPos),
            "edge planner rejects standing positions without headroom"
        );

        assertEquals(
            null,
            new PlaneTrashEdgePlanner(new PlaneAreaBounds(-8, 8, -8, 8), 319, 1, TestTrashBlockView.filledPlane(-8, 8)).select(playerPos),
            "interior positions with no local edge are rejected"
        );

        assertEquals(
            null,
            new PlaneTrashEdgePlanner(new PlaneAreaBounds(-4, 4, -4, 4), 319, 4, new TestTrashBlockView()).select(playerPos),
            "planner returns no target when no local edge exists"
        );

        assertFalse(
            planner.validStanding(new BlockPos(0, 318, -1)),
            "wrong Y level is rejected"
        );
    }

    private static void detectsUnsafeEndermanLooks() {
        Vec3d eyes = new Vec3d(0.0, 64.0, 0.0);
        List<Vec3d> enderman = List.of(new Vec3d(0.0, 64.0, 12.0));

        assertTrue(
            PlaneEndermanLookMath.unsafeLook(eyes, 0.0f, 0.0f, 64.0, enderman),
            "looking directly along the crosshair at an enderman is unsafe"
        );
        assertFalse(
            PlaneEndermanLookMath.unsafeLook(eyes, 90.0f, 0.0f, 64.0, enderman),
            "looking away from an enderman is safe"
        );
        assertFalse(
            PlaneEndermanLookMath.unsafeLook(eyes, 0.0f, 75.0f, 64.0, enderman),
            "looking down avoids enderman attention"
        );
        assertFalse(
            PlaneEndermanLookMath.unsafeLook(eyes, 0.0f, 0.0f, 8.0, enderman),
            "endermen outside the configured look radius are ignored"
        );

        Float adjustedPitch = PlaneEndermanLookMath.safeDownwardPitch(eyes, 0.0f, 0.0f, 75, 64.0, enderman);
        assertTrue(adjustedPitch != null && adjustedPitch > 0.0f, "unsafe level look receives a downward pitch adjustment");
        assertFalse(
            PlaneEndermanLookMath.unsafeLook(eyes, 0.0f, adjustedPitch, 64.0, enderman),
            "adjusted downward pitch is safe"
        );
        assertEquals(45.0f, PlaneEndermanLookMath.clampIdlePitch(10), "idle pitch is clamped upward to 45 degrees");
        assertEquals(90.0f, PlaneEndermanLookMath.clampIdlePitch(120), "idle pitch is clamped downward to 90 degrees");
    }

    private static void detectsAutoElytraObstructionsAndLandingTargets() {
        PlaneRuntimeConfig config = new PlaneRuntimeConfig(
            new PlaneBuildConfig(319, 0, 64, 0, 16, 4, 8, 50, 32, 128),
            null
        );
        TestElytraBlockView world = new TestElytraBlockView();
        PlaneAutoElytraScanner scanner = new PlaneAutoElytraScanner(config, world);
        PlaneAutoWalkPlanner.Segment east = new PlaneAutoWalkPlanner.Segment(
            new PlaneAutoWalkPlanner.Waypoint(0, 4),
            new PlaneAutoWalkPlanner.Waypoint(64, 4),
            PlaneAutoWalkPlanner.SegmentAxis.X
        );
        BlockPos playerPos = new BlockPos(10, 320, 4);

        for (int x = 11; x <= 30; x++) {
            world.blocking.add(new BlockPos(x, 319, 4));
            world.solid.add(new BlockPos(x, 319, 4));
        }
        assertFalse(scanner.routeAheadBlocked(east, playerPos, 20), "exactly 20 route-ahead blockers does not trigger auto elytra");

        world.blocking.add(new BlockPos(31, 319, 4));
        world.solid.add(new BlockPos(31, 319, 4));
        assertTrue(scanner.routeAheadBlocked(east, playerPos, 20), "more than 20 route-ahead blockers triggers auto elytra");

        world.blocking.clear();
        world.solid.clear();
        for (int x = 11; x <= 31; x++) {
            world.blocking.add(new BlockPos(x, 319, 4));
            world.hazard.add(new BlockPos(x, 319, 4));
        }
        assertTrue(scanner.routeAheadBlocked(east, playerPos, 20), "hazard blocks trigger auto elytra even when not solid");

        PlaneAutoWalkPlanner.Segment north = new PlaneAutoWalkPlanner.Segment(
            new PlaneAutoWalkPlanner.Waypoint(10, 4),
            new PlaneAutoWalkPlanner.Waypoint(10, 0),
            PlaneAutoWalkPlanner.SegmentAxis.Z
        );
        assertFalse(scanner.routeAheadBlocked(north, playerPos, 20), "route direction, not unrelated east blockers, defines in front");

        world.blocking.clear();
        world.hazard.clear();
        world.solid.clear();
        world.hazard.add(new BlockPos(12, 319, 4));
        assertTrue(scanner.hazardAhead(east, playerPos, 20), "single route-ahead hazard triggers auto elytra scan");
        assertFalse(scanner.routeAheadBlocked(east, playerPos, 20), "single route-ahead hazard does not satisfy long blocking-run threshold");

        world.hazard.clear();
        world.solid.add(new BlockPos(12, 319, 4));
        assertFalse(scanner.hazardAhead(east, playerPos, 20), "single solid non-hazard does not trigger hazard scan");

        world.solid.clear();
        world.hazard.add(new BlockPos(12, 319, 4));
        assertFalse(scanner.hazardAhead(north, playerPos, 20), "hazards outside the current route direction are ignored");

        assertTrue(
            PlaneAutoElytraHazards.hazardKind(false, false, false, false, false, false, false, false, false, false, false, true, false),
            "end portal frames are auto elytra hazards"
        );

        BlockPos landing = new BlockPos(10, 320, 4);
        world.blocking.clear();
        world.hazard.clear();
        world.solid.clear();
        world.solid.add(landing.down());
        assertTrue(scanner.safeLandingTarget(landing), "solid non-hazard support with headroom is safe for landing");

        world.hazard.add(landing.down());
        assertFalse(scanner.safeLandingTarget(landing), "hazard support is rejected for landing");
        world.hazard.clear();

        world.solid.clear();
        assertFalse(scanner.safeLandingTarget(landing), "missing landing support is rejected");

        world.solid.add(landing.down());
        world.solid.add(landing.up());
        assertFalse(scanner.safeLandingTarget(landing), "blocked headroom is rejected");

        assertFalse(scanner.safeLandingTarget(new BlockPos(65, 320, 4)), "landing outside the build area is rejected");

        PlaneAutoWalkPlanner planner = new PlaneAutoWalkPlanner(new PlaneAreaBounds(0, 16, 0, 8), 1, 8);
        PlaneAutoWalkPlanner.AutoWalkState firstLane = new PlaneAutoWalkPlanner.AutoWalkState(1, 1);
        world.solid.clear();
        world.hazard.clear();
        world.solid.add(new BlockPos(8, 319, 1));
        world.solid.add(new BlockPos(10, 319, 1));
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(8, 1),
            scanner.safeForwardContinuationTarget(planner, firstLane, new BlockPos(5, 320, 1)),
            "forward continuation selects the nearest supported target on the current segment"
        );

        world.solid.add(new BlockPos(4, 319, 1));
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(8, 1),
            scanner.safeForwardContinuationTarget(planner, firstLane, new BlockPos(5, 320, 1)),
            "forward continuation ignores safe supports behind the current route progress"
        );

        world.solid.clear();
        world.solid.add(new BlockPos(15, 319, 5));
        assertEquals(
            new PlaneAutoWalkPlanner.Waypoint(15, 5),
            scanner.safeForwardContinuationTarget(planner, firstLane, new BlockPos(10, 320, 1)),
            "forward continuation searches future snake segments when the current segment has no safe support"
        );

        world.hazard.add(new BlockPos(15, 319, 5));
        assertEquals(
            null,
            scanner.safeForwardContinuationTarget(planner, firstLane, new BlockPos(10, 320, 1)),
            "forward continuation rejects hazardous future supports"
        );
    }

    private static void selectsKillAuraMobGroups() {
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.MONSTER), "monster mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.CREATURE), "creature mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.AMBIENT), "ambient mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.WATER_CREATURE), "water creature mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.WATER_AMBIENT), "water ambient mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.UNDERGROUND_WATER_CREATURE), "underground water mobs are selected");
        assertTrue(KillAuraCompanionSettings.isMobGroup(SpawnGroup.AXOLOTLS), "axolotl mobs are selected");
        assertFalse(KillAuraCompanionSettings.isMobGroup(SpawnGroup.MISC), "misc entities are not selected");
    }

    private static void selectsBowDefenseMobTargets() {
        UUID botUuid = new UUID(0L, 42L);
        UUID otherUuid = new UUID(0L, 43L);

        assertEquals(400, PlaneBowTargeting.threatPriority(true, false, false, true), "witches are highest priority");
        assertEquals(300, PlaneBowTargeting.threatPriority(false, true, false, true), "skeletons outrank creepers");
        assertEquals(200, PlaneBowTargeting.threatPriority(false, false, true, true), "creepers outrank generic hostiles");
        assertEquals(100, PlaneBowTargeting.threatPriority(false, false, false, true), "generic hostiles remain valid threats");
        assertEquals(0, PlaneBowTargeting.threatPriority(false, false, false, false), "non-hostiles have no threat priority");
        assertTrue(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.MONSTER), "bow defense selects hostile monster mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.CREATURE), "bow defense ignores passive creature mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.AMBIENT), "bow defense ignores ambient mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.WATER_CREATURE), "bow defense ignores water creature mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.WATER_AMBIENT), "bow defense ignores water ambient mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.UNDERGROUND_WATER_CREATURE), "bow defense ignores underground water mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.AXOLOTLS), "bow defense ignores axolotl mobs");
        assertFalse(KillAuraCompanionSettings.isHostileMobGroup(SpawnGroup.MISC), "bow defense ignores misc entities");
        assertFalse(KillAuraCompanionSettings.isAllowedMobEntity(true, true, false), "bow defense still excludes players");
        assertFalse(KillAuraCompanionSettings.isAllowedMobEntity(true, false, true), "bow defense still excludes endermen");
        assertFalse(KillAuraCompanionSettings.isAllowedMobEntity(false, false, false), "bow defense still excludes unattackable entities");
        assertTrue(KillAuraCompanionSettings.isAllowedMobEntity(true, false, false), "bow defense allows attackable non-player non-enderman mobs");
        assertTrue(KillAuraCompanionSettings.isAggroedOnBot(true, null, botUuid), "bow defense accepts mobs currently targeting the bot");
        assertTrue(KillAuraCompanionSettings.isAggroedOnBot(false, botUuid, botUuid), "bow defense accepts angerable mobs angry at the bot");
        assertFalse(KillAuraCompanionSettings.isAggroedOnBot(false, otherUuid, botUuid), "bow defense rejects mobs angry at someone else");
        assertFalse(KillAuraCompanionSettings.isAggroedOnBot(false, null, botUuid), "bow defense rejects mobs with no bot aggro");
        assertTrue(PlaneBowTargeting.meleePrepPolicy(4.5, true), "melee prep runs for close aggroed threats");
        assertFalse(PlaneBowTargeting.meleePrepPolicy(4.6, true), "melee prep ignores threats outside KillAura range");
        assertTrue(PlaneBowTargeting.meleePrepPolicy(4.0, false), "melee prep handles close threats even before aggro is confirmed");
        assertFalse(PlaneBowTargeting.bowTargetPolicy(4.5, 20.0, true, true), "bow defense leaves melee-range threats to KillAura");
        assertTrue(PlaneBowTargeting.bowTargetPolicy(4.6, 20.0, true, true), "bow defense accepts aggroed threats outside melee range");
        assertTrue(PlaneBowTargeting.bowTargetPolicy(5.9, 20.0, true, false), "bow defense accepts visible threats outside melee range before aggro is confirmed");
        assertTrue(PlaneBowTargeting.bowTargetPolicy(6.0, 20.0, true, false), "bow defense accepts visible missing-aggro targets at spacing threshold");
        assertFalse(PlaneBowTargeting.bowTargetPolicy(6.0, 20.0, false, true), "bow defense still requires visibility");
        assertFalse(PlaneBowTargeting.bowTargetPolicy(21.0, 20.0, true, true), "bow defense respects configured range");
    }

    private static boolean[] hotbarEmpties(int... emptySlots) {
        boolean[] slots = new boolean[9];
        for (int slot : emptySlots) {
            slots[slot] = true;
        }

        return slots;
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000_001;
    }

    private static final class TestTrashBlockView implements PlaneTrashEdgePlanner.BlockView {
        private final Set<BlockPos> buildBlocks = new HashSet<>();
        private final Set<BlockPos> replaceable = new HashSet<>();

        static TestTrashBlockView filledPlane(int min, int max) {
            TestTrashBlockView view = new TestTrashBlockView();
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    view.buildBlocks.add(new BlockPos(x, 319, z));
                    view.replaceable.add(new BlockPos(x, 320, z));
                    view.replaceable.add(new BlockPos(x, 321, z));
                }
            }

            return view;
        }

        @Override
        public boolean buildBlock(BlockPos pos) {
            return buildBlocks.contains(pos);
        }

        @Override
        public boolean replaceable(BlockPos pos) {
            return replaceable.contains(pos);
        }
    }

    private static final class TestBlockView implements PlaneHoleEscapePlanner.BlockView {
        private final Set<BlockPos> solid = new HashSet<>();

        static TestBlockView oneDeepHole(BlockPos playerPos) {
            TestBlockView view = new TestBlockView();
            view.solid.add(playerPos.down());

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    view.solid.add(playerPos.add(dx, 0, dz));
                }
            }

            return view;
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

    private static final class TestElytraBlockView implements PlaneAutoElytraScanner.BlockView {
        private final Set<BlockPos> blocking = new HashSet<>();
        private final Set<BlockPos> solid = new HashSet<>();
        private final Set<BlockPos> hazard = new HashSet<>();

        @Override
        public boolean blocking(BlockPos pos) {
            return blocking.contains(pos) || solid.contains(pos) || hazard.contains(pos);
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
}
