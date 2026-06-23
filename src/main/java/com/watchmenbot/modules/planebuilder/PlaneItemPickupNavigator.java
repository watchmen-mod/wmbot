package com.watchmenbot.modules.planebuilder;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

final class PlaneItemPickupNavigator implements PlaneDroppedItemPickupWorkflow.Navigator<ItemEntity> {
    private final PlaneBaritoneSafetyGuard safetyGuard = new PlaneBaritoneSafetyGuard();
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
        if (target == null) return;

        endermanLookSafety.lookDownIfUnsafe();
        safetyGuard.apply();
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
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(nextPos));
        endermanLookSafety.lookDownIfUnsafe();
    }

    @Override
    public void stop() {
        if (active) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
        }

        active = false;
        targetId = null;
        targetPos = null;
        repathCooldown = 0;
        safetyGuard.restore();
        endermanLookSafety.lookDownIfUnsafe();
    }

    private boolean isPathing() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
    }
}
