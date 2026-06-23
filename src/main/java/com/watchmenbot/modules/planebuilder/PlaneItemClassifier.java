package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;

final class PlaneItemClassifier {
    private PlaneItemClassifier() {
    }

    static boolean isBuildBlockStack(ItemStack stack) {
        return isBlockStack(stack, PlaneBuilderSettings.buildBlock());
    }

    static boolean isBlockStack(ItemStack stack, Block block) {
        return stack != null
            && !stack.isEmpty()
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() == block;
    }

    static boolean isShulkerBoxStack(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    static boolean isShulkerWithEnderChests(ItemStack stack) {
        return countEnderChestsInShulker(stack) > 0;
    }

    static boolean isEmptyShulkerBoxStack(ItemStack stack) {
        if (!isShulkerBoxStack(stack)) return false;

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack ignored : container.iterateNonEmpty()) {
                return false;
            }
        }

        return !legacyShulkerDataHasItems(stack);
    }

    static int countEnderChestsInShulker(ItemStack stack) {
        if (!isShulkerBoxStack(stack)) return 0;

        return Math.max(
            countEnderChestsInContainerComponent(stack),
            countEnderChestsInLegacyShulkerData(stack)
        );
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

    private static boolean legacyShulkerDataHasItems(ItemStack stack) {
        return legacyShulkerDataHasItems(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA))
            || legacyShulkerDataHasItems(stack.get(DataComponentTypes.CUSTOM_DATA));
    }

    private static boolean legacyShulkerDataHasItems(NbtComponent component) {
        if (component == null || component.isEmpty()) return false;

        NbtCompound nbt = component.copyNbt();
        if (!nbt.getListOrEmpty("Items").isEmpty()) return true;

        return !nbt.getCompoundOrEmpty("BlockEntityTag").getListOrEmpty("Items").isEmpty();
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

    static boolean isEnderChestSupplyStack(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && (stack.isOf(Items.ENDER_CHEST) || isShulkerWithEnderChests(stack));
    }

    static boolean isUsablePickaxeStack(ItemStack stack, int durabilityThresholdPercent) {
        if (stack == null || stack.isEmpty()) return false;

        return isUsablePickaxe(
            stack.isIn(ItemTags.PICKAXES),
            hasSilkTouch(stack),
            stack.getMaxDamage(),
            stack.getDamage(),
            durabilityThresholdPercent
        );
    }

    static boolean isUsableBowStack(ItemStack stack, int durabilityThresholdPercent) {
        if (stack == null || stack.isEmpty()) return false;

        return isUsableBow(
            stack.isOf(Items.BOW),
            stack.getMaxDamage(),
            stack.getDamage(),
            durabilityThresholdPercent
        );
    }

    static boolean isUsableElytraStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return isUsableElytra(
            stack.isOf(Items.ELYTRA),
            stack.getMaxDamage(),
            stack.getDamage()
        );
    }

    static boolean isUsablePickaxe(
        boolean pickaxe,
        boolean silkTouch,
        int maxDamage,
        int damage,
        int durabilityThresholdPercent
    ) {
        return pickaxe
            && !silkTouch
            && maxDamage > 0
            && remainingDurabilityPercent(maxDamage, damage) >= durabilityThresholdPercent;
    }

    static boolean isUsableBow(
        boolean bow,
        int maxDamage,
        int damage,
        int durabilityThresholdPercent
    ) {
        return bow
            && maxDamage > 0
            && remainingDurabilityPercent(maxDamage, damage) >= durabilityThresholdPercent;
    }

    static boolean isUsableElytra(boolean elytra, int maxDamage, int damage) {
        return elytra
            && maxDamage > 0
            && remainingDurability(maxDamage, damage) > 1;
    }

    static int remainingDurability(int maxDamage, int damage) {
        return Math.max(0, maxDamage - damage);
    }

    static double remainingDurabilityPercent(int maxDamage, int damage) {
        if (maxDamage <= 0) return 0.0;
        return remainingDurability(maxDamage, damage) * 100.0 / maxDamage;
    }

    static boolean hasSilkTouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.SILK_TOUCH)) return true;
        }

        return false;
    }

    static boolean isKitbotRefillDrop(ItemStack stack) {
        return isEnderChestSupplyStack(stack) || isShulkerBoxStack(stack);
    }

    static boolean isReplenishCleanupDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return cleanupDropKind(stack.isOf(Items.OBSIDIAN), isShulkerBoxStack(stack));
    }

    static boolean isTrashStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean emptyShulkerBox = isEmptyShulkerBoxStack(stack);
        return trashKind(
            trashAllowedKind(
                isMobTrashItem(stack) || isCryingObsidianTrashItem(stack),
                isIngotTrashItem(stack),
                isMobEquipmentTrashItem(stack)
            ) || emptyShulkerBox,
            isBuildBlockStack(stack),
            isEnderChestSupplyStack(stack),
            isShulkerBoxStack(stack),
            emptyShulkerBox,
            stack.isOf(Items.ARROW) || stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.TIPPED_ARROW),
            stack.isOf(Items.ENDER_PEARL)
        );
    }

    static boolean isMobTrashItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.ROTTEN_FLESH)
            || stack.isOf(Items.BONE)
            || stack.isOf(Items.STRING)
            || stack.isOf(Items.SPIDER_EYE)
            || stack.isOf(Items.GUNPOWDER)
            || stack.isOf(Items.SLIME_BALL)
            || stack.isOf(Items.MAGMA_CREAM)
            || stack.isOf(Items.PHANTOM_MEMBRANE);
    }

    static boolean isCryingObsidianTrashItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return cryingObsidianTrashKind(stack.isOf(Items.CRYING_OBSIDIAN));
    }

    static boolean isIngotTrashItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isOf(Items.IRON_INGOT)
            || stack.isOf(Items.GOLD_INGOT)
            || stack.isOf(Items.COPPER_INGOT)
            || stack.isOf(Items.NETHERITE_INGOT);
    }

    static boolean isMobEquipmentTrashItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return mobEquipmentTrashKind(
            stack.isOf(Items.IRON_SWORD)
                || stack.isOf(Items.IRON_SHOVEL)
                || stack.isOf(Items.IRON_PICKAXE)
                || stack.isOf(Items.IRON_AXE)
                || stack.isOf(Items.IRON_HOE)
                || stack.isOf(Items.IRON_HELMET)
                || stack.isOf(Items.IRON_CHESTPLATE)
                || stack.isOf(Items.IRON_LEGGINGS)
                || stack.isOf(Items.IRON_BOOTS),
            stack.isOf(Items.GOLDEN_SWORD)
                || stack.isOf(Items.GOLDEN_SHOVEL)
                || stack.isOf(Items.GOLDEN_PICKAXE)
                || stack.isOf(Items.GOLDEN_AXE)
                || stack.isOf(Items.GOLDEN_HOE)
                || stack.isOf(Items.GOLDEN_HELMET)
                || stack.isOf(Items.GOLDEN_CHESTPLATE)
                || stack.isOf(Items.GOLDEN_LEGGINGS)
                || stack.isOf(Items.GOLDEN_BOOTS),
            stack.isOf(Items.CHAINMAIL_HELMET)
                || stack.isOf(Items.CHAINMAIL_CHESTPLATE)
                || stack.isOf(Items.CHAINMAIL_LEGGINGS)
                || stack.isOf(Items.CHAINMAIL_BOOTS),
            stack.isOf(Items.LEATHER_HELMET)
                || stack.isOf(Items.LEATHER_CHESTPLATE)
                || stack.isOf(Items.LEATHER_LEGGINGS)
                || stack.isOf(Items.LEATHER_BOOTS)
        );
    }

    static boolean cleanupDropKind(boolean obsidian, boolean shulkerBox) {
        return obsidian || shulkerBox;
    }

    static boolean cryingObsidianTrashKind(boolean cryingObsidian) {
        return cryingObsidian;
    }

    static boolean mobEquipmentTrashKind(boolean ironEquipment, boolean goldEquipment, boolean chainmailArmor, boolean leatherArmor) {
        return ironEquipment || goldEquipment || chainmailArmor || leatherArmor;
    }

    static boolean trashAllowedKind(boolean mobTrash, boolean ingotTrash, boolean mobEquipmentTrash) {
        return mobTrash || ingotTrash || mobEquipmentTrash;
    }

    static boolean trashKind(
        boolean allowedTrash,
        boolean buildBlock,
        boolean enderChestSupply,
        boolean shulkerBox,
        boolean emptyShulkerBox,
        boolean arrow,
        boolean enderPearl
    ) {
        return allowedTrash
            && !buildBlock
            && !enderChestSupply
            && (!shulkerBox || emptyShulkerBox)
            && !arrow
            && !enderPearl;
    }
}
