package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlaneAutoWalkNavigator implements PlaneAutoWalkController.Navigator {
    private static final int MIN_FLIGHT_TICKS_BEFORE_LANDING = 20;
    private static final double LANDING_HORIZONTAL_DISTANCE_SQUARED = 1.2 * 1.2;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneEndermanLookSafety endermanLookSafety;
    private final PlaneElytraFlySession elytraFly = new PlaneElytraFlySession();

    private PlaneAutoWalkPlanner.Waypoint target;
    private boolean active;
    private FlightMode flightMode = FlightMode.GROUNDED;
    private int flightTicks;

    PlaneAutoWalkNavigator() {
        this(new PlaneEndermanLookSafety());
    }

    PlaneAutoWalkNavigator(PlaneEndermanLookSafety endermanLookSafety) {
        this.endermanLookSafety = endermanLookSafety;
    }

    @Override
    public void walkTo(PlaneAutoWalkPlanner.Waypoint nextTarget) {
        if (nextTarget == null || !worldReady()) {
            stop();
            return;
        }

        elytraFly.stop();
        flightMode = FlightMode.GROUNDED;
        flightTicks = 0;
        active = true;
        target = nextTarget;
        if (face(new Vec3d(target.x() + 0.5, mc.player.getEyeY(), target.z() + 0.5))) {
            setWalkingKeys(true);
        }
        else {
            setWalkingKeys(false);
        }
    }

    @Override
    public boolean flyTo(PlaneAutoWalkPlanner.Waypoint nextTarget, double minY, double maxY) {
        if (nextTarget == null || !worldReady()) {
            stop();
            return false;
        }
        if (!elytraFly.start()) {
            walkTo(nextTarget);
            return false;
        }

        updateFlightState(FlightMode.CRUISING);
        active = true;
        target = nextTarget;
        face(new Vec3d(target.x() + 0.5, MathHelper.clamp(mc.player.getEyeY(), minY, maxY), target.z() + 0.5));
        setWalkingKeys(true);
        setFlightKeys(minY, maxY);
        return true;
    }

    @Override
    public boolean landAt(BlockPos landingTarget) {
        if (landingTarget == null || !worldReady()) {
            stop();
            return false;
        }
        if (!elytraFly.start()) return true;

        updateFlightState(FlightMode.LANDING);
        active = true;
        face(new Vec3d(landingTarget.getX() + 0.5, landingTarget.getY(), landingTarget.getZ() + 0.5));
        setWalkingKeys(horizontalDistanceSquaredTo(landingTarget) > LANDING_HORIZONTAL_DISTANCE_SQUARED);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(true);

        boolean landed = mc.player.isOnGround()
            && mc.player.getY() <= landingTarget.getY() + 0.35
            && horizontalDistanceSquaredTo(landingTarget) <= LANDING_HORIZONTAL_DISTANCE_SQUARED;
        if (landed) stop();
        return landed;
    }

    @Override
    public void hover(double minY, double maxY) {
        if (!worldReady()) {
            stop();
            return;
        }
        if (!elytraFly.start()) {
            stop();
            return;
        }

        updateFlightState(FlightMode.CRUISING);
        active = true;
        setWalkingKeys(false);
        setFlightKeys(minY, maxY);
    }

    @Override
    public boolean flying() {
        return elytraFly.active();
    }

    @Override
    public boolean readyToLand() {
        return elytraFly.active() && flightTicks >= MIN_FLIGHT_TICKS_BEFORE_LANDING;
    }

    @Override
    public void nudgeToward(BlockPos nudgeTarget) {
        if (nudgeTarget == null || !worldReady()) {
            stop();
            return;
        }

        elytraFly.stop();
        flightMode = FlightMode.GROUNDED;
        flightTicks = 0;
        active = true;
        target = null;
        if (face(new Vec3d(nudgeTarget.getX() + 0.5, mc.player.getEyeY(), nudgeTarget.getZ() + 0.5))) {
            setWalkingKeys(true);
        }
        else {
            setWalkingKeys(false);
        }
    }

    @Override
    public void stop() {
        active = false;
        target = null;
        setWalkingKeys(false);
        setVerticalKeys(false, false);
        elytraFly.stop();
        flightMode = FlightMode.GROUNDED;
        flightTicks = 0;
    }

    private boolean face(Vec3d targetPos) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = targetPos.x - eyes.x;
        double dy = targetPos.y - eyes.y;
        double dz = targetPos.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, horizontal)));
        return endermanLookSafety.applyMovementLook(yaw, pitch);
    }

    private void setWalkingKeys(boolean pressed) {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(pressed);
        mc.options.sprintKey.setPressed(pressed);
    }

    private void setFlightKeys(double minY, double maxY) {
        if (mc.options == null || mc.player == null) return;

        double y = mc.player.getY();
        if (flightMode == FlightMode.TAKING_OFF || !mc.player.isGliding()) {
            setVerticalKeys(true, false);
        }
        else if (y < minY) {
            setVerticalKeys(true, false);
        }
        else if (y > maxY) {
            setVerticalKeys(false, true);
        }
        else {
            setVerticalKeys(false, false);
        }
    }

    private void setVerticalKeys(boolean jump, boolean sneak) {
        if (mc.options == null) return;

        mc.options.jumpKey.setPressed(jump);
        mc.options.sneakKey.setPressed(sneak);
    }

    private void updateFlightState(FlightMode requestedMode) {
        if (flightMode == FlightMode.GROUNDED) flightMode = FlightMode.TAKING_OFF;
        else if (flightMode != FlightMode.LANDING && mc.player != null && mc.player.isGliding()) flightMode = requestedMode;
        else if (requestedMode == FlightMode.LANDING) flightMode = FlightMode.LANDING;

        flightTicks++;
    }

    private boolean worldReady() {
        return mc.player != null && mc.world != null;
    }

    private double horizontalDistanceSquaredTo(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;

        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private enum FlightMode {
        GROUNDED,
        TAKING_OFF,
        CRUISING,
        LANDING
    }
}
