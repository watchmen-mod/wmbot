package com.watchmenbot.modules.stash;

record KitbotRequesterAccess(String requester, String normalizedRequester, KitbotTier tier, int cooldownTicks) {
}
