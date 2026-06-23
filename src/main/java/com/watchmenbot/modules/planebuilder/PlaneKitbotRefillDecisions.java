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
        String whisperCommand = clean(config.whisperCommand());
        if (nickname.isEmpty() || kitName.isEmpty() || config.kitCount() < 1 || whisperCommand.isEmpty()) {
            return null;
        }

        return whisperCommand + " " + nickname + " " + kitName + " " + config.kitCount();
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

    static IgnoredTeleportPrompt ignoredTeleportPrompt(PlaneKitbotRefillConfig config, String message) {
        if (config == null || !config.enabled()) return null;

        String configuredNickname = clean(config.nickname());
        if (configuredNickname.isEmpty()) return null;

        String requester = PlaneKitbotPromptParser.teleportPromptRequester(message);
        if (requester == null || requester.equalsIgnoreCase(configuredNickname)) return null;

        return new IgnoredTeleportPrompt(requester, configuredNickname);
    }

    static String teleportAcceptCommand(PlaneKitbotRefillConfig config) {
        if (config == null || !config.enabled()) return null;

        String nickname = clean(config.nickname());
        String acceptCommand = clean(config.teleportAcceptCommand());
        if (acceptCommand.isEmpty()) acceptCommand = "/tpy";
        if (nickname.isEmpty()) return null;
        return acceptCommand + " " + nickname;
    }

    static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    record IgnoredTeleportPrompt(String requester, String configuredNickname) {
    }
}
