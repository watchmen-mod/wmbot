package com.watchmenbot.modules.planebuilder;

interface PlaneKitbotDroppedSupplyTracker {
    void captureBaseline();

    boolean hasNewDeliveryDrop();

    Phase tickPickup();

    void reset();
}
