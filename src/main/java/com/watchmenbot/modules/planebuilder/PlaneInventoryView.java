package com.watchmenbot.modules.planebuilder;

import net.minecraft.block.Block;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.function.Predicate;

final class PlaneInventoryView {
    static final PickaxeSafetyConfig DEFAULT_PICKAXE_SAFETY = () -> PlaneBuilderSettings.PICKAXE_DURABILITY_THRESHOLD_PERCENT;

    private final PlaneRuntimeConfig config;
    private final PlaneClientContext context;
    private final PickaxeSafetyConfig pickaxeSafetyConfig;

    PlaneInventoryView() {
        this(PlaneRuntimeConfig.DEFAULT, new PlaneClientContext(), DEFAULT_PICKAXE_SAFETY);
    }

    PlaneInventoryView(
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PickaxeSafetyConfig pickaxeSafetyConfig
    ) {
        this.config = config;
        this.context = context;
        this.pickaxeSafetyConfig = pickaxeSafetyConfig;
    }

    int countBuildBlock() {
        return countMatching(this::isBuildBlockStack);
    }

    int buildBlockCapacity() {
        return capacityMatching(this::isBuildBlockStack, config.buildBlock().asItem().getDefaultStack().getMaxCount());
    }

    int safeBuildBlockCapacity() {
        int buildBlockMaxCount = config.buildBlock().asItem().getDefaultStack().getMaxCount();
        int currentBuildBlocks = 0;
        int partialBuildBlockRoom = 0;
        int emptyInventorySlots = 0;
        int looseEnderChests = 0;
        boolean hasPartialLooseEnderChestStack = false;
        EnderChestShulkerSourceScan shulkerScan = scanEnderChestShulkerSources();

        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            if (stack.isEmpty()) {
                emptyInventorySlots++;
            }
            else if (isBuildBlockStack(stack)) {
                currentBuildBlocks += stack.getCount();
                partialBuildBlockRoom += Math.max(0, stack.getMaxCount() - stack.getCount());
            }
            else if (stack.isOf(Items.ENDER_CHEST)) {
                looseEnderChests += stack.getCount();
                hasPartialLooseEnderChestStack = hasPartialLooseEnderChestStack || stack.getCount() < stack.getMaxCount();
            }
        }

        ItemStack offhand = context.player().getOffHandStack();
        if (isBuildBlockStack(offhand)) {
            currentBuildBlocks += offhand.getCount();
            partialBuildBlockRoom += Math.max(0, offhand.getMaxCount() - offhand.getCount());
        }
        else if (offhand.isOf(Items.ENDER_CHEST)) {
            looseEnderChests += offhand.getCount();
            hasPartialLooseEnderChestStack = hasPartialLooseEnderChestStack || offhand.getCount() < offhand.getMaxCount();
        }

        int capacityWithoutShulkerReservation = PlaneReplenishTargetPolicy.safeBuildBlockCapacity(
            currentBuildBlocks,
            partialBuildBlockRoom,
            emptyInventorySlots,
            buildBlockMaxCount,
            hasPartialLooseEnderChestStack,
            false,
            true
        );
        boolean shulkerSourceMayBeNeeded = shulkerScan.hasVisibleSource()
            && currentBuildBlocks + looseEnderChests * EnderChestFarmProgress.OBSIDIAN_PER_ENDER_CHEST < capacityWithoutShulkerReservation;

        return PlaneReplenishTargetPolicy.safeBuildBlockCapacity(
            currentBuildBlocks,
            partialBuildBlockRoom,
            emptyInventorySlots,
            buildBlockMaxCount,
            hasPartialLooseEnderChestStack,
            shulkerSourceMayBeNeeded,
            true
        );
    }

    int countLooseEnderChests() {
        return countMatching(stack -> stack.isOf(Items.ENDER_CHEST));
    }

    int countEnderChestShulkers() {
        return countMatching(this::isShulkerWithEnderChests);
    }

    int countEnderChestsInShulkers() {
        int count = 0;
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            count += countEnderChestsInShulker(stack) * stack.getCount();
        }

        ItemStack offhand = context.player().getOffHandStack();
        return count + countEnderChestsInShulker(offhand) * offhand.getCount();
    }

    int countEnderChestSupply() {
        return countLooseEnderChests() + countEnderChestsInShulkers();
    }

    boolean hasInventorySpaceForEnderChest() {
        return anyMatching(stack -> stack.isEmpty() || (stack.isOf(Items.ENDER_CHEST) && stack.getCount() < stack.getMaxCount()));
    }

    boolean hasInventorySpaceForEnderChestPreservingShulkerSlot() {
        int emptySlots = 0;
        boolean partialEnderChestStack = false;
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            if (stack.isEmpty()) {
                emptySlots++;
            }
            else if (stack.isOf(Items.ENDER_CHEST) && stack.getCount() < stack.getMaxCount()) {
                partialEnderChestStack = true;
            }
        }

        ItemStack offhand = context.player().getOffHandStack();
        if (offhand.isEmpty()) {
            emptySlots++;
        }
        else if (offhand.isOf(Items.ENDER_CHEST) && offhand.getCount() < offhand.getMaxCount()) {
            partialEnderChestStack = true;
        }

        return PlaneInventoryQueries.enderChestPickupPreservesShulkerSlot(partialEnderChestStack, emptySlots);
    }

    boolean hasInventorySpaceForCleanupDrop(ItemStack dropStack) {
        return PlaneInventoryQueries.cleanupDropPickupable(
            isBuildBlockStack(dropStack),
            isShulkerBoxStack(dropStack),
            anyMatching(ItemStack::isEmpty),
            anyMatching(stack -> isBuildBlockStack(stack) && stack.getCount() < stack.getMaxCount())
        );
    }

    boolean hasEnderChestShulkerInMainInventory() {
        return findMainInventoryEnderChestShulkerSlot() >= 0;
    }

    int findMainInventoryBuildBlockSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (isBuildBlockStack(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    int findMainInventoryEnderChestSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (context.player().getInventory().getStack(slot).isOf(Items.ENDER_CHEST)) return slot;
        }

        return -1;
    }

    int findMainInventoryEnderChestShulkerSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (isShulkerWithEnderChests(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    EnderChestShulkerSourceScan scanEnderChestShulkerSources() {
        int hotbarSlot = -1;
        int hotbarStackCount = 0;
        int hotbarEnderChestCount = Integer.MAX_VALUE;
        int mainInventorySlot = -1;
        int shulkerStacks = 0;
        int containedEnderChests = 0;
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            int enderChestCount = countEnderChestsInShulker(stack);
            if (enderChestCount <= 0) continue;

            shulkerStacks += stack.getCount();
            containedEnderChests += enderChestCount * stack.getCount();
            if (slot < 9 && enderChestCount < hotbarEnderChestCount) {
                hotbarSlot = slot;
                hotbarStackCount = stack.getCount();
                hotbarEnderChestCount = enderChestCount;
            }
            else if (slot >= 9 && mainInventorySlot < 0) {
                mainInventorySlot = slot;
            }
        }

        ItemStack offhandStack = context.player().getOffHandStack();
        int offhandEnderChestCount = countEnderChestsInShulker(offhandStack);
        boolean offhand = offhandEnderChestCount > 0;
        if (offhand) {
            shulkerStacks += offhandStack.getCount();
            containedEnderChests += offhandEnderChestCount * offhandStack.getCount();
        }

        ItemStack cursorStack = context.player().currentScreenHandler != context.player().playerScreenHandler
            ? context.player().currentScreenHandler.getCursorStack()
            : ItemStack.EMPTY;
        int cursorEnderChestCount = countEnderChestsInShulker(cursorStack);
        boolean cursor = cursorEnderChestCount > 0;
        if (cursor) {
            shulkerStacks += cursorStack.getCount();
            containedEnderChests += cursorEnderChestCount * cursorStack.getCount();
        }

        return new EnderChestShulkerSourceScan(
            hotbarSlot,
            hotbarStackCount,
            mainInventorySlot,
            offhand,
            cursor,
            shulkerStacks,
            containedEnderChests
        );
    }

    int findMainInventoryPickaxeSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (isUsablePickaxeStack(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    int findMainInventoryBowSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (isUsableBowStack(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    int findMainInventorySwordSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 9; slot < inventorySlots; slot++) {
            if (isUsableSwordStack(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    int findTrashSlot() {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            if (PlaneItemClassifier.isTrashStack(context.player().getInventory().getStack(slot))) return slot;
        }

        return -1;
    }

    int findOpenShulkerEnderChestSlot(ScreenHandler handler) {
        int endSlot = Math.min(27, handler.slots.size());
        for (int slot = 0; slot < endSlot; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isOf(Items.ENDER_CHEST)) return slot;
        }

        return -1;
    }

    boolean isBuildBlockStack(ItemStack stack) {
        return isBlockStack(stack, config.buildBlock());
    }

    boolean isBlockStack(ItemStack stack, Block block) {
        return PlaneItemClassifier.isBlockStack(stack, block);
    }

    boolean isShulkerBoxStack(ItemStack stack) {
        return PlaneItemClassifier.isShulkerBoxStack(stack);
    }

    boolean isShulkerWithEnderChests(ItemStack stack) {
        return PlaneItemClassifier.isShulkerWithEnderChests(stack);
    }

    boolean isUsablePickaxeStack(ItemStack stack) {
        return PlaneItemClassifier.isUsablePickaxeStack(stack, pickaxeSafetyConfig.pickaxeDurabilityThresholdPercent());
    }

    boolean isUsableBowStack(ItemStack stack) {
        return PlaneItemClassifier.isUsableBowStack(stack, PlaneBuilderSettings.PICKAXE_DURABILITY_THRESHOLD_PERCENT);
    }

    boolean isUsableSwordStack(ItemStack stack) {
        return PlaneItemClassifier.isUsableSwordStack(stack, PlaneBuilderSettings.PICKAXE_DURABILITY_THRESHOLD_PERCENT);
    }

    int countEnderChestsInShulker(ItemStack stack) {
        return PlaneItemClassifier.countEnderChestsInShulker(stack);
    }

    static boolean isMainInventorySlot(int slot, int inventorySize) {
        return slot >= 9 && slot < mainInventoryEnd(inventorySize);
    }

    static int mainInventoryEnd(int inventorySize) {
        return Math.min(36, inventorySize);
    }

    private int countMatching(Predicate<ItemStack> predicate) {
        int count = 0;
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            if (predicate.test(stack)) count += stack.getCount();
        }

        ItemStack offhand = context.player().getOffHandStack();
        if (predicate.test(offhand)) count += offhand.getCount();
        return count;
    }

    private boolean anyMatching(Predicate<ItemStack> predicate) {
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            if (predicate.test(stack)) return true;
        }

        return predicate.test(context.player().getOffHandStack());
    }

    private int capacityMatching(Predicate<ItemStack> predicate, int emptySlotCapacity) {
        int capacity = 0;
        int inventorySlots = mainInventoryEnd(context.player().getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            capacity += stackCapacity(context.player().getInventory().getStack(slot), predicate, emptySlotCapacity);
        }

        capacity += stackCapacity(context.player().getOffHandStack(), predicate, emptySlotCapacity);
        return capacity;
    }

    private int stackCapacity(ItemStack stack, Predicate<ItemStack> predicate, int emptySlotCapacity) {
        if (stack.isEmpty()) return emptySlotCapacity;
        return predicate.test(stack) ? stack.getMaxCount() : 0;
    }

    interface PickaxeSafetyConfig {
        int pickaxeDurabilityThresholdPercent();
    }
}
