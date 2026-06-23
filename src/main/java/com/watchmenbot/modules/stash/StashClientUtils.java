package com.watchmenbot.modules.stash;

import com.watchmenbot.util.ClientWorkGuards;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class StashClientUtils {
    private StashClientUtils() {
    }

    static boolean canUse(MinecraftClient mc) {
        return ClientWorkGuards.interactionReady(mc);
    }

    static String dimensionId(MinecraftClient mc) {
        if (mc.world == null) return "unknown";
        return mc.world.getRegistryKey().getValue().toString();
    }

    static String formatPos(BlockPos pos) {
        return "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    static void closeContainerScreen(MinecraftClient mc) {
        if (mc.player != null && isContainerScreenOpen(mc)) {
            mc.player.closeHandledScreen();
        }
    }

    static boolean isContainerScreenOpen(MinecraftClient mc) {
        if (mc.player == null) return false;

        ScreenHandler handler = mc.player.currentScreenHandler;
        return handler != mc.player.playerScreenHandler;
    }

    static boolean isCloseEnough(MinecraftClient mc, StashTarget target, double range) {
        if (mc.player == null) return false;

        double rangeSq = range * range;
        Vec3d eyes = mc.player.getEyePos();
        for (BlockPos pos : target.positions()) {
            if (eyes.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq) return true;
        }

        return false;
    }

    static double playerBlockDistanceSq(MinecraftClient mc, BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        return mc.player.getBlockPos().getSquaredDistance(pos);
    }
}
