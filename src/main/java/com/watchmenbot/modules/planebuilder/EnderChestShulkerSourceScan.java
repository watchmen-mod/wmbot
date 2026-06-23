package com.watchmenbot.modules.planebuilder;

record EnderChestShulkerSourceScan(
    int hotbarSlot,
    int hotbarStackCount,
    int mainInventorySlot,
    boolean offhand,
    boolean cursor,
    int shulkerStacks,
    int containedEnderChests
) {
    static final EnderChestShulkerSourceScan EMPTY = new EnderChestShulkerSourceScan(-1, 0, -1, false, false, 0, 0);

    boolean hasHotbarSource() {
        return hotbarSlot >= 0;
    }

    boolean hasMainInventorySource() {
        return mainInventorySlot >= 0;
    }

    boolean hasVisibleSource() {
        return shulkerStacks > 0 || offhand || cursor || hasHotbarSource() || hasMainInventorySource();
    }
}
