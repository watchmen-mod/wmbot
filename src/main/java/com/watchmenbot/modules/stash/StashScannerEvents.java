package com.watchmenbot.modules.stash;

import com.watchmenbot.util.WorkflowLogger;

import java.time.Instant;

interface StashScannerEvents extends WorkflowLogger {
    void writeCache(Instant finishedAt);

    void stopModule();
}
