package com.watchmenbot.modules.inventory;

final class AutoEatStockerPlanner {
    private AutoEatStockerPlanner() {
    }

    static StockAction plan(InventoryState state, boolean preferEmptySlot, int fallbackHotbarSlot) {
        if (state.enchantedGoldenAppleInHotbar()) return StockAction.none();
        if (state.enchantedGoldenAppleMainInventorySlot() >= 0) {
            return action(
                FoodKind.ENCHANTED_GOLDEN_APPLE,
                state.enchantedGoldenAppleMainInventorySlot(),
                state.emptyHotbarSlots(),
                preferEmptySlot,
                fallbackHotbarSlot
            );
        }

        if (state.goldenAppleInHotbar()) return StockAction.none();
        if (state.goldenAppleMainInventorySlot() >= 0) {
            return action(
                FoodKind.GOLDEN_APPLE,
                state.goldenAppleMainInventorySlot(),
                state.emptyHotbarSlots(),
                preferEmptySlot,
                fallbackHotbarSlot
            );
        }

        return StockAction.none();
    }

    private static StockAction action(
        FoodKind food,
        int sourceSlot,
        boolean[] emptyHotbarSlots,
        boolean preferEmptySlot,
        int fallbackHotbarSlot
    ) {
        int targetSlot = targetHotbarSlot(emptyHotbarSlots, preferEmptySlot, fallbackHotbarSlot);
        if (sourceSlot < 9 || targetSlot < 0) return StockAction.none();

        return new StockAction(food, sourceSlot, targetSlot);
    }

    static int targetHotbarSlot(boolean[] emptyHotbarSlots, boolean preferEmptySlot, int fallbackHotbarSlot) {
        if (preferEmptySlot) {
            int hotbarSlots = Math.min(9, emptyHotbarSlots.length);
            for (int slot = 0; slot < hotbarSlots; slot++) {
                if (emptyHotbarSlots[slot]) return slot;
            }
        }

        return fallbackHotbarSlot >= 0 && fallbackHotbarSlot <= 8 ? fallbackHotbarSlot : -1;
    }

    enum FoodKind {
        ENCHANTED_GOLDEN_APPLE("enchanted golden apples"),
        GOLDEN_APPLE("golden apples");

        private final String label;

        FoodKind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record InventoryState(
        boolean enchantedGoldenAppleInHotbar,
        int enchantedGoldenAppleMainInventorySlot,
        boolean goldenAppleInHotbar,
        int goldenAppleMainInventorySlot,
        boolean[] emptyHotbarSlots
    ) {
    }

    record StockAction(FoodKind food, int sourceSlot, int targetHotbarSlot) {
        static StockAction none() {
            return new StockAction(null, -1, -1);
        }

        boolean found() {
            return food != null && sourceSlot >= 9 && targetHotbarSlot >= 0 && targetHotbarSlot <= 8;
        }
    }
}
