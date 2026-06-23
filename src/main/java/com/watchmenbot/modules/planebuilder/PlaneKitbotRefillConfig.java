package com.watchmenbot.modules.planebuilder;

record PlaneKitbotRefillConfig(
    boolean enabled,
    String nickname,
    String kitName,
    int kitCount,
    String whisperCommand,
    String teleportAcceptCommand
) {
}
