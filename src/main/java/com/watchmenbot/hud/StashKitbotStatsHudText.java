package com.watchmenbot.hud;

import com.watchmenbot.modules.stash.StashKitbot;

import java.util.ArrayList;
import java.util.List;

public final class StashKitbotStatsHudText {
    private StashKitbotStatsHudText() {
    }

    public static List<String> buildLines(StashKitbot.KitbotStatsSnapshot stats) {
        List<String> lines = new ArrayList<>();
        lines.add("Stash Kitbot");
        lines.add("Status: " + (stats.active() ? "active" : "inactive"));
        lines.add("Phase: " + stats.phase());
        lines.add("Queued requests: " + stats.queuedCount());
        lines.add("Completed deliveries: " + stats.completedDeliveries());
        lines.add("Delivered shulkers: " + stats.deliveredShulkers());

        StashKitbot.CurrentRequest request = stats.currentRequest();
        if (request != null) {
            lines.add(request.requester() + ": " + request.kitName() + " x" + request.requestedCount());
            lines.add("Gathered: " + request.gatheredCount() + "/" + request.requestedCount());
            lines.add("Delivered: " + request.deliveredCount() + "/" + request.requestedCount());
        }

        lines.add("Top requesters:");
        if (stats.topRequesters().isEmpty()) {
            lines.add("  none");
        }
        else {
            for (StashKitbot.RequesterDeliveryCount requester : stats.topRequesters()) {
                lines.add("  " + requester.requester() + ": " + requester.shulkersDelivered());
            }
        }

        return lines;
    }
}
