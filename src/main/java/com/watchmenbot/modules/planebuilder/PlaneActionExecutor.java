package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class PlaneActionExecutor {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneRuntimeConfig config;
    private final PlaneEndermanLookSafety endermanLookSafety;

    PlaneActionExecutor() {
        this(PlaneRuntimeConfig.DEFAULT, new PlaneEndermanLookSafety());
    }

    PlaneActionExecutor(PlaneRuntimeConfig config) {
        this(config, new PlaneEndermanLookSafety());
    }

    PlaneActionExecutor(PlaneRuntimeConfig config, PlaneEndermanLookSafety endermanLookSafety) {
        this.config = config;
        this.endermanLookSafety = endermanLookSafety;
    }

    void rotate(Vec3d hit, Runnable action) {
        float yaw = (float) Rotations.getYaw(hit);
        float pitch = (float) Rotations.getPitch(hit);
        if (!endermanLookSafety.safeToLook(yaw, pitch)) {
            endermanLookSafety.lookDown();
            return;
        }

        Rotations.rotate(yaw, pitch, config.rotationPriority(), action);
    }

    void withHotbarSwap(int slot, Runnable action) {
        InvUtils.swap(slot, true);
        try {
            action.run();
        }
        finally {
            InvUtils.swapBack();
        }
    }

    boolean swapToHotbarSlot(int slot) {
        return InvUtils.swap(slot, true);
    }

    void swapBack() {
        InvUtils.swapBack();
    }

    void interact(BlockHitResult hitResult, Hand hand) {
        BlockUtils.interact(hitResult, hand, true);
    }

    void breakBlock(BlockPos pos) {
        if (pos != null) {
            Vec3d hit = Vec3d.ofCenter(pos);
            if (!endermanLookSafety.safeToLook((float) Rotations.getYaw(hit), (float) Rotations.getPitch(hit))) {
                endermanLookSafety.lookDown();
                return;
            }
        }

        BlockUtils.breakBlock(pos, true);
    }

    void pressUseKey() {
        mc.options.useKey.setPressed(true);
        if (mc.interactionManager == null || mc.player == null || mc.player.isUsingItem()) return;

        ActionResult result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    void releaseUseKey() {
        mc.options.useKey.setPressed(false);
    }

    void stopUsingItem() {
        mc.options.useKey.setPressed(false);
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
    }
}
