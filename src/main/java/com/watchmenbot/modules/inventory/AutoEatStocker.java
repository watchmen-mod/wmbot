package com.watchmenbot.modules.inventory;

import com.watchmenbot.WMBot;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoEatStocker extends Module {
    private static final int HOTBAR_SLOTS = 9;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> fallbackSlot = sgGeneral.add(new IntSetting.Builder()
        .name("fallback-slot")
        .description("Hotbar slot to replace when no empty hotbar slot is available.")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Boolean> preferEmptySlot = sgGeneral.add(new BoolSetting.Builder()
        .name("prefer-empty-slot")
        .description("Uses an empty hotbar slot before replacing the fallback slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait after moving a gapple stack into the hotbar.")
        .defaultValue(5)
        .range(0, 100)
        .sliderRange(0, 40)
        .build()
    );

    private int ticksUntilNextAction;

    public AutoEatStocker() {
        super(WMBot.CATEGORY, "auto-eat-stocker", "Keeps gapples stocked in the hotbar for Auto Eat.");
    }

    @Override
    public void onActivate() {
        ticksUntilNextAction = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticksUntilNextAction > 0) {
            ticksUntilNextAction--;
            return;
        }

        if (!readyForInventoryMove()) return;

        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            inventoryState(),
            preferEmptySlot.get(),
            fallbackSlot.get() - 1
        );
        if (!action.found()) return;

        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            playerInventoryScreenSlot(action.sourceSlot()),
            action.targetHotbarSlot(),
            SlotActionType.SWAP,
            mc.player
        );

        ticksUntilNextAction = delay.get();
        info("Moved %s into hotbar slot %d.", action.food().label(), action.targetHotbarSlot() + 1);
    }

    private boolean readyForInventoryMove() {
        return mc.player != null
            && mc.world != null
            && mc.interactionManager != null
            && mc.player.currentScreenHandler == mc.player.playerScreenHandler
            && mc.player.currentScreenHandler.getCursorStack().isEmpty()
            && !mc.player.isUsingItem()
            && !mc.options.useKey.isPressed();
    }

    private AutoEatStockerPlanner.InventoryState inventoryState() {
        boolean enchantedGoldenAppleInHotbar = false;
        boolean goldenAppleInHotbar = false;
        int enchantedGoldenAppleMainInventorySlot = -1;
        int goldenAppleMainInventorySlot = -1;
        boolean[] emptyHotbarSlots = new boolean[HOTBAR_SLOTS];

        int inventorySlots = Math.min(36, mc.player.getInventory().size());
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (slot < HOTBAR_SLOTS) {
                emptyHotbarSlots[slot] = stack.isEmpty();
                enchantedGoldenAppleInHotbar |= stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
                goldenAppleInHotbar |= stack.isOf(Items.GOLDEN_APPLE);
                continue;
            }

            if (enchantedGoldenAppleMainInventorySlot < 0 && isGapple(stack, Items.ENCHANTED_GOLDEN_APPLE)) {
                enchantedGoldenAppleMainInventorySlot = slot;
            }
            else if (goldenAppleMainInventorySlot < 0 && isGapple(stack, Items.GOLDEN_APPLE)) {
                goldenAppleMainInventorySlot = slot;
            }
        }

        return new AutoEatStockerPlanner.InventoryState(
            enchantedGoldenAppleInHotbar,
            enchantedGoldenAppleMainInventorySlot,
            goldenAppleInHotbar,
            goldenAppleMainInventorySlot,
            emptyHotbarSlots
        );
    }

    private boolean isGapple(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.isOf(item);
    }

    private int playerInventoryScreenSlot(int inventorySlot) {
        return inventorySlot < HOTBAR_SLOTS ? inventorySlot + 36 : inventorySlot;
    }
}
