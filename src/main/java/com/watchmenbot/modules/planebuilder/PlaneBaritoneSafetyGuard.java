package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.BaritoneCompatibility;

final class PlaneBaritoneSafetyGuard {
    private final BaritoneSafety safety = BaritoneCompatibility.available() ? new BaritonePlaneSafetyGuard() : null;

    void apply() {
        if (safety != null) safety.apply();
    }

    void restore() {
        if (safety != null) safety.restore();
    }

    interface BaritoneSafety {
        void apply();

        void restore();
    }
}
