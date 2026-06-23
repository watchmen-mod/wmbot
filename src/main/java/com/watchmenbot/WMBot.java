package com.watchmenbot;

import com.mojang.logging.LogUtils;
import com.watchmenbot.hud.PlaneBuilderStatsHud;
import com.watchmenbot.hud.StashScannerStatsHud;
import com.watchmenbot.hud.StashKitbotStatsHud;
import com.watchmenbot.modules.inventory.AutoEatStocker;
import com.watchmenbot.modules.inventory.InventoryTools;
import com.watchmenbot.modules.planebuilder.PlaneBuilder;
import com.watchmenbot.modules.sixb6t.SixB6TLogin;
import com.watchmenbot.modules.sixb6t.SixB6TPortals;
import com.watchmenbot.modules.stash.StashScanner;
import com.watchmenbot.modules.stash.StashKitbot;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import org.slf4j.Logger;

public class WMBot extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("WMBot");

    @Override
    public void onInitialize() {
        LOG.info("Initializing WMBot");

        WMBotRegistry.registerModules(
            new SixB6TLogin(),
            new SixB6TPortals(),
            new StashScanner(),
            new StashKitbot(),
            new AutoEatStocker(),
            new InventoryTools(),
            new PlaneBuilder()
        );

        WMBotRegistry.registerHud(
            StashScannerStatsHud.INFO,
            StashKitbotStatsHud.INFO,
            PlaneBuilderStatsHud.INFO
        );
    }

    @Override
    public void onRegisterCategories() {
        WMBotRegistry.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.watchmenbot";
    }
}
