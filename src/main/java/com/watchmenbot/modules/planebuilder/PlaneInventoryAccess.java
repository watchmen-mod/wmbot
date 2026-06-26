package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

interface PlaneInventoryAccess {
    int countBuildBlock();

    int effectiveReplenishTarget(int configuredTarget, boolean useAvailableSafeInventorySpace);

    int effectiveReplenishTarget(
        int configuredTarget,
        boolean useAvailableSafeInventorySpace,
        boolean reserveManagedShulkerSlot
    );

    int requiredEnderChestsForTarget(int targetBuildBlocks);

    int countLooseEnderChests();

    boolean hasInventorySpaceForEnderChest();

    boolean hasInventorySpaceForEnderChestPreservingShulkerSlot();

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

    int findMainInventorySwordSlot();

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

    FindItemResult prepareUsablePickaxe();

    FindItemResult findHotbarSword();

    FindItemResult prepareUsableSword();
}
