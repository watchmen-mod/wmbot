package com.watchmenbot.modules.planebuilder;

final class PlaneBowUseActions {
    private final PlaneActionExecutor actions = new PlaneActionExecutor();

    void holdUse() {
        actions.pressUseKey();
    }

    void release() {
        actions.stopUsingItem();
    }

    void clearUse() {
        actions.releaseUseKey();
    }
}
