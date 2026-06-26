package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.BaritoneCompatibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

final class PlaneItemPickupNavigator implements PlaneDroppedItemPickupWorkflow.Navigator<ItemEntity> {
    private final BaritoneItemPickupPathing pathing;
    private final PlaneEndermanLookSafety endermanLookSafety;
    private final PickupNudger nudger;
    private final PickupRecovery recovery = new PickupRecovery(
        PlanePickupSettings.PICKUP_IDLE_REPATHS_BEFORE_NUDGE,
        PlanePickupSettings.PICKUP_NUDGE_TICKS
    );

    private int repathCooldown;
    private boolean active;

    PlaneItemPickupNavigator() {
        this(new PlaneEndermanLookSafety());
    }

    PlaneItemPickupNavigator(PlaneEndermanLookSafety endermanLookSafety) {
        this(
            BaritoneCompatibility.available() ? new BaritonePlaneItemPickupNavigator() : null,
            endermanLookSafety,
            new MinecraftPickupNudger(endermanLookSafety)
        );
    }

    PlaneItemPickupNavigator(BaritoneItemPickupPathing pathing, PlaneEndermanLookSafety endermanLookSafety, PickupNudger nudger) {
        this.pathing = pathing;
        this.endermanLookSafety = endermanLookSafety;
        this.nudger = nudger;
    }

    @Override
    public void pathTo(ItemEntity target) {
        if (pathing == null) return;
        if (target == null) return;

        endermanLookSafety.lookDownIfUnsafe();
        pathing.applySafety();
        active = true;

        UUID nextId = target.getUuid();
        BlockPos nextPos = target.getBlockPos();
        boolean changedTarget = recovery.observeTarget(nextId, nextPos);
        if (changedTarget) {
            repathCooldown = 0;
            nudger.stop();
        }

        if (!changedTarget && isPathing()) {
            recovery.pathingActive();
            nudger.stop();
            return;
        }
        if (!changedTarget && recovery.tickNudge()) {
            nudger.nudgeToward(nextPos);
            return;
        }
        if (!changedTarget && repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        if (!changedTarget && recovery.recordIdleRepathAndShouldNudge()) {
            recovery.tickNudge();
            nudger.nudgeToward(nextPos);
            return;
        }

        repathCooldown = PlanePickupSettings.REPATH_COOLDOWN_TICKS;
        nudger.stop();
        pathing.pathTo(nextPos);
        endermanLookSafety.lookDownIfUnsafe();
    }

    @Override
    public void stop() {
        if (active) {
            pathing.stop();
        }

        active = false;
        repathCooldown = 0;
        recovery.reset();
        nudger.stop();
        if (pathing != null) pathing.restoreSafety();
        endermanLookSafety.lookDownIfUnsafe();
    }

    private boolean isPathing() {
        return pathing != null && pathing.isPathing();
    }

    interface BaritoneItemPickupPathing {
        void applySafety();

        void restoreSafety();

        void pathTo(BlockPos target);

        boolean isPathing();

        void stop();
    }

    interface PickupNudger {
        void nudgeToward(BlockPos target);

        void stop();
    }

    static final class PickupRecovery {
        private final int idleRepathThreshold;
        private final int nudgeTicks;

        private UUID targetId;
        private BlockPos targetPos;
        private int idleRepaths;
        private int nudgeTicksRemaining;

        PickupRecovery(int idleRepathThreshold, int nudgeTicks) {
            this.idleRepathThreshold = Math.max(1, idleRepathThreshold);
            this.nudgeTicks = Math.max(1, nudgeTicks);
        }

        boolean observeTarget(UUID nextId, BlockPos nextPos) {
            boolean changed = nextId == null
                || nextPos == null
                || !nextId.equals(targetId)
                || !nextPos.equals(targetPos);
            if (changed) {
                targetId = nextId;
                targetPos = nextPos == null ? null : nextPos.toImmutable();
                idleRepaths = 0;
                nudgeTicksRemaining = 0;
            }
            return changed;
        }

        void pathingActive() {
            idleRepaths = 0;
            nudgeTicksRemaining = 0;
        }

        boolean recordIdleRepathAndShouldNudge() {
            idleRepaths++;
            if (idleRepaths < idleRepathThreshold) return false;

            idleRepaths = 0;
            nudgeTicksRemaining = nudgeTicks;
            return true;
        }

        boolean tickNudge() {
            if (nudgeTicksRemaining <= 0) return false;

            nudgeTicksRemaining--;
            if (nudgeTicksRemaining == 0) idleRepaths = 0;
            return true;
        }

        void reset() {
            targetId = null;
            targetPos = null;
            idleRepaths = 0;
            nudgeTicksRemaining = 0;
        }

        int idleRepaths() {
            return idleRepaths;
        }

        int nudgeTicksRemaining() {
            return nudgeTicksRemaining;
        }
    }

    private static final class MinecraftPickupNudger implements PickupNudger {
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final PlaneEndermanLookSafety endermanLookSafety;

        private boolean active;

        private MinecraftPickupNudger(PlaneEndermanLookSafety endermanLookSafety) {
            this.endermanLookSafety = endermanLookSafety;
        }

        @Override
        public void nudgeToward(BlockPos target) {
            if (target == null || mc.player == null) return;

            active = true;
            if (face(new Vec3d(target.getX() + 0.5, mc.player.getEyeY(), target.getZ() + 0.5))) {
                setWalkingKeys(true);
            }
            else {
                setWalkingKeys(false);
            }
        }

        @Override
        public void stop() {
            if (!active) return;

            active = false;
            setWalkingKeys(false);
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
    }
}
