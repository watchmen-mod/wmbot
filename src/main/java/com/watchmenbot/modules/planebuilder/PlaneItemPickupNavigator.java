package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.BaritoneCompatibility;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

final class PlaneItemPickupNavigator implements PlaneDroppedItemPickupWorkflow.Navigator<ItemEntity> {
    private final BaritoneItemPickupPathing pathing = BaritoneCompatibility.available() ? new BaritonePlaneItemPickupNavigator() : null;
    private final PlaneEndermanLookSafety endermanLookSafety;

    private UUID targetId;
    private BlockPos targetPos;
    private int repathCooldown;
    private boolean active;

    PlaneItemPickupNavigator() {
        this(new PlaneEndermanLookSafety());
    }

    PlaneItemPickupNavigator(PlaneEndermanLookSafety endermanLookSafety) {
        this.endermanLookSafety = endermanLookSafety;
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
        boolean changedTarget = !nextId.equals(targetId);
        boolean changedPos = !nextPos.equals(targetPos);

        if (!changedTarget && !changedPos && isPathing()) return;
        if (!changedTarget && !changedPos && repathCooldown > 0) {
            repathCooldown--;
            return;
        }

        targetId = nextId;
        targetPos = nextPos;
        repathCooldown = PlanePickupSettings.REPATH_COOLDOWN_TICKS;
        pathing.pathTo(nextPos);
        endermanLookSafety.lookDownIfUnsafe();
    }

    @Override
    public void stop() {
        if (active) {
            pathing.stop();
        }

        active = false;
        targetId = null;
        targetPos = null;
        repathCooldown = 0;
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
}
