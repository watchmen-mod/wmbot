package com.watchmenbot.modules.planebuilder;

public final class PlaneBuilderPureTest {
    private PlaneBuilderPureTest() {
    }

    public static void main(String[] args) {
        PlaneReplenishPureTest.run();
        PlaneBowDefensePureTest.run();
        PlaneKitbotRefillPureTest.run();
        PlaneUtilityPureTest.run();
        PlaneBuilderStatsPureTest.run();
        PlaneRefactorPureTest.run();
        PlaneModuleIsolationSessionPureTest.run();
        PlaneNametagsTeardownGuardPureTest.run();
    }
}
