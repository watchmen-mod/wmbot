package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.hud.PlaneBuilderStatsHudText;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertEquals;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertFalse;
import static com.watchmenbot.modules.planebuilder.PlaneTestAssertions.assertTrue;

final class PlaneBuilderStatsPureTest {
    private PlaneBuilderStatsPureTest() {
    }

    static void run() {
        countsConfirmedPlacements();
        expiresRollingWindows();
        ignoresDuplicatePendingTargetConfirmation();
        resetClearsCountsAndRates();
        tracksRuntime();
        formatsRuntimeForHud();
    }

    private static void countsConfirmedPlacements() {
        PlaneBuilderStats stats = new PlaneBuilderStats();
        BlockPos target = new BlockPos(1, PlaneBuilderSettings.BUILD_Y, 2);

        stats.attemptedPlacement(target);
        assertTrue(stats.confirmPlaced(target, 1_000L), "confirmed pending placement counts");

        PlaneBuilderStats.Snapshot snapshot = stats.snapshot(1_000L);
        assertEquals(1L, snapshot.placedThisSession(), "session count increments");
        assertEquals(1.0, snapshot.perSecond(), "one confirmed placement appears in one second rate");
        assertEquals(1.0, snapshot.perMinute(), "one confirmed placement appears in one minute rate");
        assertEquals(1.0, snapshot.perHour(), "one confirmed placement appears in one hour rate");
    }

    private static void expiresRollingWindows() {
        PlaneBuilderStats stats = new PlaneBuilderStats();
        confirm(stats, 1, 0L);
        confirm(stats, 2, 2_000L);
        confirm(stats, 3, 61_000L);

        PlaneBuilderStats.Snapshot snapshot = stats.snapshot(61_000L);
        assertEquals(3L, snapshot.placedThisSession(), "session count keeps all confirmed placements");
        assertEquals(1.0, snapshot.perSecond(), "one second rate drops older placements");
        assertEquals(2.0, snapshot.perMinute(), "one minute rate drops placements older than sixty seconds");
        assertEquals(3.0, snapshot.perHour(), "one hour rate keeps recent placements");

        PlaneBuilderStats.Snapshot later = stats.snapshot(3_661_001L);
        assertEquals(3L, later.placedThisSession(), "session count survives hour window expiry");
        assertEquals(0.0, later.perSecond(), "one second rate eventually expires");
        assertEquals(0.0, later.perMinute(), "one minute rate eventually expires");
        assertEquals(0.0, later.perHour(), "one hour rate eventually expires");
    }

    private static void ignoresDuplicatePendingTargetConfirmation() {
        PlaneBuilderStats stats = new PlaneBuilderStats();
        BlockPos target = new BlockPos(4, PlaneBuilderSettings.BUILD_Y, 5);

        stats.attemptedPlacement(target);
        stats.attemptedPlacement(target);

        assertTrue(stats.confirmPlaced(target, 5_000L), "first confirmation counts");
        assertFalse(stats.confirmPlaced(target, 5_001L), "duplicate confirmation does not count");

        PlaneBuilderStats.Snapshot snapshot = stats.snapshot(5_001L);
        assertEquals(1L, snapshot.placedThisSession(), "duplicate target is only counted once");
        assertEquals(1.0, snapshot.perSecond(), "duplicate target does not inflate rate");
    }

    private static void resetClearsCountsAndRates() {
        PlaneBuilderStats stats = new PlaneBuilderStats();
        stats.start(9_000L);
        confirm(stats, 6, 10_000L);

        stats.reset();

        PlaneBuilderStats.Snapshot snapshot = stats.snapshot(10_000L);
        assertEquals(0L, snapshot.placedThisSession(), "reset clears session count");
        assertEquals(0.0, snapshot.perSecond(), "reset clears one second rate");
        assertEquals(0.0, snapshot.perMinute(), "reset clears one minute rate");
        assertEquals(0.0, snapshot.perHour(), "reset clears one hour rate");
    }

    private static void tracksRuntime() {
        PlaneBuilderStats stats = new PlaneBuilderStats();

        assertEquals(0L, stats.snapshot(10_000L).runtimeMillis(), "inactive stats have no runtime");

        stats.start(10_000L);
        assertEquals(0L, stats.snapshot(10_000L).runtimeMillis(), "runtime starts at zero");
        assertEquals(12_345L, stats.snapshot(22_345L).runtimeMillis(), "runtime increases from start timestamp");

        stats.reset();
        assertEquals(0L, stats.snapshot(30_000L).runtimeMillis(), "reset clears runtime");

        stats.start(40_000L);
        assertEquals(2_000L, stats.snapshot(42_000L).runtimeMillis(), "runtime starts fresh after reset");
    }

    private static void formatsRuntimeForHud() {
        assertEquals("12s", PlaneBuilderStatsHudText.formatRuntime(12_999L), "runtime formats seconds");
        assertEquals("03:14", PlaneBuilderStatsHudText.formatRuntime(194_000L), "runtime formats minutes and seconds");
        assertEquals("1:02:09", PlaneBuilderStatsHudText.formatRuntime(3_729_000L), "runtime formats hours");
        assertEquals("0s", PlaneBuilderStatsHudText.formatRuntime(-1_000L), "runtime clamps negative values");

        List<String> lines = PlaneBuilderStatsHudText.buildLines(new PlaneBuilder.StatsSnapshot(
            true,
            "active",
            "placing obsidian",
            12_000L,
            4L,
            1.0,
            4.0,
            4.0
        ));
        assertEquals("Runtime: 12s", lines.get(3), "hud includes runtime after phase");
    }

    private static void confirm(PlaneBuilderStats stats, int x, long nowMillis) {
        BlockPos target = new BlockPos(x, PlaneBuilderSettings.BUILD_Y, x);
        stats.attemptedPlacement(target);
        stats.confirmPlaced(target, nowMillis);
    }
}
