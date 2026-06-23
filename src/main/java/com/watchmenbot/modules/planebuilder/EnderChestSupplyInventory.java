package com.watchmenbot.modules.planebuilder;

final class EnderChestSupplyInventory {
    private final PlaneInventory inventory;

    EnderChestSupplyInventory(PlaneInventory inventory) {
        this.inventory = inventory;
    }

    int looseEnderChests() {
        return inventory.countLooseEnderChests();
    }

    boolean hasLooseEnderChests() {
        return looseEnderChests() > 0;
    }

    boolean hasEnderChestShulker() {
        return inventory.hasAnyEnderChestShulker();
    }

    int enderChestShulkerCount() {
        return inventory.countEnderChestShulkers();
    }

    int enderChestsInShulkers() {
        return inventory.countEnderChestsInShulkers();
    }

    int enderChestSupplyCount() {
        return inventory.countEnderChestSupply();
    }
}
