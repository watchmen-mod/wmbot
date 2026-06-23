package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

record BuildBlockPreparation(
    FindItemResult result,
    boolean alreadyUsable,
    boolean mainInventorySourceFound,
    boolean hotbarPromotionAttempted
) {
    static BuildBlockPreparation alreadyUsable(FindItemResult result) {
        return new BuildBlockPreparation(result, true, false, false);
    }

    static BuildBlockPreparation missing(FindItemResult result) {
        return new BuildBlockPreparation(result, false, false, false);
    }

    static BuildBlockPreparation afterHotbarPromotion(FindItemResult result) {
        return new BuildBlockPreparation(result, false, true, true);
    }

    boolean buildBlockFound() {
        return alreadyUsable || mainInventorySourceFound;
    }
}
