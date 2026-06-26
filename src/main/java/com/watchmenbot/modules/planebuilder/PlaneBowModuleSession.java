package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;

final class PlaneBowModuleSession {
    private final HotbarSwapper hotbarSwapper;

    private boolean swappedToBow;

    PlaneBowModuleSession() {
        this(new ActionHotbarSwapper());
    }

    PlaneBowModuleSession(HotbarSwapper hotbarSwapper) {
        this.hotbarSwapper = hotbarSwapper;
    }

    boolean start(FindItemResult bow) {
        if (!hotbarSwapper.swapToHotbarSlot(bow.slot())) return false;
        swappedToBow = true;
        return true;
    }

    void stopShot() {
        if (swappedToBow) {
            hotbarSwapper.swapBack();
            swappedToBow = false;
        }
    }

    void stopAll() {
        stopShot();
    }

    boolean active() {
        return swappedToBow;
    }

    boolean passiveLatched() {
        return false;
    }

    interface HotbarSwapper {
        boolean swapToHotbarSlot(int slot);

        void swapBack();
    }

    private static final class ActionHotbarSwapper implements HotbarSwapper {
        private final PlaneActionExecutor actions = new PlaneActionExecutor();

        @Override
        public boolean swapToHotbarSlot(int slot) {
            return actions.swapToHotbarSlot(slot);
        }

        @Override
        public void swapBack() {
            actions.swapBack();
        }
    }
}
