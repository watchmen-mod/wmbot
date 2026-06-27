package com.watchmenbot.modules.planebuilder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

final class PlaneBowUseActions {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneActionExecutor actions = new PlaneActionExecutor();

    void holdUse() {
        actions.pressUseKey();
    }

    void release() {
        actions.stopUsingItem();
    }

    void clearUse() {
        actions.releaseUseKey();
    }

    int useTicks() {
        return mc.player != null && mc.player.isUsingItem() ? mc.player.getItemUseTime() : 0;
    }

    boolean usingItem() {
        return mc.player != null && mc.player.isUsingItem();
    }

    boolean mainHandBow() {
        return mc.player != null && mc.player.getMainHandStack().isOf(Items.BOW);
    }

    int selectedSlot() {
        return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
    }

    String mainHandItemName() {
        if (mc.player == null) return "none";

        ItemStack stack = mc.player.getMainHandStack();
        return stack.isEmpty() ? "empty" : stack.getItem().toString();
    }

    ClientPlayerEntity player() {
        return mc.player;
    }
}
