package com.watchmenbot.modules.stash;

import java.util.List;
import java.util.Map;

final class StashKitbotTestFixtures {
    private StashKitbotTestFixtures() {
    }

    static CacheFile cacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "Blue EChest", 3),
                    shulker(1, "Red EChest", 4)
                )),
                container("b", new PositionRecord(20, 64, 20), List.of(
                    shulker(2, "Blue EChest", 5),
                    item(3, "minecraft:stone", "Stone", 64)
                )),
                container("c", new PositionRecord(30, 64, 30), List.of(
                    shulker(4, "Green Kit", 9)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    static CacheFile echestContentCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("echest-a", new PositionRecord(10, 64, 10), List.of(
                    pureEchestShulker(0, "Purple Shulker Box", 2, 54),
                    shulker(1, "Blue EChest", 4)
                )),
                container("echest-b", new PositionRecord(20, 64, 20), List.of(
                    pureEchestShulker(2, "Tools", 3, 81),
                    item(3, "minecraft:stone", "Stone", 64)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    static CacheFile fancyCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("fancy-a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░", 4)
                )),
                container("fancy-b", new PositionRecord(20, 64, 20), List.of(
                    shulker(2, "The Watchmen Kit", 3)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    static CacheFile fancyPossessiveCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(container("fancy-a", new PositionRecord(10, 64, 10), List.of(
                shulker(0, "░▒▓█🆃🅷🅴 🆆🅰🆃🅲🅷🅼🅴🅽'🆂 🅺🅸🆃█▓▒░", 4)
            ))),
            Map.of(),
            List.of()
        );
    }

    static CacheFile phraseCacheFile() {
        PositionRecord pos = new PositionRecord(10, 64, 10);
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(container("phrase-a", pos, List.of(
                shulker(0, "Watchmen PvP Kit", 9),
                shulker(1, "The Watchmen Kit", 3)
            ))),
            Map.of(),
            List.of()
        );
    }

    static CacheFile watchmenVariantCacheFile() {
        return new CacheFile(
            1,
            "now",
            "server",
            "minecraft:overworld",
            null,
            List.of(
                container("watchmen-a", new PositionRecord(10, 64, 10), List.of(
                    shulker(0, "The Watchmen", 1),
                    shulker(1, "The Watchmen's Kit", 4)
                )),
                container("watchmen-b", new PositionRecord(20, 64, 20), List.of(
                    shulker(0, "The Watchmen's EChest's", 2),
                    shulker(1, "The Watchmen's Kit", 6)
                ))
            ),
            Map.of(),
            List.of()
        );
    }

    static StashCachedContainer container(String id, PositionRecord pos, List<StashCachedItem> items) {
        return new StashCachedContainer(id, "minecraft:chest", 27, List.of(pos), pos, "now", items);
    }

    static StashCachedItem shulker(int slot, String displayName, int count) {
        return new StashCachedItem(slot, "minecraft:shulker_box", displayName, count, "{}", true, false, 0);
    }

    static StashCachedItem pureEchestShulker(int slot, String displayName, int count, int containedEnderChests) {
        return new StashCachedItem(slot, "minecraft:shulker_box", displayName, count, "{}", true, true, containedEnderChests);
    }

    static StashCachedItem item(int slot, String itemId, String displayName, int count) {
        return new StashCachedItem(slot, itemId, displayName, count, "{}", false, false, 0);
    }

    static StashTarget target(String id) {
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(1, 64, 1);
        return target(id, pos);
    }

    static StashTarget target(String id, net.minecraft.util.math.BlockPos pos) {
        return new StashTarget(id, "minecraft:chest", List.of(pos), pos, 27);
    }

    static KitSource source(String id, int x, int y, int z) {
        return new KitSource(id, new net.minecraft.util.math.BlockPos(x, y, z), List.of(0), 1);
    }

    static KitRequest kitRequestWithSources(KitSource... sources) {
        return new KitRequest(
            "Alice",
            "Blue EChest",
            "blue echest",
            1,
            List.of(sources),
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );
    }

    static PositionRecord toPosition(net.minecraft.util.math.BlockPos pos) {
        return new PositionRecord(pos.getX(), pos.getY(), pos.getZ());
    }
}
