package com.watchmenbot.modules.planebuilder;

final class PlanePickupSettings {
    static final double KITBOT_REFILL_SCAN_RADIUS = 24.0;
    static final double REPLENISH_CLEANUP_SCAN_RADIUS = 12.0;
    static final double SHULKER_RECOVERY_SCAN_RADIUS = 24.0;
    static final int REPLENISH_CLEANUP_GRACE_TICKS = 60;
    static final int REPLENISH_CLEANUP_MAX_TARGET_TICKS = 200;
    static final int MANAGED_SHULKER_RECOVERY_MAX_TARGET_TICKS = 1200;
    static final int MANAGED_SHULKER_RECOVERY_LOG_INTERVAL_TICKS = 20;
    static final int REPATH_COOLDOWN_TICKS = 5;

    private PlanePickupSettings() {
    }
}
