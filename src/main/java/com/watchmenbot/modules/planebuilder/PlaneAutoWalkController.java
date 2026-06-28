package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.BlockPos;

final class PlaneAutoWalkController {
    private static final int POST_LANDING_AUTO_ELYTRA_COOLDOWN_TICKS = 40;
    private static final int ROUTE_RECOVERY_CORRIDOR_MULTIPLIER = 3;

    private final AutoWalkConfig config;
    private final PlaneRuntimeConfig runtimeConfig;
    private final PlaneAutoWalkPlanner planner;
    private final Navigator navigator;
    private final PlaneAutoElytraScanner autoElytraScanner;
    private PlaneAutoWalkPlanner.AutoWalkState state;
    private LockoutReason autoElytraLockout = LockoutReason.NONE;
    private int autoElytraGroundedCooldownTicks;

    PlaneAutoWalkController(PlaneBuilderSettings.AutoWalk settings) {
        this(settings, PlaneRuntimeConfig.DEFAULT);
    }

    PlaneAutoWalkController(PlaneBuilderSettings.AutoWalk settings, PlaneRuntimeConfig runtimeConfig) {
        this(settings, runtimeConfig, new PlaneEndermanLookSafety());
    }

    PlaneAutoWalkController(
        PlaneBuilderSettings.AutoWalk settings,
        PlaneRuntimeConfig runtimeConfig,
        PlaneEndermanLookSafety endermanLookSafety
    ) {
        this(
            new AutoWalkConfig() {
                @Override
                public boolean enabled() {
                    return settings.enabled().get();
                }

                @Override
                public boolean autoElytraFlyEnabled() {
                    return settings.autoElytraFly().get();
                }

                @Override
                public void disableAutoElytraFly() {
                    settings.autoElytraFly().set(false);
                }

            },
            runtimeConfig,
            new PlaneAutoWalkPlanner(runtimeConfig),
            new PlaneAutoWalkNavigator(endermanLookSafety),
            null
        );
    }

    PlaneAutoWalkController(
        PlaneBuilderSettings.AutoWalk settings,
        PlaneRuntimeConfig runtimeConfig,
        PlaneEndermanLookSafety endermanLookSafety,
        PlaneAutoElytraScanner autoElytraScanner
    ) {
        this(
            new AutoWalkConfig() {
                @Override
                public boolean enabled() {
                    return settings.enabled().get();
                }

                @Override
                public boolean autoElytraFlyEnabled() {
                    return settings.autoElytraFly().get();
                }

                @Override
                public void disableAutoElytraFly() {
                    settings.autoElytraFly().set(false);
                }

            },
            runtimeConfig,
            new PlaneAutoWalkPlanner(runtimeConfig),
            new PlaneAutoWalkNavigator(endermanLookSafety),
            autoElytraScanner
        );
    }

    PlaneAutoWalkController(AutoWalkConfig config, PlaneAutoWalkPlanner planner, Navigator navigator) {
        this(config, PlaneRuntimeConfig.DEFAULT, planner, navigator, null);
    }

    PlaneAutoWalkController(AutoWalkConfig config, PlaneRuntimeConfig runtimeConfig, PlaneAutoWalkPlanner planner, Navigator navigator) {
        this(config, runtimeConfig, planner, navigator, null);
    }

    PlaneAutoWalkController(
        AutoWalkConfig config,
        PlaneRuntimeConfig runtimeConfig,
        PlaneAutoWalkPlanner planner,
        Navigator navigator,
        PlaneAutoElytraScanner autoElytraScanner
    ) {
        this.config = config;
        this.runtimeConfig = runtimeConfig;
        this.planner = planner;
        this.navigator = navigator;
        this.autoElytraScanner = autoElytraScanner;
    }

    Phase tick(BlockPos playerPos) {
        if (!config.enabled()) {
            reset();
            return Phase.IDLE;
        }

        int radius = PlaneBuilderSettings.AUTO_WALK_WAYPOINT_RADIUS;
        ensureValidState(playerPos, radius);

        PlaneAutoWalkPlanner.Waypoint waypoint = planner.waypoint(state);
        if (waypoint == null) {
            reset();
            return Phase.IDLE;
        }

        while (planner.endpointReached(state, playerPos.getX(), playerPos.getZ(), radius)) {
            PlaneAutoWalkPlanner.AutoWalkState nextState = planner.advance(state);
            if (nextState.equals(state)) {
                suspend();
                return Phase.IDLE;
            }

            state = nextState;
            waypoint = planner.waypoint(state);
            if (waypoint == null) {
                reset();
                return Phase.IDLE;
            }
        }

        PlaneAutoWalkPlanner.Waypoint localTarget = planner.localTarget(
            state,
            playerPos.getX(),
            playerPos.getZ(),
            runtimeConfig.scanRadius()
        );

        PlaneAutoWalkPlanner.Segment segment = planner.segment(state);
        PlaneAutoWalkPlanner.RouteCorrection flightCorrection = planner.correctedTarget(
            state,
            playerPos.getX(),
            playerPos.getZ(),
            runtimeConfig.scanRadius(),
            runtimeConfig.scanRadius()
        );
        if (shouldFly(segment, playerPos)) {
            flyOrDisable(flightCorrection.target());
            return Phase.AUTO_ELYTRA_FLYING;
        }

        if (navigator.flying()) {
            BlockPos landingTarget = autoElytraScanner == null ? null : autoElytraScanner.safeLandingTarget(playerPos, runtimeConfig.scanRadius());
            if (landingTarget != null && flightCorrection.withinCorridor() && navigator.readyToLand()) return landAt(landingTarget);

            PlaneAutoWalkPlanner.Waypoint flightTarget = landingTarget == null && autoElytraScanner != null
                ? autoElytraScanner.safeForwardContinuationTarget(planner, state, playerPos)
                : null;
            flyOrDisable(flightTarget == null ? flightCorrection.target() : flightTarget);
            return Phase.AUTO_ELYTRA_FLYING;
        }

        tickGroundedAutoElytraCooldown();
        navigator.walkTo(localTarget);
        return Phase.AUTO_WALKING;
    }

    Phase landBeforeWorldAction(BlockPos playerPos, LockoutReason reason) {
        if (!navigator.flying()) return Phase.IDLE;

        lockAutoElytra(reason);
        BlockPos landingTarget = autoElytraScanner == null ? null : autoElytraScanner.safeLandingTarget(playerPos, runtimeConfig.scanRadius());
        if (landingTarget == null || !navigator.readyToLand()) {
            navigator.hover(runtimeConfig.buildY() + 1.25, runtimeConfig.buildY() + 2.25);
            return Phase.AUTO_ELYTRA_FLYING;
        }

        return landAt(landingTarget);
    }

    void pause() {
        suspend();
    }

    void suspend() {
        navigator.stop();
    }

    void reset() {
        state = null;
        autoElytraLockout = LockoutReason.NONE;
        autoElytraGroundedCooldownTicks = 0;
        suspend();
    }

    void lockAutoElytra(LockoutReason reason) {
        if (reason != LockoutReason.NONE) autoElytraLockout = reason;
    }

    void releaseAutoElytraLockout() {
        autoElytraLockout = LockoutReason.NONE;
    }

    boolean autoElytraLockedOut() {
        return autoElytraLockout != LockoutReason.NONE;
    }

    boolean flying() {
        return navigator.flying();
    }

    PlaneAutoWalkPlanner.Segment activeSegment() {
        return state == null ? null : planner.segment(state);
    }

    void nudgeTowardPlacementTarget(BlockPos target) {
        if (target == null || navigator.flying()) return;

        navigator.nudgeToward(target);
    }

    private void ensureValidState(BlockPos playerPos, int radius) {
        int corridor = Math.max(runtimeConfig.scanRadius() * ROUTE_RECOVERY_CORRIDOR_MULTIPLIER, radius * 2);
        if (state == null || !planner.compatibleWithSegment(state, playerPos.getX(), playerPos.getZ(), corridor)) {
            state = planner.initialState(playerPos.getX(), playerPos.getZ());
        }
    }

    private boolean shouldFly(PlaneAutoWalkPlanner.Segment segment, BlockPos playerPos) {
        if (!config.autoElytraFlyEnabled() || autoElytraLockedOut() || autoElytraScanner == null) return false;

        int lookahead = PlaneBuilderSettings.AUTO_ELYTRA_SOLID_LOOKAHEAD;
        if (autoElytraScanner.hazardAhead(segment, playerPos, lookahead)) return true;

        return autoElytraGroundedCooldownTicks <= 0
            && autoElytraScanner.routeAheadBlocked(segment, playerPos, lookahead);
    }

    private Phase landAt(BlockPos landingTarget) {
        if (!navigator.landAt(landingTarget)) return Phase.AUTO_ELYTRA_LANDING;

        autoElytraGroundedCooldownTicks = POST_LANDING_AUTO_ELYTRA_COOLDOWN_TICKS;
        return Phase.IDLE;
    }

    private void flyOrDisable(PlaneAutoWalkPlanner.Waypoint target) {
        if (navigator.flyTo(target, runtimeConfig.buildY() + 1.25, runtimeConfig.buildY() + 2.25)) return;

        config.disableAutoElytraFly();
    }

    private void tickGroundedAutoElytraCooldown() {
        if (autoElytraGroundedCooldownTicks > 0) autoElytraGroundedCooldownTicks--;
    }

    interface AutoWalkConfig {
        boolean enabled();

        default boolean autoElytraFlyEnabled() {
            return false;
        }

        default void disableAutoElytraFly() {
        }

    }

    interface Navigator {
        void walkTo(PlaneAutoWalkPlanner.Waypoint nextTarget);

        default boolean flyTo(PlaneAutoWalkPlanner.Waypoint nextTarget, double minY, double maxY) {
            walkTo(nextTarget);
            return false;
        }

        default boolean landAt(BlockPos target) {
            stop();
            return true;
        }

        default void hover(double minY, double maxY) {
            stop();
        }

        default boolean flying() {
            return false;
        }

        default boolean readyToLand() {
            return true;
        }

        default void nudgeToward(BlockPos target) {
            stop();
        }

        void stop();
    }

    enum LockoutReason {
        NONE,
        SAFETY,
        BOW_DEFENSE,
        GUARD_PAUSED,
        REPLENISH,
        HOLE_ESCAPE,
        PLACEMENT
    }
}
