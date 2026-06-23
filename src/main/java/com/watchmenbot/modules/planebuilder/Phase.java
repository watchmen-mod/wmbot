package com.watchmenbot.modules.planebuilder;

enum Phase {
    IDLE("idle", false),
    OUTSIDE_AREA("outside area", false),
    HOLE_ESCAPE("escaping hole", false),
    MISSING_OBSIDIAN("missing obsidian", true),
    PLACING_OBSIDIAN("placing obsidian", false),
    AUTO_WALKING("auto walking", false),
    AUTO_ELYTRA_FLYING("auto elytra flying", false),
    AUTO_ELYTRA_LANDING("auto elytra landing", false),
    SELECTING_SERVICE_HOLE("selecting service hole", true),
    PLACING_SUPPORT("placing support", true),
    OPENING_SERVICE_HOLE("opening service hole", true),
    SERVICE_HOLE_OPEN("service hole open", true),
    SELECTING_REPLENISH_SOURCE("selecting replenish source", true),
    PLACING_ENDER_CHEST("placing ender chest", true),
    BREAKING_ENDER_CHEST("breaking ender chest", true),
    CLOSING_SERVICE_HOLE("closing service hole", true),
    PLACING_ENDER_CHEST_SHULKER("placing ender chest shulker", true),
    ENDER_CHEST_SHULKER_PLACED("ender chest shulker placed", true),
    OPENING_ENDER_CHEST_SHULKER("opening ender chest shulker", true),
    TAKING_ENDER_CHESTS_FROM_SHULKER("taking ender chests from shulker", true),
    BREAKING_ENDER_CHEST_SHULKER("breaking ender chest shulker", true),
    SERVICE_HOLE_BLOCKED("service hole blocked", true),
    MISSING_PICKAXE("missing pickaxe", true),
    MISSING_ENDER_CHEST("missing ender chest", true),
    MISSING_ENDER_CHEST_SHULKER("missing ender chest shulker", true),
    CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL("closing service hole for kitbot refill", true),
    WAITING_FOR_KITBOT_REFILL("waiting for kitbot refill", true),
    PICKING_UP_KITBOT_REFILL("picking up kitbot refill", true),
    PICKING_UP_MISSING_ENDER_CHEST_SHULKER("picking up missing ender chest shulker", true),
    PICKING_UP_REPLENISH_DROPS("picking up replenish drops", true),
    MOVING_TO_TRASH_EDGE("moving to trash edge", true),
    DROPPING_TRASH_OFF_EDGE("dropping trash off edge", true),
    WAITING_FOR_TRASH_TO_FALL("waiting for trash to fall", true);

    private final String label;
    private final boolean replenishActive;

    Phase(String label, boolean replenishActive) {
        this.label = label;
        this.replenishActive = replenishActive;
    }

    String label() {
        return label;
    }

    boolean replenishActive() {
        return replenishActive;
    }
}
