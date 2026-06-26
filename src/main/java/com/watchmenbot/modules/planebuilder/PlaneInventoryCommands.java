package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

interface PlaneInventoryCommands {
    void ensureBuildBlockInHotbar();

    FindItemResult prepareUsableBuildBlock();

    FindItemResult prepareUsableEnderChest();

    FindItemResult prepareUsableEnderChestShulker();

    FindItemResult prepareUsablePickaxe();

    FindItemResult prepareUsableSword();
}
