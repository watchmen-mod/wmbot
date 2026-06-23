package com.watchmenbot.modules.stash;

final class StashSkipReasons {
    static final String CHANGED_OR_MISSING = "changed-or-missing";
    static final String PATH_TIMEOUT = "path-timeout";
    static final String UNEXPECTED_SCREEN = "unexpected-screen";
    static final String OPEN_TIMEOUT = "open-timeout";
    static final String CLOSED_SCREEN = "closed-screen";

    private StashSkipReasons() {
    }
}
