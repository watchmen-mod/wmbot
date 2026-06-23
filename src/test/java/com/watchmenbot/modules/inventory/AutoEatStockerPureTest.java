package com.watchmenbot.modules.inventory;

import java.util.Objects;

public final class AutoEatStockerPureTest {
    private AutoEatStockerPureTest() {
    }

    public static void main(String[] args) {
        run();
    }

    static void run() {
        InventorySortPlannerPureTest.run();
        promotesEnchantedGoldenAppleFirst();
        promotesGoldenAppleWhenEnchantedIsUnavailable();
        skipsWhenEnchantedGoldenAppleAlreadyInHotbar();
        prefersEmptyHotbarSlot();
        usesFallbackSlotWhenHotbarIsFull();
        rejectsInvalidFallbackSlot();
        skipsWhenNoMainInventorySourceExists();
    }

    private static void promotesEnchantedGoldenAppleFirst() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, 14, false, 20, hotbarEmpties()),
            true,
            8
        );

        assertEquals(AutoEatStockerPlanner.FoodKind.ENCHANTED_GOLDEN_APPLE, action.food(), "enchanted golden apple wins priority");
        assertEquals(14, action.sourceSlot(), "enchanted source slot is selected");
        assertEquals(8, action.targetHotbarSlot(), "fallback slot is used when no hotbar slot is empty");
    }

    private static void promotesGoldenAppleWhenEnchantedIsUnavailable() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, -1, false, 22, hotbarEmpties(4)),
            true,
            8
        );

        assertEquals(AutoEatStockerPlanner.FoodKind.GOLDEN_APPLE, action.food(), "golden apple is selected without enchanted supply");
        assertEquals(22, action.sourceSlot(), "golden apple source slot is selected");
        assertEquals(4, action.targetHotbarSlot(), "empty hotbar slot is selected");
    }

    private static void skipsWhenEnchantedGoldenAppleAlreadyInHotbar() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(true, 14, false, 20, hotbarEmpties(2)),
            true,
            8
        );

        assertFalse(action.found(), "hotbar enchanted golden apple satisfies stocker");
    }

    private static void prefersEmptyHotbarSlot() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, 18, false, -1, hotbarEmpties(3, 7)),
            true,
            8
        );

        assertEquals(3, action.targetHotbarSlot(), "first empty hotbar slot wins");
    }

    private static void usesFallbackSlotWhenHotbarIsFull() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, 18, false, -1, hotbarEmpties()),
            true,
            6
        );

        assertEquals(6, action.targetHotbarSlot(), "configured fallback slot is used when full");
    }

    private static void rejectsInvalidFallbackSlot() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, 18, false, -1, hotbarEmpties()),
            true,
            9
        );

        assertFalse(action.found(), "invalid fallback slot prevents a forced replacement");
    }

    private static void skipsWhenNoMainInventorySourceExists() {
        AutoEatStockerPlanner.StockAction action = AutoEatStockerPlanner.plan(
            state(false, -1, false, -1, hotbarEmpties(0)),
            true,
            8
        );

        assertFalse(action.found(), "missing main inventory source produces no action");
    }

    private static AutoEatStockerPlanner.InventoryState state(
        boolean enchantedGoldenAppleInHotbar,
        int enchantedGoldenAppleMainInventorySlot,
        boolean goldenAppleInHotbar,
        int goldenAppleMainInventorySlot,
        boolean[] emptyHotbarSlots
    ) {
        return new AutoEatStockerPlanner.InventoryState(
            enchantedGoldenAppleInHotbar,
            enchantedGoldenAppleMainInventorySlot,
            goldenAppleInHotbar,
            goldenAppleMainInventorySlot,
            emptyHotbarSlots
        );
    }

    private static boolean[] hotbarEmpties(int... emptySlots) {
        boolean[] slots = new boolean[9];
        for (int slot : emptySlots) {
            slots[slot] = true;
        }
        return slots;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("%s: expected <%s> but got <%s>".formatted(message, expected, actual));
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
