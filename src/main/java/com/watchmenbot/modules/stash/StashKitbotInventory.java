package com.watchmenbot.modules.stash;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class StashKitbotInventory {
    private final MinecraftClient mc;

    StashKitbotInventory(MinecraftClient mc) {
        this.mc = mc;
    }

    int emptyInventorySlots() {
        if (mc.player == null) return 0;

        int empty = 0;
        int playerStorageSlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < playerStorageSlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) empty++;
        }

        return empty;
    }

    int findNextMatchingContainerSlot(ScreenHandler handler, int openedSize, KitSource source, String kitAlias) {
        Set<Integer> cachedSlots = source == null ? Set.of() : new HashSet<>(source.slots());

        for (int slot : cachedSlots) {
            if (slot >= 0 && slot < openedSize && stackMatchesAlias(handler.getSlot(slot).getStack(), kitAlias)) return slot;
        }

        for (int slot = 0; slot < openedSize; slot++) {
            if (stackMatchesAlias(handler.getSlot(slot).getStack(), kitAlias)) return slot;
        }

        return -1;
    }

    int matchingInventoryCount(String kitAlias) {
        int count = 0;
        if (mc.player == null) return count;

        int playerStorageSlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < playerStorageSlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stackMatchesAlias(stack, kitAlias)) count += stack.getCount();
        }

        return count;
    }

    Map<String, Integer> matchingKitCounts(String query, boolean quotedSearch) {
        Map<String, Integer> counts = new HashMap<>();
        if (mc.player == null) return counts;

        boolean echestQuery = StashShulkerContentClassifier.isEchestAlias(query);
        int playerStorageSlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < playerStorageSlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) continue;

            if (echestQuery) {
                if (StashShulkerContentClassifier.isPureEchestShulker(stack)) {
                    counts.merge(StashShulkerContentClassifier.ECHEST_ALIAS, stack.getCount(), Integer::sum);
                }
                continue;
            }

            String name = displayName(stack);
            if (StashKitNameNormalizer.matches(name, query, quotedSearch)) counts.merge(name, stack.getCount(), Integer::sum);
        }

        return counts;
    }

    Map<String, Integer> allKitCounts() {
        Map<String, Integer> counts = new HashMap<>();
        if (mc.player == null) return counts;

        int playerStorageSlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < playerStorageSlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) continue;

            if (StashShulkerContentClassifier.isPureEchestShulker(stack)) {
                counts.merge(StashShulkerContentClassifier.ECHEST_ALIAS, stack.getCount(), Integer::sum);
            }
            counts.merge(displayName(stack), stack.getCount(), Integer::sum);
        }

        return counts;
    }

    int findMatchingInventorySlot(String kitAlias) {
        int playerStorageSlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < playerStorageSlots; slot++) {
            if (stackMatchesAlias(mc.player.getInventory().getStack(slot), kitAlias)) return slot;
        }

        return -1;
    }

    boolean prepareThrowSlot(int inventorySlot, String kitAlias) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            mc.player.getInventory().setSelectedSlot(inventorySlot);
            return stackMatchesAlias(mc.player.getInventory().getStack(inventorySlot), kitAlias);
        }

        int hotbarSlot = findThrowHotbarSlot(kitAlias);
        if (hotbarSlot < 0) return false;

        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            playerInventoryScreenSlot(inventorySlot),
            hotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );

        if (!stackMatchesAlias(mc.player.getInventory().getStack(hotbarSlot), kitAlias)) return false;

        mc.player.getInventory().setSelectedSlot(hotbarSlot);
        return true;
    }

    boolean dropSelectedPreparedSlot(String kitAlias) {
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (!stackMatchesAlias(mc.player.getInventory().getStack(selectedSlot), kitAlias)) return false;

        return mc.player.dropSelectedItem(false);
    }

    boolean stackMatchesAlias(ItemStack stack, String kitAlias) {
        if (stack == null || stack.isEmpty() || !(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) return false;
        if (StashShulkerContentClassifier.isEchestAlias(kitAlias)) return StashShulkerContentClassifier.isPureEchestShulker(stack);

        return StashKitNameNormalizer.matchesAliasKey(displayName(stack), kitAlias);
    }

    private int findThrowHotbarSlot(String kitAlias) {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getStack(selected).isEmpty()) return selected;

        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }

        for (int slot = 0; slot <= 8; slot++) {
            if (!stackMatchesAlias(mc.player.getInventory().getStack(slot), kitAlias)) return slot;
        }

        return -1;
    }

    private int playerInventoryScreenSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private String displayName(ItemStack stack) {
        Text customName = stack.getCustomName();
        if (customName != null) return customName.getString();

        return stack.getName().getString();
    }
}
