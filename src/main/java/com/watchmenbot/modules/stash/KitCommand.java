package com.watchmenbot.modules.stash;

record KitCommand(String name, int count, boolean quotedSearch) {
    KitCommand(String name, int count) {
        this(name, count, false);
    }
}
