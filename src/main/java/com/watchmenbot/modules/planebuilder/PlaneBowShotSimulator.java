package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.ProjectileEntitySimulator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

final class PlaneBowShotSimulator {
    private static final int MAX_SIMULATION_TICKS = 120;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();

    boolean simulatedFirstHitIsTarget(Entity target) {
        return simulatedFirstHit(target) == ShotPrediction.DIRECT_TARGET;
    }

    ShotPrediction simulatedFirstHit(Entity target) {
        if (mc.player == null || target == null) return ShotPrediction.UNAVAILABLE;
        if (!simulator.set(mc.player, mc.player.getMainHandStack(), 0, true, 1.0f)) return ShotPrediction.UNAVAILABLE;

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            HitResult result = simulator.tick();
            if (result == null) continue;
            if (result instanceof EntityHitResult entityHit) {
                return entityHit.getEntity().getId() == target.getId()
                    ? ShotPrediction.DIRECT_TARGET
                    : ShotPrediction.OTHER_ENTITY;
            }

            return ShotPrediction.BLOCKED;
        }

        return ShotPrediction.NO_HIT;
    }

    enum ShotPrediction {
        DIRECT_TARGET("direct hit"),
        OTHER_ENTITY("another entity is first in the shot path"),
        BLOCKED("a block is first in the shot path"),
        NO_HIT("shot path does not reach the target"),
        UNAVAILABLE("shot prediction unavailable");

        private final String logReason;

        ShotPrediction(String logReason) {
            this.logReason = logReason;
        }

        String logReason() {
            return logReason;
        }
    }
}
