package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotRefillDecisions {
    private PlaneKitbotRefillDecisions() {
    }

    static Phase missingSupplyPhase(boolean refillEnabled, boolean hasEnoughEnderChestSupply) {
        return missingSupplyPhase(refillEnabled, hasEnoughEnderChestSupply, false);
    }

    static Phase missingSupplyPhase(boolean refillEnabled, boolean hasEnoughEnderChestSupply, boolean managedSupplyActive) {
        if (managedSupplyActive) return Phase.MISSING_ENDER_CHEST_SHULKER;
        if (hasEnoughEnderChestSupply) return Phase.SELECTING_REPLENISH_SOURCE;
        return refillEnabled ? Phase.CLOSING_SERVICE_HOLE_FOR_KITBOT_REFILL : Phase.MISSING_ENDER_CHEST_SHULKER;
    }

    static Phase waitingPhase(boolean hasLooseEnderChest, boolean hasEnderChestShulker) {
        if (hasLooseEnderChest) return Phase.PLACING_ENDER_CHEST;
        if (hasEnderChestShulker) return Phase.PLACING_ENDER_CHEST_SHULKER;
        return Phase.WAITING_FOR_KITBOT_REFILL;
    }

    static Phase waitingPhase(boolean hasLooseEnderChest, boolean hasEnderChestShulker, boolean hasDroppedSupply) {
        Phase inventoryPhase = waitingPhase(hasLooseEnderChest, hasEnderChestShulker);
        if (inventoryPhase != Phase.WAITING_FOR_KITBOT_REFILL) return inventoryPhase;

        return hasDroppedSupply ? Phase.PICKING_UP_KITBOT_REFILL : Phase.WAITING_FOR_KITBOT_REFILL;
    }

    static Phase pickupPhase(boolean hasLooseEnderChest, boolean hasEnderChestShulker, boolean targetStillAvailable) {
        Phase inventoryPhase = waitingPhase(hasLooseEnderChest, hasEnderChestShulker);
        if (inventoryPhase != Phase.WAITING_FOR_KITBOT_REFILL) return inventoryPhase;

        return targetStillAvailable ? Phase.PICKING_UP_KITBOT_REFILL : Phase.WAITING_FOR_KITBOT_REFILL;
    }

    static String requestCommand(PlaneKitbotRefillConfig config) {
        if (config == null || !config.enabled()) return null;

        String nickname = clean(config.nickname());
        String kitName = clean(config.kitName());
        if (nickname.isEmpty() || kitName.isEmpty() || config.kitCount() < 1) {
            return null;
        }

        return PlaneBuilderSettings.KITBOT_WHISPER_COMMAND + " " + nickname + " " + kitName + " " + config.kitCount();
    }

    static String teleportAcceptCommand(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled()) return null;

        if (!teleportPromptMatches(config, message)) return null;
        return teleportAcceptCommand(config);
    }

    static boolean teleportPromptMatches(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled()) return false;

        String nickname = clean(config.nickname());
        if (nickname.isEmpty()) return false;
        return PlaneKitbotPromptParser.teleportPromptMatches(message, nickname);
    }

    static boolean teleportAcceptConfirmed(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled() || message == null) return false;

        String nickname = clean(config.nickname());
        if (nickname.isEmpty()) return false;

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        String lowerNickname = nickname.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(lowerNickname)
            && lower.contains("request from")
            && lower.contains("accepted");
    }

    static boolean teleportRequestGone(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled() || message == null) return false;

        String nickname = clean(config.nickname());
        if (nickname.isEmpty()) return false;

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(nickname.toLowerCase(java.util.Locale.ROOT))
            && lower.contains("no request to accept");
    }

    static IgnoredTeleportPrompt ignoredTeleportPrompt(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled()) return null;

        String configuredNickname = clean(config.nickname());
        if (configuredNickname.isEmpty()) return null;

        String requester = PlaneKitbotPromptParser.teleportPromptRequester(message);
        if (requester == null || requester.equalsIgnoreCase(configuredNickname)) return null;

        return new IgnoredTeleportPrompt(requester, configuredNickname);
    }

    static KitbotDeliveryMessage kitbotDeliveryMessage(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled() || message == null) return KitbotDeliveryMessage.IGNORED;

        String nickname = clean(config.nickname());
        if (nickname.isEmpty()) return KitbotDeliveryMessage.IGNORED;

        String lower = message.toLowerCase(java.util.Locale.ROOT);
        String lowerNickname = nickname.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains(lowerNickname)) return KitbotDeliveryMessage.IGNORED;
        if (!lower.contains(lowerNickname + " whispers")) return KitbotDeliveryMessage.IGNORED;

        if (lower.contains("delivery timed out waiting for")
            || lower.contains("delivery cancelled before teleport")
            || lower.contains("returned to the stash position with the kits")) {
            return KitbotDeliveryMessage.FAILED;
        }

        if ((lower.contains("delivered") && lower.contains("returning home"))
            || (lower.contains("finished") && lower.contains("delivery"))) {
            return KitbotDeliveryMessage.DELIVERED;
        }

        if (lower.contains("sending tpa")) {
            return KitbotDeliveryMessage.TPA_REQUESTED;
        }

        if (lower.contains("you already have a kit request in progress")
            || (lower.contains("accepted") && lower.contains("preparing to gather"))
            || (lower.contains("accepted") && lower.contains("gathering now"))
            || lower.contains("gathering ")
            || lower.contains("i already have it in inventory")) {
            return KitbotDeliveryMessage.ACTIVE;
        }

        return KitbotDeliveryMessage.IGNORED;
    }

    static String teleportAcceptCommand(PlaneKitbotRefillConfig config) {
        if (config == null || !config.enabled()) return null;

        String nickname = clean(config.nickname());
        if (nickname.isEmpty()) return null;
        return PlaneBuilderSettings.KITBOT_TELEPORT_ACCEPT_COMMAND + " " + nickname;
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    record IgnoredTeleportPrompt(String requester, String configuredNickname) {
    }

    enum KitbotDeliveryMessage {
        IGNORED,
        ACTIVE,
        TPA_REQUESTED,
        FAILED,
        DELIVERED
    }
}
