package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.block.Block;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

final class PlaneInventory implements PlaneInventoryAccess, PlaneInventoryCommands {
    private final PlaneClientContext context;
    private final PlaneRuntimeConfig config;
    private final PlaneInventoryView view;
    private final PlaneInventoryMover mover;
    private final PlaneInventoryPreparation preparation;

    PlaneInventory(PlaneActionGuards guards) {
        this(guards, PlaneRuntimeConfig.DEFAULT, new PlaneClientContext(), PlaneInventoryView.DEFAULT_PICKAXE_SAFETY);
    }

    PlaneInventory(
        PlaneActionGuards guards,
        PlaneRuntimeConfig config,
        PlaneClientContext context,
        PlaneInventoryView.PickaxeSafetyConfig pickaxeSafetyConfig
    ) {
        this.context = context;
        this.config = config;
        view = new PlaneInventoryView(config, context, pickaxeSafetyConfig);
        mover = new PlaneInventoryMover(guards, this);
        preparation = new PlaneInventoryPreparation(this, mover);
    }

    @Override
    public int countBuildBlock() {
        return view.countBuildBlock();
    }

    @Override
    public int effectiveReplenishTarget(int configuredTarget, boolean useAvailableSafeInventorySpace) {
        return effectiveReplenishTarget(configuredTarget, useAvailableSafeInventorySpace, false);
    }

    @Override
    public int effectiveReplenishTarget(
        int configuredTarget,
        boolean useAvailableSafeInventorySpace,
        boolean reserveManagedShulkerSlot
    ) {
        int safeBuildBlockCapacity = useAvailableSafeInventorySpace ? view.safeBuildBlockCapacity(reserveManagedShulkerSlot) : 0;
        return PlaneReplenishTargetPolicy.effectiveTarget(
            configuredTarget,
            useAvailableSafeInventorySpace,
            config.replenishMinBuildBlocks(),
            view.buildBlockCapacity(),
            safeBuildBlockCapacity
        );
    }

    @Override
    public int requiredEnderChestsForTarget(int targetBuildBlocks) {
        return PlaneInventoryQueries.requiredEnderChestsForTarget(countBuildBlock(), targetBuildBlocks);
    }

    @Override
    public int countLooseEnderChests() {
        return view.countLooseEnderChests();
    }

    @Override
    public boolean hasInventorySpaceForEnderChest() {
        return view.hasInventorySpaceForEnderChest();
    }

    @Override
    public boolean hasInventorySpaceForEnderChestPreservingShulkerSlot() {
        return view.hasInventorySpaceForEnderChestPreservingShulkerSlot();
    }

    @Override
    public FindItemResult findHotbarBuildBlock() {
        return InvUtils.findInHotbar(this::isBuildBlockStack);
    }

    @Override
    public void ensureBuildBlockInHotbar() {
        mover.ensureBuildBlockInHotbar();
    }

    @Override
    public FindItemResult prepareUsableBuildBlock() {
        return preparation.prepareBuildBlock().result();
    }

    BuildBlockPreparation prepareBuildBlock() {
        return preparation.prepareBuildBlock();
    }

    @Override
    public FindItemResult findEnderChest() {
        return InvUtils.findInHotbar(Items.ENDER_CHEST);
    }

    @Override
    public FindItemResult prepareUsableEnderChest() {
        return preparation.prepareUsableEnderChest();
    }

    @Override
    public FindItemResult findHotbarBow() {
        FindItemResult result = InvUtils.findInHotbar(view::isUsableBowStack);

        return result.isHotbar() ? result : null;
    }

    @Override
    public FindItemResult prepareUsableBow() {
        return preparation.prepareUsableBow();
    }

    @Override
    public FindItemResult findHotbarSword() {
        FindItemResult result = InvUtils.findInHotbar(view::isUsableSwordStack);

        return result.isHotbar() ? result : null;
    }

    @Override
    public FindItemResult prepareUsableSword() {
        return preparation.prepareUsableSword();
    }

    @Override
    public boolean hasArrows() {
        return InvUtils.find(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW).found();
    }

    @Override
    public FindItemResult prepareUsableEnderChestShulker() {
        return preparation.prepareUsableEnderChestShulker();
    }

    @Override
    public FindItemResult prepareUsablePickaxe() {
        return preparation.prepareUsablePickaxe();
    }

    @Override
    public FindItemResult findEnderChestShulkerInHotbar() {
        int[] enderChestCountsBySlot = new int[9];
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = context.player().getInventory().getStack(slot);
            enderChestCountsBySlot[slot] = view.countEnderChestsInShulker(stack);
        }

        int bestSlot = PlaneInventoryQueries.bestEnderChestShulkerHotbarSlot(enderChestCountsBySlot);
        return bestSlot < 0 ? new FindItemResult(-1, 0) : new FindItemResult(bestSlot, context.player().getInventory().getStack(bestSlot).getCount());
    }

    @Override
    public boolean hasEnderChestShulkerInMainInventory() {
        return view.hasEnderChestShulkerInMainInventory();
    }

    int countEnderChestShulkers() {
        return view.countEnderChestShulkers();
    }

    int countEnderChestsInShulkers() {
        return view.countEnderChestsInShulkers();
    }

    int countEnderChestSupply() {
        return view.countEnderChestSupply();
    }

    @Override
    public int findMainInventoryBuildBlockSlot() {
        return view.findMainInventoryBuildBlockSlot();
    }

    int findMainInventoryEnderChestSlot() {
        return view.findMainInventoryEnderChestSlot();
    }

    boolean hasTrashItems() {
        return view.findTrashSlot() >= 0;
    }

    boolean hasInventorySpaceForCleanupDrop(ItemStack dropStack) {
        return view.hasInventorySpaceForCleanupDrop(dropStack);
    }

    boolean dropNextTrashStack() {
        if (!prepareNextTrashStackForDrop()) return false;

        return dropSelectedTrashStack();
    }

    boolean prepareNextTrashStackForDrop() {
        if (!context.interactionReady()) return false;

        int trashSlot = view.findTrashSlot();
        if (trashSlot < 0) return false;

        int hotbarSlot = trashSlot <= 8 ? trashSlot : moveTrashStackToHotbar(trashSlot);
        if (hotbarSlot < 0) return false;
        if (!PlaneItemClassifier.isTrashStack(context.player().getInventory().getStack(hotbarSlot))) return false;

        context.player().getInventory().setSelectedSlot(hotbarSlot);
        return true;
    }

    boolean dropSelectedTrashStack() {
        if (!context.interactionReady()) return false;

        int selectedSlot = context.player().getInventory().getSelectedSlot();
        if (!PlaneItemClassifier.isTrashStack(context.player().getInventory().getStack(selectedSlot))) return false;

        return context.player().dropSelectedItem(true);
    }

    @Override
    public int findMainInventoryEnderChestShulkerSlot() {
        return view.findMainInventoryEnderChestShulkerSlot();
    }

    @Override
    public int findMainInventoryPickaxeSlot() {
        return view.findMainInventoryPickaxeSlot();
    }

    @Override
    public int findMainInventoryBowSlot() {
        return view.findMainInventoryBowSlot();
    }

    @Override
    public int findMainInventorySwordSlot() {
        return view.findMainInventorySwordSlot();
    }

    @Override
    public boolean hasAnyEnderChestShulker() {
        return findEnderChestShulkerInHotbar().isHotbar() || hasEnderChestShulkerInMainInventory();
    }

    @Override
    public EnderChestShulkerSourceScan scanEnderChestShulkerSources() {
        return view.scanEnderChestShulkerSources();
    }

    @Override
    public int findOpenShulkerEnderChestSlot(ScreenHandler handler) {
        return view.findOpenShulkerEnderChestSlot(handler);
    }

    @Override
    public boolean findResultMatchesBuildBlock(FindItemResult result) {
        return findResultMatchesBlock(result, config.buildBlock());
    }

    @Override
    public boolean findResultMatchesEnderChestShulker(FindItemResult result) {
        return result != null
            && result.isHotbar()
            && isShulkerWithEnderChests(context.player().getInventory().getStack(result.slot()));
    }

    @Override
    public boolean findResultMatchesBlock(FindItemResult result, Block block) {
        if (result == null || !result.found()) return false;
        if (result.isHotbar()) {
            return isBlockStack(context.player().getInventory().getStack(result.slot()), block);
        }
        if (result.isOffhand()) {
            return isBlockStack(context.player().getOffHandStack(), block);
        }

        return false;
    }

    @Override
    public boolean isBuildBlockStack(ItemStack stack) {
        return view.isBuildBlockStack(stack);
    }

    @Override
    public boolean isBlockStack(ItemStack stack, Block block) {
        return view.isBlockStack(stack, block);
    }

    @Override
    public boolean isShulkerWithEnderChests(ItemStack stack) {
        return view.isShulkerWithEnderChests(stack);
    }

    @Override
    public boolean isShulkerBoxStack(ItemStack stack) {
        return view.isShulkerBoxStack(stack);
    }

    @Override
    public boolean isEnderChestSupplyStack(ItemStack stack) {
        return PlaneItemClassifier.isEnderChestSupplyStack(stack);
    }

    @Override
    public FindItemResult findHotbarPickaxe() {
        FindItemResult result = InvUtils.findInHotbar(view::isUsablePickaxeStack);

        return result.isHotbar() ? result : null;
    }

    private int moveTrashStackToHotbar(int sourceSlot) {
        int selected = context.player().getInventory().getSelectedSlot();
        boolean[] emptyHotbarSlots = new boolean[9];
        for (int slot = 0; slot <= 8; slot++) {
            emptyHotbarSlots[slot] = context.player().getInventory().getStack(slot).isEmpty();
        }

        int hotbarSlot = PlaneInventoryMover.hotbarSwapTarget(selected, emptyHotbarSlots);
        if (hotbarSlot < 0 || context.client().interactionManager == null) return -1;

        context.client().interactionManager.clickSlot(
            context.player().playerScreenHandler.syncId,
            playerInventoryScreenSlot(sourceSlot),
            hotbarSlot,
            SlotActionType.SWAP,
            context.player()
        );
        return hotbarSlot;
    }

    private int playerInventoryScreenSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

}
