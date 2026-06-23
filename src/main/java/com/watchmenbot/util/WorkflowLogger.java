package com.watchmenbot.util;

public interface WorkflowLogger {
    void info(String message, Object... args);

    void warning(String message, Object... args);
}
