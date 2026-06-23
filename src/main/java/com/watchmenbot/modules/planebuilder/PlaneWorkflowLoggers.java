package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;

final class PlaneWorkflowLoggers {
    static final WorkflowLogger NOOP = new WorkflowLogger() {
        @Override
        public void info(String message, Object... args) {
        }

        @Override
        public void warning(String message, Object... args) {
        }
    };

    private PlaneWorkflowLoggers() {
    }
}
