package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class PlaneTrashEdgeNavigator {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneEndermanLookSafety endermanLookSafety;

    PlaneTrashEdgeNavigator() {
        this(new PlaneEndermanLookSafety());
    }

    PlaneTrashEdgeNavigator(PlaneEndermanLookSafety endermanLookSafety) {
        this.endermanLookSafety = endermanLookSafety;
    }

    void walkTo(Vec3d target) {
        if (target == null || mc.player == null) return;

        if (face(new Vec3d(target.x, mc.player.getEyeY(), target.z))) {
            setWalkingKeys(true);
        }
        else {
            setWalkingKeys(false);
        }
    }

    void stop() {
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
