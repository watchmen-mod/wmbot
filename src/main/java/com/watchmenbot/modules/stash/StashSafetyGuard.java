package com.watchmenbot.modules.stash;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

final class StashSafetyGuard {
    private BaritoneSafetySettings savedSettings;

    void apply() {
        Settings settings = BaritoneAPI.getSettings();
        if (savedSettings == null) {
            savedSettings = new BaritoneSafetySettings(
                settings.allowBreak.value,
                settings.allowPlace.value,
                settings.allowParkourPlace.value,
                settings.allowInventory.value,
                settings.autoTool.value,
                settings.rightClickContainerOnArrival.value,
                settings.allowDownward.value
            );
        }

        settings.allowBreak.value = false;
        settings.allowPlace.value = false;
        settings.allowParkourPlace.value = false;
        settings.allowInventory.value = false;
        settings.autoTool.value = false;
        settings.rightClickContainerOnArrival.value = false;
        settings.allowDownward.value = false;
    }

    void restore() {
        if (savedSettings == null) return;

        Settings settings = BaritoneAPI.getSettings();
        settings.allowBreak.value = savedSettings.allowBreak();
        settings.allowPlace.value = savedSettings.allowPlace();
        settings.allowParkourPlace.value = savedSettings.allowParkourPlace();
        settings.allowInventory.value = savedSettings.allowInventory();
        settings.autoTool.value = savedSettings.autoTool();
        settings.rightClickContainerOnArrival.value = savedSettings.rightClickContainerOnArrival();
        settings.allowDownward.value = savedSettings.allowDownward();
        savedSettings = null;
    }

    void cancelBreaking(MinecraftClient mc) {
        if (mc.options != null) mc.options.attackKey.setPressed(false);
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }

    boolean cancelDestroyPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerActionC2SPacket packet && isDestroyAction(packet.getAction())) {
            event.cancel();
            return true;
        }

        return false;
    }

    private boolean isDestroyAction(PlayerActionC2SPacket.Action action) {
        return action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
            || action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK;
    }

    private record BaritoneSafetySettings(
        boolean allowBreak,
        boolean allowPlace,
        boolean allowParkourPlace,
        boolean allowInventory,
        boolean autoTool,
        boolean rightClickContainerOnArrival,
        boolean allowDownward
    ) {
    }
}
