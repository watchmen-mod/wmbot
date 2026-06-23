package com.watchmenbot.modules.inventory;

import java.util.List;
import java.util.Objects;

public final class InventorySortPlannerPureTest {
    private InventorySortPlannerPureTest() {
    }

    public static void run() {
        sortsAlphabetically();
        movesEmptySlotsToEnd();
        combinesCompatiblePartialStacks();
        keepsDifferentComponentsSeparate();
        plansOnlyRequestedMainInventorySlots();
    }

    private static void sortsAlphabetically() {
        List<InventorySortPlanner.PlannedStack> planned = InventorySortPlanner.plan(List.of(
            stack("Stone", "minecraft:stone", "", 64, 64),
            stack("Apple", "minecraft:apple", "", 2, 64),
            stack("Bread", "minecraft:bread", "", 4, 64)
        ), 3);

        assertEquals("minecraft:apple", planned.get(0).itemId(), "apple sorts first");
        assertEquals("minecraft:bread", planned.get(1).itemId(), "bread sorts second");
        assertEquals("minecraft:stone", planned.get(2).itemId(), "stone sorts third");
    }

    private static void movesEmptySlotsToEnd() {
        List<InventorySortPlanner.PlannedStack> planned = InventorySortPlanner.plan(List.of(
            empty(),
            stack("Bread", "minecraft:bread", "", 4, 64),
            empty(),
            stack("Apple", "minecraft:apple", "", 2, 64)
        ), 4);

        assertEquals("minecraft:apple", planned.get(0).itemId(), "non-empty item sorts to front");
        assertEquals("minecraft:bread", planned.get(1).itemId(), "second non-empty item follows");
        assertTrue(planned.get(2).isEmpty(), "first empty is at the end");
        assertTrue(planned.get(3).isEmpty(), "second empty is at the end");
    }

    private static void combinesCompatiblePartialStacks() {
        List<InventorySortPlanner.PlannedStack> planned = InventorySortPlanner.plan(List.of(
            stack("Obsidian", "minecraft:obsidian", "", 40, 64),
            stack("Obsidian", "minecraft:obsidian", "", 30, 64),
            stack("Obsidian", "minecraft:obsidian", "", 5, 64)
        ), 3);

        assertEquals(64, planned.get(0).count(), "first stack is filled");
        assertEquals(11, planned.get(1).count(), "remaining compatible items are compacted");
        assertTrue(planned.get(2).isEmpty(), "compaction frees the last slot");
    }

    private static void keepsDifferentComponentsSeparate() {
        List<InventorySortPlanner.PlannedStack> planned = InventorySortPlanner.plan(List.of(
            stack("Shulker Box", "minecraft:purple_shulker_box", "kit=a", 1, 1),
            stack("Shulker Box", "minecraft:purple_shulker_box", "kit=b", 1, 1)
        ), 2);

        assertEquals("kit=a", planned.get(0).componentKey(), "first component group remains distinct");
        assertEquals("kit=b", planned.get(1).componentKey(), "second component group remains distinct");
    }

    private static void plansOnlyRequestedMainInventorySlots() {
        List<InventorySortPlanner.StackInfo> inventoryOnly = List.of(
            stack("Stone", "minecraft:stone", "", 1, 64),
            stack("Apple", "minecraft:apple", "", 1, 64),
            stack("Bread", "minecraft:bread", "", 1, 64)
        );

        List<InventorySortPlanner.PlannedStack> planned = InventorySortPlanner.plan(inventoryOnly, 3);

        assertEquals(3, planned.size(), "planner only returns the requested slot count");
        assertEquals("minecraft:apple", planned.get(0).itemId(), "main inventory range is sorted");
    }

    private static InventorySortPlanner.StackInfo stack(String displayName, String itemId, String componentKey, int count, int maxCount) {
        return new InventorySortPlanner.StackInfo(displayName, itemId, componentKey, count, maxCount);
    }

    private static InventorySortPlanner.StackInfo empty() {
        return new InventorySortPlanner.StackInfo("", "", "", 0, 0);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("%s: expected <%s> but got <%s>".formatted(message, expected, actual));
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
