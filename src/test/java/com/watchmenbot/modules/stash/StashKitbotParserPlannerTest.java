package com.watchmenbot.modules.stash;

import com.watchmenbot.util.AtomicJsonFile;
import com.watchmenbot.util.TickTimer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertEquals;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertFalse;
import static com.watchmenbot.modules.stash.StashKitbotTestAssertions.assertTrue;

public final class StashKitbotParserPlannerTest {
    private StashKitbotParserPlannerTest() {
    }

    public static void main(String[] args) {
        StashKitbotRequestAccessPureTest.run();
        readsAndWritesAtomicJsonFiles();
        StashKitbotDeliveryPlannerPureTest.run();
        StashKitbotStockSourceCachePureTest.run();
        StashKitbotScannerPureTest.run();
        StashKitbotQueueSessionPureTest.run();
        StashKitbotStatsHudPureTest.run();
        expiresTimerByElapsedTime();
        keepsSkipReasonWireValues();
        formatsReturnCommands();
    }

    private static void readsAndWritesAtomicJsonFiles() {
        try {
            Path tempDir = Files.createTempDirectory("watchmenbot-atomic-json-test");
            Path file = tempDir.resolve("state.json");

            AtomicJsonFile.ReadResult<String> missing = AtomicJsonFile.readIfExists(file, "fallback", reader -> "unexpected");
            assertEquals("fallback", missing.value(), "missing atomic json file returns fallback");
            assertFalse(missing.failed(), "missing atomic json file is not a read failure");

            Files.writeString(file, "{not-json");
            AtomicJsonFile.ReadResult<Integer> failed = AtomicJsonFile.readIfExists(file, 7, reader -> {
                throw new IOException("malformed");
            });
            assertEquals(7, failed.value(), "failed atomic json read returns fallback");
            assertTrue(failed.failed(), "failed atomic json read surfaces error result");

            AtomicJsonFile.write(file, writer -> writer.write("{\"ok\":true}"));
            assertEquals("{\"ok\":true}", Files.readString(file), "atomic json write replaces file contents");
        }
        catch (Exception exception) {
            throw new AssertionError("atomic json file test failed", exception);
        }
    }

    private static void expiresTimerByElapsedTime() {
        TickTimer timer = new TickTimer();
        timer.reset(1);
        sleep(75);

        assertTrue(timer.tickOrElapsedExpired(), "timer expires by elapsed wall time");
    }

    private static void keepsSkipReasonWireValues() {
        assertEquals("changed-or-missing", StashSkipReasons.CHANGED_OR_MISSING, "changed skip reason");
        assertEquals("path-timeout", StashSkipReasons.PATH_TIMEOUT, "path timeout skip reason");
        assertEquals("unexpected-screen", StashSkipReasons.UNEXPECTED_SCREEN, "unexpected screen skip reason");
        assertEquals("open-timeout", StashSkipReasons.OPEN_TIMEOUT, "open timeout skip reason");
        assertEquals("closed-screen", StashSkipReasons.CLOSED_SCREEN, "closed screen skip reason");
    }

    private static void formatsReturnCommands() {
        assertEquals(
            "/kill",
            StashKitbotReturnCommand.command(StashKitbotReturnCommand.ReturnMethod.KILL, "/home stash", ""),
            "kill return method uses built-in kill command"
        );
        assertEquals(
            "/home stash",
            StashKitbotReturnCommand.command(StashKitbotReturnCommand.ReturnMethod.HOME, "/home stash", ""),
            "home return method uses configured home command"
        );
        assertEquals(
            "/spawn",
            StashKitbotReturnCommand.command(StashKitbotReturnCommand.ReturnMethod.CUSTOM, "/home stash", " /spawn "),
            "custom return method trims configured command"
        );
        assertEquals(
            "/kill",
            StashKitbotReturnCommand.command(StashKitbotReturnCommand.ReturnMethod.CUSTOM, "/home stash", ""),
            "blank custom return command falls back to kill"
        );
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("sleep interrupted", exception);
        }
    }
}
