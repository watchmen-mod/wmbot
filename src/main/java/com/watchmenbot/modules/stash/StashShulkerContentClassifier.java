package com.watchmenbot.modules.stash;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

final class StashShulkerContentClassifier {
    static final String ECHEST_ALIAS = "echest";

    private StashShulkerContentClassifier() {
    }

    static ShulkerContent classify(ItemStack stack) {
        if (!isShulkerBoxStack(stack)) return ShulkerContent.notEchest();

        ShulkerContent modern = classifyContainerComponent(stack);
        if (modern.nonEmptyStacks() > 0 || modern.mixedContents()) return modern;

        return classifyLegacyShulkerData(stack);
    }

    static boolean isPureEchestShulker(ItemStack stack) {
        return classify(stack).pureEchest();
    }

    static boolean isEchestAlias(String kitAlias) {
        return StashKitNameNormalizer.canonicalAlias(kitAlias).equals(ECHEST_ALIAS);
    }

    private static boolean isShulkerBoxStack(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    private static ShulkerContent classifyContainerComponent(ItemStack stack) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return ShulkerContent.notEchest();

        int nonEmptyStacks = 0;
        int enderChests = 0;
        for (ItemStack contained : container.iterateNonEmpty()) {
            nonEmptyStacks++;
            if (!contained.isOf(Items.ENDER_CHEST)) return new ShulkerContent(false, enderChests, nonEmptyStacks, true);

            enderChests += contained.getCount();
        }

        return new ShulkerContent(nonEmptyStacks > 0, enderChests, nonEmptyStacks, false);
    }

    private static ShulkerContent classifyLegacyShulkerData(ItemStack stack) {
        ShulkerContent blockEntityData = classifyLegacyShulkerData(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA));
        ShulkerContent customData = classifyLegacyShulkerData(stack.get(DataComponentTypes.CUSTOM_DATA));

        if (blockEntityData.nonEmptyStacks() > 0 || blockEntityData.mixedContents()) return blockEntityData;
        return customData;
    }

    private static ShulkerContent classifyLegacyShulkerData(NbtComponent component) {
        if (component == null || component.isEmpty()) return ShulkerContent.notEchest();

        NbtCompound nbt = component.copyNbt();
        ShulkerContent direct = classifyItemsList(nbt);
        if (direct.nonEmptyStacks() > 0 || direct.mixedContents()) return direct;

        return classifyItemsList(nbt.getCompoundOrEmpty("BlockEntityTag"));
    }

    private static ShulkerContent classifyItemsList(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) return ShulkerContent.notEchest();

        int nonEmptyStacks = 0;
        int enderChests = 0;
        NbtList items = nbt.getListOrEmpty("Items");
        for (NbtCompound item : items.streamCompounds().toList()) {
            nonEmptyStacks++;
            String id = item.getString("id", "");
            if (!"minecraft:ender_chest".equals(id) && !"ender_chest".equals(id)) {
                return new ShulkerContent(false, enderChests, nonEmptyStacks, true);
            }

            enderChests += Math.max(1, item.getByte("count", item.getByte("Count", (byte) 1)));
        }

        return new ShulkerContent(nonEmptyStacks > 0, enderChests, nonEmptyStacks, false);
    }

    record ShulkerContent(boolean pureEchest, int enderChestCount, int nonEmptyStacks, boolean mixedContents) {
        static ShulkerContent notEchest() {
            return new ShulkerContent(false, 0, 0, false);
        }
    }
}
