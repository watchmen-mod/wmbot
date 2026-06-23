package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

interface PlaneInventoryAccess {
    int countBuildBlock();

    int effectiveReplenishTarget(int configuredTarget);

    int requiredEnderChestsForTarget(int targetBuildBlocks);

    int countLooseEnderChests();

    boolean hasInventorySpaceForEnderChest();

    FindItemResult findHotbarBuildBlock();

    FindItemResult findEnderChest();

    FindItemResult findHotbarBow();

    FindItemResult prepareUsableBow();

    boolean hasArrows();

    FindItemResult findEnderChestShulkerInHotbar();

    boolean hasEnderChestShulkerInMainInventory();

    int findMainInventoryBuildBlockSlot();

    int findMainInventoryEnderChestShulkerSlot();

    int findMainInventoryPickaxeSlot();

    int findMainInventoryBowSlot();

    boolean hasAnyEnderChestShulker();

    EnderChestShulkerSourceScan scanEnderChestShulkerSources();

    int findOpenShulkerEnderChestSlot(ScreenHandler handler);

    boolean findResultMatchesBuildBlock(FindItemResult result);

    boolean findResultMatchesEnderChestShulker(FindItemResult result);

    boolean findResultMatchesBlock(FindItemResult result, Block block);

    boolean isBuildBlockStack(ItemStack stack);

    boolean isBlockStack(ItemStack stack, Block block);

    boolean isShulkerWithEnderChests(ItemStack stack);

    boolean isShulkerBoxStack(ItemStack stack);

    boolean isEnderChestSupplyStack(ItemStack stack);

    FindItemResult findHotbarPickaxe();
}
