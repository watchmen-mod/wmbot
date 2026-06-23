package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotSupplyProbes {
    private PlaneKitbotSupplyProbes() {
    }

    static PlaneKitbotSupplyProbe from(EnderChestSupplyInventory supply) {
        return new PlaneKitbotSupplyProbe() {
            @Override
            public boolean hasLooseEnderChests() {
                return supply != null && supply.hasLooseEnderChests();
            }

            @Override
            public int looseEnderChestCount() {
                return supply == null ? 0 : supply.looseEnderChests();
            }

            @Override
            public boolean hasEnderChestShulker() {
                return supply != null && supply.hasEnderChestShulker();
            }

            @Override
            public int enderChestsInShulkersCount() {
                return supply == null ? 0 : supply.enderChestsInShulkers();
            }

            @Override
            public int enderChestSupplyCount() {
                return supply == null ? 0 : supply.enderChestSupplyCount();
            }
        };
    }
}
