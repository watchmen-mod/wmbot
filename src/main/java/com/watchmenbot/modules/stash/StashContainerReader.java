package com.watchmenbot.modules.stash;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class StashContainerReader {
    int openedStorageSize(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic) return generic.getRows() * 9;
        if (handler instanceof ShulkerBoxScreenHandler) return StashTargetDiscovery.SINGLE_STORAGE_SIZE;
        return 0;
    }

    boolean screenMatchesTarget(ScreenHandler handler, StashTarget target) {
        int openedSize = openedStorageSize(handler);
        if (openedSize != target.expectedSize()) return false;

        if (target.type().contains("shulker_box")) return handler instanceof ShulkerBoxScreenHandler;
        return handler instanceof GenericContainerScreenHandler;
    }

    StashCachedContainer read(StashTarget target, ScreenHandler handler, int size) {
        List<StashCachedItem> items = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            StashShulkerContentClassifier.ShulkerContent shulkerContent = StashShulkerContentClassifier.classify(stack);
            items.add(new StashCachedItem(
                slot,
                Registries.ITEM.getId(stack.getItem()).toString(),
                displayName(stack),
                stack.getCount(),
                stack.getComponentChanges().toString(),
                isShulkerBoxItem(stack),
                shulkerContent.pureEchest(),
                shulkerContent.enderChestCount()
            ));
        }

        return new StashCachedContainer(
            target.id(),
            target.type(),
            size,
            StashInventoryCache.positions(target.positions()),
            StashInventoryCache.position(target.interactionPos()),
            Instant.now().toString(),
            items
        );
    }

    private boolean isShulkerBoxItem(ItemStack stack) {
        return Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    private String displayName(ItemStack stack) {
        Text customName = stack.getCustomName();
        if (customName != null) return customName.getString();

        return stack.getName().getString();
    }
}
