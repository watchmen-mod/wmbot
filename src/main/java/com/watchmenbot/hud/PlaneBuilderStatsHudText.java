package com.watchmenbot.hud;

import com.watchmenbot.modules.planebuilder.PlaneBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaneBuilderStatsHudText {
    private PlaneBuilderStatsHudText() {
    }

    public static List<String> buildLines(PlaneBuilder.StatsSnapshot stats) {
        List<String> lines = new ArrayList<>();
        lines.add("Plane Builder");
        lines.add("Status: " + stats.status());
        lines.add("Phase: " + stats.phase());
        lines.add("Runtime: " + formatRuntime(stats.runtimeMillis()));
        lines.add("Placed: " + stats.placedThisSession());
        lines.add("Rate/s: " + formatRate(stats.perSecond()));
        lines.add("Rate/min: " + formatRate(stats.perMinute()));
        lines.add("Rate/hour: " + formatRate(stats.perHour()));
        return lines;
    }

    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String formatRuntime(long runtimeMillis) {
        long totalSeconds = Math.max(0L, runtimeMillis / 1_000L);
        if (totalSeconds < 60L) return totalSeconds + "s";

        long seconds = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        if (totalMinutes < 60L) return String.format(Locale.ROOT, "%02d:%02d", totalMinutes, seconds);

        long minutes = totalMinutes % 60L;
        long hours = totalMinutes / 60L;
        return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
    }
}
