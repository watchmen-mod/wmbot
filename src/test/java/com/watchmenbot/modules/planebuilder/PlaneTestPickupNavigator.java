package com.watchmenbot.modules.planebuilder;

final class PlaneTestPickupNavigator implements PlaneDroppedItemPickupWorkflow.Navigator<Object> {
    int pathTicks;
    int stopTicks;

    @Override
    public void pathTo(Object target) {
        pathTicks++;
    }

    @Override
    public void stop() {
        stopTicks++;
    }
}
