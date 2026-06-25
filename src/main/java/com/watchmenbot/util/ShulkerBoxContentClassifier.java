package com.watchmenbot.util;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

public final class ShulkerBoxContentClassifier {
    private ShulkerBoxContentClassifier() {
    }

    public static boolean isShulkerBoxStack(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    public static Content classify(ItemStack stack) {
        if (!isShulkerBoxStack(stack)) return Content.empty();

        Content modern = classifyContainerComponent(stack);
        if (modern.nonEmptyStacks() > 0 || modern.mixedContents()) return modern;

        return classifyLegacyShulkerData(stack);
    }

    public static int countEnderChests(ItemStack stack) {
        if (!isShulkerBoxStack(stack)) return 0;

        return Math.max(
            countEnderChestsInContainerComponent(stack),
            countEnderChestsInLegacyShulkerData(stack)
        );
    }

    public static boolean isEmptyShulkerBox(ItemStack stack) {
        return isShulkerBoxStack(stack) && classify(stack).nonEmptyStacks() == 0;
    }

    private static Content classifyContainerComponent(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return Content.empty();

        int nonEmptyStacks = 0;
        int enderChests = 0;
        for (ItemStack contained : container.iterateNonEmpty()) {
            nonEmptyStacks++;
            if (!contained.isOf(Items.ENDER_CHEST)) return new Content(nonEmptyStacks, enderChests, true);

            enderChests += contained.getCount();
        }

        return new Content(nonEmptyStacks, enderChests, false);
    }

    private static Content classifyLegacyShulkerData(ItemStack stack) {
        Content blockEntityData = classifyLegacyShulkerData(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA));
        Content customData = classifyLegacyShulkerData(stack.get(DataComponentTypes.CUSTOM_DATA));

        if (blockEntityData.nonEmptyStacks() > 0 || blockEntityData.mixedContents()) return blockEntityData;
        return customData;
    }

    private static Content classifyLegacyShulkerData(NbtComponent component) {
        if (component == null || component.isEmpty()) return Content.empty();

        NbtCompound nbt = component.copyNbt();
        Content direct = classifyItemsList(nbt);
        if (direct.nonEmptyStacks() > 0 || direct.mixedContents()) return direct;

        return classifyItemsList(nbt.getCompoundOrEmpty("BlockEntityTag"));
    }

    private static Content classifyItemsList(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) return Content.empty();

        int nonEmptyStacks = 0;
        int enderChests = 0;
        NbtList items = nbt.getListOrEmpty("Items");
        for (NbtCompound item : items.streamCompounds().toList()) {
            nonEmptyStacks++;
            String id = item.getString("id", "");
            if (!"minecraft:ender_chest".equals(id) && !"ender_chest".equals(id)) {
                return new Content(nonEmptyStacks, enderChests, true);
            }

            enderChests += Math.max(1, item.getByte("count", item.getByte("Count", (byte) 1)));
        }

        return new Content(nonEmptyStacks, enderChests, false);
    }

    private static int countEnderChestsInContainerComponent(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack contained : container.iterateNonEmpty()) {
            if (contained.isOf(Items.ENDER_CHEST)) count += contained.getCount();
        }

        return count;
    }

    private static int countEnderChestsInLegacyShulkerData(ItemStack stack) {
        return Math.max(
            countEnderChestsInLegacyShulkerData(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA)),
            countEnderChestsInLegacyShulkerData(stack.get(DataComponentTypes.CUSTOM_DATA))
        );
    }

    private static int countEnderChestsInLegacyShulkerData(NbtComponent component) {
        if (component == null || component.isEmpty()) return 0;

        NbtCompound nbt = component.copyNbt();
        int directCount = countEnderChestsInItemsList(nbt);
        if (directCount > 0) return directCount;

        return countEnderChestsInItemsList(nbt.getCompoundOrEmpty("BlockEntityTag"));
    }

    private static int countEnderChestsInItemsList(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) return 0;

        int count = 0;
        NbtList items = nbt.getListOrEmpty("Items");
        for (NbtCompound item : items.streamCompounds().toList()) {
            String id = item.getString("id", "");
            if ("minecraft:ender_chest".equals(id) || "ender_chest".equals(id)) {
                count += Math.max(1, item.getByte("count", item.getByte("Count", (byte) 1)));
            }
        }

        return count;
    }

    public record Content(int nonEmptyStacks, int enderChestCount, boolean mixedContents) {
        private static Content empty() {
            return new Content(0, 0, false);
        }

        public boolean pureEnderChestContents() {
            return nonEmptyStacks > 0 && !mixedContents;
        }
    }
}
