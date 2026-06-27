package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

final class PlaneBowAimController {
    private static final double ARROW_GRAVITY = 0.05;
    private static final double MAX_LEAD_TICKS = 8.0;
    private static final double MIN_PROJECTILE_SPEED = 0.3;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final int rotationPriority;

    PlaneBowAimController(PlaneRuntimeConfig config) {
        this.rotationPriority = config.rotationPriority();
    }

    boolean aimAt(Entity target, int chargeTicks) {
        return aimAt(target, chargeTicks, null);
    }

    boolean aimAt(Entity target, int chargeTicks, Runnable callback) {
        Optional<Aim> aim = aim(target, chargeTicks);
        if (aim.isEmpty()) return false;

        return rotate(aim.get(), callback);
    }

    Optional<Aim> aim(Entity target, int chargeTicks) {
        if (mc.player == null || target == null) return Optional.empty();

        Vec3d shooter = mc.player.getEyePos();
        return solve(
            shooter,
            aimPoint(target.getBoundingBox()),
            target.getVelocity(),
            chargeTicks
        );
    }

    static Vec3d aimPoint(Box box) {
        return new Vec3d(
            (box.minX + box.maxX) * 0.5,
            box.minY + (box.maxY - box.minY) * 0.55,
            (box.minZ + box.maxZ) * 0.5
        );
    }

    boolean rotate(Aim aim, Runnable callback) {
        if (aim == null) return false;

        Rotations.rotate(aim.yaw(), aim.pitch(), rotationPriority, callback);
        return true;
    }

    static Optional<Aim> solve(Vec3d shooter, Vec3d targetCenter, Vec3d targetVelocity, int chargeTicks) {
        double speed = projectileSpeed(chargeTicks);
        if (speed < MIN_PROJECTILE_SPEED) return Optional.empty();

        Vec3d initialDelta = targetCenter.subtract(shooter);
        double initialHorizontalDistance = horizontalDistance(initialDelta);
        double leadTicks = Math.min(MAX_LEAD_TICKS, initialHorizontalDistance / speed);
        Vec3d aimedTarget = targetCenter.add(targetVelocity.multiply(leadTicks));
        Vec3d delta = aimedTarget.subtract(shooter);

        double horizontalDistance = horizontalDistance(delta);
        if (horizontalDistance < 1.0E-4) return Optional.empty();

        double speedSq = speed * speed;
        double discriminant = speedSq * speedSq
            - ARROW_GRAVITY * (ARROW_GRAVITY * horizontalDistance * horizontalDistance + 2.0 * delta.y * speedSq);
        if (discriminant < 0.0) return Optional.empty();

        double pitchRadians = Math.atan((speedSq - Math.sqrt(discriminant)) / (ARROW_GRAVITY * horizontalDistance));
        double yaw = Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0;
        double pitch = -Math.toDegrees(pitchRadians);
        return Optional.of(new Aim(yaw, pitch, aimedTarget));
    }

    static double projectileSpeed(int chargeTicks) {
        double pull = Math.min(Math.max(chargeTicks, 0), 20) / 20.0;
        double progress = (pull * pull + pull * 2.0) / 3.0;
        return Math.min(progress, 1.0) * 3.0;
    }

    private static double horizontalDistance(Vec3d delta) {
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    record Aim(double yaw, double pitch, Vec3d aimedTarget) {
    }
}
