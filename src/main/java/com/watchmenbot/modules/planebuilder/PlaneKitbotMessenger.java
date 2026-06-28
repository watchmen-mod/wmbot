package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class PlaneKitbotMessenger {
    private final Supplier<PlaneKitbotRefillConfig> config;
    private final MessageSender sender;
    private final BooleanSupplier clientReady;

    PlaneKitbotMessenger(PlaneBuilderSettings.KitbotRefill settings) {
        MinecraftClient mc = MinecraftClient.getInstance();
        this.config = () -> new PlaneKitbotRefillConfig(
            settings.enabled().get(),
            settings.nickname().get(),
            settings.kitName().get(),
            settings.kitCount().get()
        );
        this.sender = message -> ChatUtils.sendPlayerMsg(message, true);
        this.clientReady = () -> mc.player != null && mc.world != null;
    }

    PlaneKitbotMessenger(
        Supplier<PlaneKitbotRefillConfig> config,
        MessageSender sender,
        BooleanSupplier clientReady
    ) {
        this.config = config;
        this.sender = sender;
        this.clientReady = clientReady;
    }

    boolean enabled() {
        return config.get().enabled();
    }

    boolean sendRefillRequest() {
        if (!clientReady.getAsBoolean()) return false;

        String message = PlaneKitbotRefillDecisions.requestCommand(config.get());
        if (message == null) return false;

        sender.send(message);
        return true;
    }

    boolean sendTeleportAccept(String message) {
        if (!clientReady.getAsBoolean()) return false;

        String command = PlaneKitbotRefillDecisions.teleportAcceptCommand(config.get(), message);
        if (command == null) return false;

        sender.send(command);
        return true;
    }

    String teleportAcceptCommand(String message) {
        if (!clientReady.getAsBoolean()) return null;

        return PlaneKitbotRefillDecisions.teleportAcceptCommand(config.get(), message);
    }

    boolean teleportPromptMatches(String message) {
        return PlaneKitbotRefillDecisions.teleportPromptMatches(config.get(), message);
    }

    boolean teleportAcceptConfirmed(String message) {
        return PlaneKitbotRefillDecisions.teleportAcceptConfirmed(config.get(), message);
    }

    boolean teleportRequestGone(String message) {
        return PlaneKitbotRefillDecisions.teleportRequestGone(config.get(), message);
    }

    PlaneKitbotRefillDecisions.IgnoredTeleportPrompt ignoredTeleportPrompt(String message) {
        return PlaneKitbotRefillDecisions.ignoredTeleportPrompt(config.get(), message);
    }

    PlaneKitbotRefillDecisions.KitbotDeliveryMessage kitbotDeliveryMessage(String message) {
        return PlaneKitbotRefillDecisions.kitbotDeliveryMessage(config.get(), message);
    }

    boolean sendTeleportAccept() {
        if (!clientReady.getAsBoolean()) return false;

        String command = PlaneKitbotRefillDecisions.teleportAcceptCommand(config.get());
        if (command == null) return false;

        sender.send(command);
        return true;
    }

    String teleportAcceptCommand() {
        if (!clientReady.getAsBoolean()) return null;

        return PlaneKitbotRefillDecisions.teleportAcceptCommand(config.get());
    }

    boolean sendCommand(String command) {
        if (!clientReady.getAsBoolean() || command == null) return false;

        sender.send(command);
        return true;
    }

    interface MessageSender {
        void send(String message);
    }
}
