package com.watchmenbot.modules.planebuilder;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

final class PlaneEndermanLookMath {
    private static final double UPPER_BODY_HIT_RADIUS = 0.42;
    private static final double MIN_RANGE = 1.0;

    private PlaneEndermanLookMath() {
    }

    static boolean unsafeLook(Vec3d eyes, float yaw, float pitch, double range, List<Vec3d> endermanLookPoints) {
        if (eyes == null || endermanLookPoints == null || endermanLookPoints.isEmpty()) return false;

        double effectiveRange = Math.max(MIN_RANGE, range);
        Vec3d look = lookVector(yaw, pitch);
        for (Vec3d point : endermanLookPoints) {
            if (point == null) continue;
            Vec3d toPoint = point.subtract(eyes);
            double distance = toPoint.length();
            if (distance > effectiveRange) continue;

            double alongLook = toPoint.dotProduct(look);
            if (alongLook <= 0.0 || alongLook > effectiveRange) continue;

            Vec3d closest = eyes.add(look.multiply(alongLook));
            if (closest.squaredDistanceTo(point) <= UPPER_BODY_HIT_RADIUS * UPPER_BODY_HIT_RADIUS) return true;
        }

        return false;
    }

    static Float safeDownwardPitch(Vec3d eyes, float yaw, float desiredPitch, int idlePitch, double range, List<Vec3d> endermanLookPoints) {
        float clampedDesired = clampPitch(desiredPitch);
        if (!unsafeLook(eyes, yaw, clampedDesired, range, endermanLookPoints)) return clampedDesired;

        float clampedIdle = clampIdlePitch(idlePitch);
        for (float pitch = Math.max(clampedDesired, 45.0f); pitch <= 90.0f; pitch += 5.0f) {
            if (!unsafeLook(eyes, yaw, pitch, range, endermanLookPoints)) return pitch;
        }

        if (!unsafeLook(eyes, yaw, clampedIdle, range, endermanLookPoints)) return clampedIdle;
        if (!unsafeLook(eyes, yaw, 90.0f, range, endermanLookPoints)) return 90.0f;
        return null;
    }

    static float clampIdlePitch(int pitch) {
        return Math.min(90.0f, Math.max(45.0f, pitch));
    }

    static float clampPitch(float pitch) {
        return Math.min(90.0f, Math.max(-90.0f, pitch));
    }

    private static Vec3d lookVector(float yaw, float pitch) {
        float yawRadians = yaw * MathHelper.RADIANS_PER_DEGREE;
        float pitchRadians = pitch * MathHelper.RADIANS_PER_DEGREE;
        float cosPitch = MathHelper.cos(pitchRadians);

        return new Vec3d(
            -MathHelper.sin(yawRadians) * cosPitch,
            -MathHelper.sin(pitchRadians),
            MathHelper.cos(yawRadians) * cosPitch
        ).normalize();
    }
}
