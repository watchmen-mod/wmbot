package com.watchmenbot.hud;

import com.watchmenbot.modules.stash.StashScanner;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

public class StashScannerStatsHud extends HudElement {
    public static final HudGroup GROUP = new HudGroup("WMBot");
    public static final HudElementInfo<StashScannerStatsHud> INFO = new HudElementInfo<>(
        GROUP,
        "stash-scanner-stats",
        "Displays stash scanner progress and shulker counts.",
        StashScannerStatsHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Renders text with shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(1.0)
        .range(0.5, 3.0)
        .sliderRange(0.5, 2.0)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Title color.")
        .defaultValue(new SettingColor(77, 180, 255))
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Text color.")
        .defaultValue(new SettingColor(230, 230, 230))
        .build()
    );

    private List<String> lines = List.of("Stash Scanner", "Scanner: inactive");

    public StashScannerStatsHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        lines = buildLines();

        double width = 0;
        for (String line : lines) {
            width = Math.max(width, renderer.textWidth(line, shadow.get(), scale.get()));
        }

        setSize(width, lines.size() * renderer.textHeight(shadow.get(), scale.get()));
    }

    @Override
    public void render(HudRenderer renderer) {
        double y = this.y;
        double lineHeight = renderer.textHeight(shadow.get(), scale.get());

        for (int i = 0; i < lines.size(); i++) {
            Color color = i == 0 ? titleColor.get() : textColor.get();
            renderer.text(lines.get(i), x, y, color, shadow.get(), scale.get());
            y += lineHeight;
        }
    }

    private List<String> buildLines() {
        StashScanner scanner = Modules.get().get(StashScanner.class);
        if (scanner == null) return List.of("Stash Scanner", "Scanner: missing");

        StashScanner.InventoryStats stats = scanner.statsSnapshot();
        List<String> next = new ArrayList<>();
        next.add("Stash Scanner");
        next.add("Scanner: " + (stats.active() ? stats.phase() : "inactive"));
        next.add("Searched: " + stats.containersSearched());
        next.add("Queued: " + (stats.containersQueued() + stats.containersCurrent()));
        next.add("Skipped: " + stats.containersSkipped());
        next.add("Stacks seen: " + stats.stacksSeen());
        next.add("Top shulkers:");

        if (stats.topShulkers().isEmpty()) {
            next.add("  none");
        }
        else {
            for (StashScanner.ShulkerNameCount shulker : stats.topShulkers()) {
                next.add("  " + shulker.name() + ": " + shulker.count());
            }
        }

        return next;
    }
}
