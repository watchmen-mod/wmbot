package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;

final class PlaneKitbotRefillSettings {
    private PlaneKitbotRefillSettings() {
    }

    static PlaneBuilderSettings.KitbotRefill create(SettingGroup group) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name("kitbot-refill")
            .description("Whispers a configured kitbot for ender-chest supplies when Plane Builder runs out.")
            .defaultValue(false)
            .build()
        );

        Setting<String> nickname = group.add(new StringSetting.Builder()
            .name("kitbot-nickname")
            .description("Nickname to whisper for ender-chest supply deliveries.")
            .defaultValue("whoahbuddy")
            .placeholder("whoahbuddy")
            .build()
        );

        Setting<String> kitName = group.add(new StringSetting.Builder()
            .name("kitbot-kit-name")
            .description("Kit name sent to the kitbot.")
            .defaultValue("the watchmen's echest's")
            .placeholder("the watchmen's echest's")
            .build()
        );

        Setting<Integer> kitCount = group.add(new IntSetting.Builder()
            .name("kitbot-kit-count")
            .description("Number of kits to request from the kitbot.")
            .defaultValue(1)
            .range(1, 36)
            .sliderRange(1, 36)
            .build()
        );

        Setting<String> whisperCommand = group.add(new StringSetting.Builder()
            .name("kitbot-whisper-command")
            .description("Whisper command used to request a kitbot refill.")
            .defaultValue("/w")
            .placeholder("/w")
            .build()
        );

        Setting<String> teleportAcceptCommand = group.add(new StringSetting.Builder()
            .name("kitbot-teleport-accept-command")
            .description("Command used to accept the kitbot teleport request.")
            .defaultValue("/tpy")
            .placeholder("/tpy")
            .build()
        );

        return new PlaneBuilderSettings.KitbotRefill(enabled, nickname, kitName, kitCount, whisperCommand, teleportAcceptCommand);
    }
}
