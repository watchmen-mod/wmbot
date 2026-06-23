package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class PlaneEndermanLookSafety {
    private static final Config DEFAULT_CONFIG = new Config() {
        @Override
        public boolean endermanLookSafetyEnabled() {
            return true;
        }

        @Override
        public double endermanLookRadius() {
            return PlaneBuilderSettings.ENDERMAN_LOOK_RADIUS;
        }

        @Override
        public int safeIdleLookPitch() {
            return PlaneBuilderSettings.SAFE_IDLE_LOOK_PITCH;
        }
    };

    private final Config config;
    private final PlaneClientContext context;

    PlaneEndermanLookSafety() {
        this(DEFAULT_CONFIG, new PlaneClientContext());
    }

    PlaneEndermanLookSafety(Config config, PlaneClientContext context) {
        this.config = config;
        this.context = context;
    }

    boolean enabled() {
        return config.endermanLookSafetyEnabled();
    }

    boolean safeToLook(float yaw, float pitch) {
        if (!enabled() || !context.worldReady()) return true;

        return !PlaneEndermanLookMath.unsafeLook(
            context.player().getEyePos(),
            yaw,
            pitch,
            config.endermanLookRadius(),
            visibleEndermanLookPoints()
        );
    }

    Float safeDownwardPitch(float yaw, float desiredPitch) {
        if (!enabled() || !context.worldReady()) return PlaneEndermanLookMath.clampPitch(desiredPitch);

        return PlaneEndermanLookMath.safeDownwardPitch(
            context.player().getEyePos(),
            yaw,
            desiredPitch,
            config.safeIdleLookPitch(),
            config.endermanLookRadius(),
            visibleEndermanLookPoints()
        );
    }

    boolean applyMovementLook(float yaw, float desiredPitch) {
        if (!context.worldReady()) return false;

        Float safePitch = safeDownwardPitch(yaw, desiredPitch);
        if (safePitch == null) {
            lookDown();
            return false;
        }

        context.player().setYaw(yaw);
        context.player().setPitch(safePitch);
        return true;
    }

    void lookDown() {
        if (!context.worldReady()) return;

        context.player().setPitch(PlaneEndermanLookMath.clampIdlePitch(config.safeIdleLookPitch()));
    }

    void lookDownIfUnsafe() {
        if (!context.worldReady()) return;
        if (!safeToLook(context.player().getYaw(), context.player().getPitch())) lookDown();
    }

    private List<Vec3d> visibleEndermanLookPoints() {
        List<Vec3d> points = new ArrayList<>();
        if (!context.worldReady()) return points;

        for (Entity entity : context.world().getEntities()) {
            if (entity == null || entity.getType() != EntityType.ENDERMAN || !entity.isAlive()) continue;
            if (!EntityUtils.isAttackable(entity.getType())) continue;
            if (!PlayerUtils.isWithin(entity, config.endermanLookRadius())) continue;
            if (!PlayerUtils.canSeeEntity(entity)) continue;

            Vec3d pos = entity.getPos();
            points.add(pos.add(0.0, 1.35, 0.0));
            points.add(pos.add(0.0, 1.95, 0.0));
            points.add(pos.add(0.0, 2.55, 0.0));
        }

        return points;
    }

    interface Config {
        boolean endermanLookSafetyEnabled();

        double endermanLookRadius();

        int safeIdleLookPitch();
    }
}
