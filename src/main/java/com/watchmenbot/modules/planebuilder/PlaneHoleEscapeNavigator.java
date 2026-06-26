package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.BaritoneCompatibility;
import net.minecraft.util.math.BlockPos;

final class PlaneHoleEscapeNavigator implements PlaneHoleEscapeController.Navigator {
    private final BaritoneHoleEscapePathing pathing = BaritoneCompatibility.available() ? new BaritonePlaneHoleEscapeNavigator() : null;

    private BlockPos targetPos;
    private boolean active;

    @Override
    public void pathTo(BlockPos target) {
        if (pathing == null) return;
        if (target == null) return;

        pathing.applySafety();
        active = true;

        if (target.equals(targetPos) && isPathing()) return;

        targetPos = target.toImmutable();
        pathing.pathTo(targetPos);
    }

    @Override
    public void stop() {
        if (active) {
            pathing.stop();
        }

        active = false;
        targetPos = null;
        if (pathing != null) pathing.restoreSafety();
    }

    private boolean isPathing() {
        return pathing != null && pathing.isPathing();
    }

    interface BaritoneHoleEscapePathing {
        void applySafety();

        void restoreSafety();

        void pathTo(BlockPos target);

        boolean isPathing();

        void stop();
    }
}
