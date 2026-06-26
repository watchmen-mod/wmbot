package com.watchmenbot.modules.planebuilder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class PlaneReplenishTransitionTable {
    private static final Set<Phase> TRANSITION_PHASES = EnumSet.of(
        Phase.SELECTING_SERVICE_HOLE,
        Phase.PLACING_SUPPORT,
        Phase.OPENING_SERVICE_HOLE,
        Phase.SERVICE_HOLE_OPEN,
        Phase.SELECTING_REPLENISH_SOURCE,
        Phase.PLACING_ENDER_CHEST,
        Phase.BREAKING_ENDER_CHEST,
        Phase.CLOSING_SERVICE_HOLE,
        Phase.PLACING_ENDER_CHEST_SHULKER,
        Phase.ENDER_CHEST_SHULKER_PLACED,
        Phase.OPENING_ENDER_CHEST_SHULKER,
        Phase.TAKING_ENDER_CHESTS_FROM_SHULKER,
        Phase.BREAKING_ENDER_CHEST_SHULKER,
        Phase.MISSING_ENDER_CHEST,
        Phase.MISSING_ENDER_CHEST_SHULKER,
        Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL,
        Phase.WAITING_FOR_KITBOT_REFILL,
        Phase.PICKING_UP_KITBOT_REFILL,
        Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER,
        Phase.PICKING_UP_REPLENISH_DROPS,
        Phase.MOVING_TO_TRASH_EDGE,
        Phase.DROPPING_TRASH_OFF_EDGE,
        Phase.WAITING_FOR_TRASH_TO_FALL,
        Phase.SERVICE_HOLE_BLOCKED,
        Phase.MISSING_OBSIDIAN,
        Phase.MISSING_PICKAXE
    );

    private PlaneReplenishTransitionTable() {
    }

    static Map<Phase, PlaneReplenishTransition> create(
        ServiceHoleWorkflow serviceHoles,
        EnderChestFarmWorkflow enderChestFarm,
        PlaneReplenishTransition closeServiceHole,
        PlaneReplenishTransition placeEnderChestShulker,
        PlaneReplenishTransition openEnderChestShulker,
        PlaneReplenishTransition takeEnderChestsFromShulker,
        PlaneReplenishTransition breakEnderChestShulker,
        PlaneReplenishTransition closeServiceHoleForKitbotRefill,
        PlaneReplenishTransition waitForKitbotRefill,
        PlaneReplenishTransition pickUpKitbotRefill,
        PlaneReplenishTransition pickUpMissingEnderChestShulker,
        PlaneReplenishTransition pickUpReplenishDrops,
        PlaneReplenishTransition moveToTrashEdge,
        PlaneReplenishTransition dropTrashOffEdge,
        PlaneReplenishTransition waitForTrashToFall,
        PlaneReplenishTransition recoverMissingEnderChest,
        PlaneReplenishTransition recoverMissingEnderChestShulker,
        PlaneReplenishTransition recoverMissingObsidian,
        PlaneReplenishTransition recoverMissingPickaxe
    ) {
        Map<Phase, PlaneReplenishTransition> transitions = new EnumMap<>(Phase.class);
        transitions.put(Phase.SELECTING_SERVICE_HOLE, serviceHoles::select);
        transitions.put(Phase.PLACING_SUPPORT, serviceHoles::ensureSupport);
        transitions.put(Phase.OPENING_SERVICE_HOLE, serviceHoles::open);
        transitions.put(Phase.SERVICE_HOLE_OPEN, () -> Phase.SELECTING_REPLENISH_SOURCE);
        transitions.put(Phase.SELECTING_REPLENISH_SOURCE, enderChestFarm::selectSource);
        transitions.put(Phase.PLACING_ENDER_CHEST, enderChestFarm::place);
        transitions.put(Phase.BREAKING_ENDER_CHEST, enderChestFarm::breakPlacedEnderChest);
        transitions.put(Phase.CLOSING_SERVICE_HOLE, closeServiceHole);
        transitions.put(Phase.PLACING_ENDER_CHEST_SHULKER, placeEnderChestShulker);
        transitions.put(Phase.ENDER_CHEST_SHULKER_PLACED, () -> Phase.OPENING_ENDER_CHEST_SHULKER);
        transitions.put(Phase.OPENING_ENDER_CHEST_SHULKER, openEnderChestShulker);
        transitions.put(Phase.TAKING_ENDER_CHESTS_FROM_SHULKER, takeEnderChestsFromShulker);
        transitions.put(Phase.BREAKING_ENDER_CHEST_SHULKER, breakEnderChestShulker);
        transitions.put(Phase.MISSING_ENDER_CHEST, recoverMissingEnderChest);
        transitions.put(Phase.MISSING_ENDER_CHEST_SHULKER, recoverMissingEnderChestShulker);
        transitions.put(Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL, closeServiceHoleForKitbotRefill);
        transitions.put(Phase.WAITING_FOR_KITBOT_REFILL, waitForKitbotRefill);
        transitions.put(Phase.PICKING_UP_KITBOT_REFILL, pickUpKitbotRefill);
        transitions.put(Phase.PICKING_UP_MISSING_ENDER_CHEST_SHULKER, pickUpMissingEnderChestShulker);
        transitions.put(Phase.PICKING_UP_REPLENISH_DROPS, pickUpReplenishDrops);
        transitions.put(Phase.MOVING_TO_TRASH_EDGE, moveToTrashEdge);
        transitions.put(Phase.DROPPING_TRASH_OFF_EDGE, dropTrashOffEdge);
        transitions.put(Phase.WAITING_FOR_TRASH_TO_FALL, waitForTrashToFall);
        transitions.put(Phase.SERVICE_HOLE_BLOCKED, serviceHoles::recoverBlocked);
        transitions.put(Phase.MISSING_OBSIDIAN, recoverMissingObsidian);
        transitions.put(Phase.MISSING_PICKAXE, recoverMissingPickaxe);
        return transitions;
    }

    static Set<Phase> transitionPhases() {
        return EnumSet.copyOf(TRANSITION_PHASES);
    }
}
