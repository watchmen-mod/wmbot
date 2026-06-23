package com.watchmenbot.modules.stash;

import com.watchmenbot.util.WorkflowLogger;

interface StashKitbotEvents extends WorkflowLogger {

    void reply(String player, String message);

    void failRequest(String message);

    void delivered(KitRequest request);
}
