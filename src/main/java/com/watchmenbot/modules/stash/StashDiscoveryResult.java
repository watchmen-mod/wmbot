package com.watchmenbot.modules.stash;

import java.util.List;

record StashDiscoveryResult(List<StashTarget> targets, List<StashSkippedContainer> skipped) {
}
