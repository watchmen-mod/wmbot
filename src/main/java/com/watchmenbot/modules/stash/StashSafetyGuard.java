package com.watchmenbot.modules.stash;

import com.watchmenbot.util.BaritoneCompatibility;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

final class StashSafetyGuard {
    private final BaritoneSafety safety = BaritoneCompatibility.available() ? new BaritoneStashSafetyGuard() : null;

    void apply() {
        if (safety != null) safety.apply();
    }

    void restore() {
        if (safety != null) safety.restore();
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

    interface BaritoneSafety {
        void apply();

        void restore();
    }
}
