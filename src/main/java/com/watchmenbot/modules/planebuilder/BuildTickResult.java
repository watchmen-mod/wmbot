package com.watchmenbot.modules.planebuilder;

record BuildTickResult(Phase phase, boolean startReplenish, boolean resetBowDefense) {
    static BuildTickResult phase(Phase phase) {
        return new BuildTickResult(phase, false, false);
    }

    static BuildTickResult missingObsidian() {
        return new BuildTickResult(Phase.MISSING_OBSIDIAN, false, false);
    }

    static BuildTickResult startingReplenish() {
        return new BuildTickResult(Phase.SELECTING_SERVICE_HOLE, true, true);
    }
}
