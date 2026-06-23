package com.watchmenbot.modules.sixb6t;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PortalWalker {
    private final MinecraftClient mc;
    private BlockPos target;

    public PortalWalker(MinecraftClient mc) {
        this.mc = mc;
    }

    public boolean canWalk() {
        return mc.player != null && mc.world != null;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public boolean updateTarget(int radius) {
        if (!canWalk()) return false;
        if (target != null && mc.world.getBlockState(target).isOf(Blocks.NETHER_PORTAL)) return true;

        target = findNearestPortal(radius);
        return target != null;
    }

    public void walkToTarget() {
        if (target == null || !canWalk()) return;

        face(Vec3d.ofCenter(target));
        setWalkingKeys(true);
    }

    public boolean isInsidePortal() {
        if (!canWalk()) return false;

        BlockPos feet = mc.player.getBlockPos();
        BlockPos eyes = BlockPos.ofFloored(mc.player.getEyePos());

        return mc.world.getBlockState(feet).isOf(Blocks.NETHER_PORTAL)
            || mc.world.getBlockState(eyes).isOf(Blocks.NETHER_PORTAL);
    }

    public void stop() {
        target = null;
        setWalkingKeys(false);
    }

    public void pause() {
        setWalkingKeys(false);
    }

    private BlockPos findNearestPortal(int radius) {
        BlockPos origin = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) continue;

                    double distanceSq = origin.getSquaredDistance(pos);
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearest = pos.toImmutable();
                    }
                }
            }
        }

        return nearest;
    }

    private void face(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        mc.player.setYaw((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, horizontal))));
    }

    private void setWalkingKeys(boolean pressed) {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(pressed);
        mc.options.sprintKey.setPressed(pressed);
    }
}
