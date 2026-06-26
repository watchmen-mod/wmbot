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
        if (mc.player == null) return false;
        if (!simulator.set(mc.player, mc.player.getMainHandStack(), 0, true, 1.0f)) return false;

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            HitResult result = simulator.tick();
            if (result == null) continue;
            if (result instanceof EntityHitResult entityHit) {
                return entityHit.getEntity().getId() == target.getId();
            }

            return false;
        }

        return false;
    }
}
