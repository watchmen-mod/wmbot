package com.watchmenbot.modules.inventory;

import com.watchmenbot.WMBot;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class InventoryTools extends Module {
    public static final int SHULKER_BOX_SLOTS = 27;
    public static final int PLAYER_MAIN_INVENTORY_SLOTS = 27;
    public static final int PLAYER_HOTBAR_SLOTS = 9;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includeHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("include-hotbar")
        .description("Includes hotbar slots when using Put All.")
        .defaultValue(false)
        .build()
    );

    public InventoryTools() {
        super(WMBot.CATEGORY, "inventory-tools", "Adds take-all and put-all buttons to supported container screens.");
    }

    public static boolean isEnabled() {
        InventoryTools module = Modules.get().get(InventoryTools.class);
        return module != null && module.isActive();
    }

    public static int containerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic) return generic.getRows() * 9;
        if (handler instanceof ShulkerBoxScreenHandler) return SHULKER_BOX_SLOTS;
        return -1;
    }

    public static boolean supports(ScreenHandler handler) {
        return containerSlotCount(handler) > 0;
    }

    public static void takeAll(MinecraftClient mc, ScreenHandler handler) {
        int containerSlots = containerSlotCount(handler);
        if (!canTransfer(mc, handler, containerSlots)) return;

        for (int slot = 0; slot < containerSlots; slot++) {
            quickMoveIfOccupied(mc, handler, slot);
        }
    }

    public static void putAll(MinecraftClient mc, ScreenHandler handler) {
        int containerSlots = containerSlotCount(handler);
        if (!canTransfer(mc, handler, containerSlots)) return;

        int playerSlots = PLAYER_MAIN_INVENTORY_SLOTS + (includeHotbar() ? PLAYER_HOTBAR_SLOTS : 0);
        int endSlot = Math.min(handler.slots.size(), containerSlots + playerSlots);
        for (int slot = containerSlots; slot < endSlot; slot++) {
            quickMoveIfOccupied(mc, handler, slot);
        }
    }

    public static void sortChest(MinecraftClient mc, ScreenHandler handler) {
        int containerSlots = containerSlotCount(handler);
        if (!canTransfer(mc, handler, containerSlots)) return;

        sortSlots(mc, handler, 0, containerSlots);
    }

    public static void sortInventory(MinecraftClient mc, ScreenHandler handler) {
        int containerSlots = containerSlotCount(handler);
        if (!canTransfer(mc, handler, containerSlots)) return;

        int endSlot = Math.min(handler.slots.size(), containerSlots + PLAYER_MAIN_INVENTORY_SLOTS);
        sortSlots(mc, handler, containerSlots, endSlot);
    }

    private static boolean includeHotbar() {
        InventoryTools module = Modules.get().get(InventoryTools.class);
        return module != null && module.includeHotbar.get();
    }

    private static boolean canTransfer(MinecraftClient mc, ScreenHandler handler, int containerSlots) {
        return mc != null
            && mc.player != null
            && mc.interactionManager != null
            && handler != null
            && containerSlots > 0;
    }

    private static void quickMoveIfOccupied(MinecraftClient mc, ScreenHandler handler, int slot) {
        if (slot < 0 || slot >= handler.slots.size()) return;

        ItemStack stack = handler.getSlot(slot).getStack();
        if (stack.isEmpty()) return;

        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private static void sortSlots(MinecraftClient mc, ScreenHandler handler, int startSlot, int endSlot) {
        if (!canSort(mc, handler, startSlot, endSlot)) return;

        compactCompatibleStacks(mc, handler, startSlot, endSlot);
        if (!restoreCursor(mc, handler, startSlot, endSlot)) return;

        List<InventorySortPlanner.PlannedStack> desired = InventorySortPlanner.plan(readStackInfos(handler, startSlot, endSlot), endSlot - startSlot);
        for (int offset = 0; offset < desired.size(); offset++) {
            int targetSlot = startSlot + offset;
            InventorySortPlanner.PlannedStack planned = desired.get(offset);
            if (slotMatches(handler, targetSlot, planned)) continue;
            if (planned.isEmpty()) continue;

            int sourceSlot = findMatchingSlot(handler, planned, targetSlot + 1, endSlot);
            if (sourceSlot < 0) continue;
            if (!moveSlotInto(mc, handler, sourceSlot, targetSlot)) return;
        }

        restoreCursor(mc, handler, startSlot, endSlot);
    }

    private static boolean canSort(MinecraftClient mc, ScreenHandler handler, int startSlot, int endSlot) {
        return mc != null
            && mc.player != null
            && mc.interactionManager != null
            && handler != null
            && startSlot >= 0
            && endSlot > startSlot
            && endSlot <= handler.slots.size()
            && handler.getCursorStack().isEmpty();
    }

    private static void compactCompatibleStacks(MinecraftClient mc, ScreenHandler handler, int startSlot, int endSlot) {
        for (int targetSlot = startSlot; targetSlot < endSlot; targetSlot++) {
            ItemStack target = handler.getSlot(targetSlot).getStack();
            if (target.isEmpty() || target.getCount() >= target.getMaxCount()) continue;

            for (int sourceSlot = targetSlot + 1; sourceSlot < endSlot; sourceSlot++) {
                ItemStack source = handler.getSlot(sourceSlot).getStack();
                target = handler.getSlot(targetSlot).getStack();
                if (target.isEmpty() || target.getCount() >= target.getMaxCount()) break;
                if (source.isEmpty() || !canCombine(target, source)) continue;

                click(mc, handler, sourceSlot);
                click(mc, handler, targetSlot);
                if (!handler.getCursorStack().isEmpty()) click(mc, handler, sourceSlot);
                if (!restoreCursor(mc, handler, startSlot, endSlot)) return;
            }
        }
    }

    private static boolean moveSlotInto(MinecraftClient mc, ScreenHandler handler, int sourceSlot, int targetSlot) {
        if (sourceSlot == targetSlot) return true;
        if (!handler.getCursorStack().isEmpty()) return false;

        click(mc, handler, sourceSlot);
        if (handler.getCursorStack().isEmpty()) return false;

        click(mc, handler, targetSlot);
        if (!handler.getCursorStack().isEmpty()) click(mc, handler, sourceSlot);

        return handler.getCursorStack().isEmpty();
    }

    private static boolean restoreCursor(MinecraftClient mc, ScreenHandler handler, int startSlot, int endSlot) {
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty()) return true;

        for (int slot = startSlot; slot < endSlot; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isEmpty() && canCombine(stack, cursor) && stack.getCount() < stack.getMaxCount()) {
                click(mc, handler, slot);
                if (handler.getCursorStack().isEmpty()) return true;
            }
        }

        for (int slot = startSlot; slot < endSlot; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) {
                click(mc, handler, slot);
                return handler.getCursorStack().isEmpty();
            }
        }

        return handler.getCursorStack().isEmpty();
    }

    private static List<InventorySortPlanner.StackInfo> readStackInfos(ScreenHandler handler, int startSlot, int endSlot) {
        List<InventorySortPlanner.StackInfo> stacks = new ArrayList<>(endSlot - startSlot);
        for (int slot = startSlot; slot < endSlot; slot++) {
            stacks.add(stackInfo(handler.getSlot(slot).getStack()));
        }

        return stacks;
    }

    private static int findMatchingSlot(ScreenHandler handler, InventorySortPlanner.PlannedStack planned, int startSlot, int endSlot) {
        for (int slot = startSlot; slot < endSlot; slot++) {
            if (slotMatches(handler, slot, planned)) return slot;
        }

        return -1;
    }

    private static boolean slotMatches(ScreenHandler handler, int slot, InventorySortPlanner.PlannedStack planned) {
        ItemStack stack = handler.getSlot(slot).getStack();
        if (planned.isEmpty()) return stack.isEmpty();
        if (stack.isEmpty() || stack.getCount() != planned.count()) return false;

        return stackInfo(stack).sameIdentity(planned);
    }

    private static InventorySortPlanner.StackInfo stackInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new InventorySortPlanner.StackInfo("", "", "", 0, 0);
        }

        return new InventorySortPlanner.StackInfo(
            stack.getName().getString(),
            Registries.ITEM.getId(stack.getItem()).toString(),
            stack.getComponentChanges().toString(),
            stack.getCount(),
            stack.getMaxCount()
        );
    }

    private static boolean canCombine(ItemStack a, ItemStack b) {
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }

    private static void click(MinecraftClient mc, ScreenHandler handler, int slot) {
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }
}
