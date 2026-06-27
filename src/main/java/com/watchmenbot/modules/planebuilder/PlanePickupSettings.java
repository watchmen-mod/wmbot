package com.watchmenbot.modules.planebuilder;

final class PlanePickupSettings {
    static final double KITBOT_REFILL_SCAN_RADIUS = 24.0;
    static final double REPLENISH_CLEANUP_SCAN_RADIUS = KITBOT_REFILL_SCAN_RADIUS;
    static final double SHULKER_RECOVERY_SCAN_RADIUS = 24.0;
    static final int REPLENISH_CLEANUP_GRACE_TICKS = 60;
    static final int REPLENISH_CLEANUP_MAX_TARGET_TICKS = 200;
    static final int MANAGED_SHULKER_RECOVERY_MAX_TARGET_TICKS = 1200;
    static final int MANAGED_SHULKER_RECOVERY_LOG_INTERVAL_TICKS = 20;
    static final int REPATH_COOLDOWN_TICKS = 5;
    static final int PICKUP_IDLE_REPATHS_BEFORE_NUDGE = 2;
    static final int PICKUP_NUDGE_TICKS = 4;
    static final int STUCK_JUMP_TICKS = 20;
    static final int STUCK_REPATH_COOLDOWN_TICKS = 20;

    private PlanePickupSettings() {
    }
}
