package com.watchmenbot.modules.sixb6t;

public final class SixB6TSession {
    public static final int INACTIVE_TICKS = -1;

    private static int loginTicksLeft = INACTIVE_TICKS;
    private static int portalStartTicks = INACTIVE_TICKS;
    private static int portalTicksLeft = INACTIVE_TICKS;
    private static int nextPortalTicks = INACTIVE_TICKS;
    private static int portalsEntered;
    private static boolean loginSent;
    private static boolean portalChainStarted;
    private static boolean pendingNextPortal;

    private SixB6TSession() {
    }

    public static int loginTicksLeft() {
        return loginTicksLeft;
    }

    public static void loginTicksLeft(int ticks) {
        loginTicksLeft = ticks;
    }

    public static int portalStartTicks() {
        return portalStartTicks;
    }

    public static void portalStartTicks(int ticks) {
        portalStartTicks = ticks;
    }

    public static int portalTicksLeft() {
        return portalTicksLeft;
    }

    public static void portalTicksLeft(int ticks) {
        portalTicksLeft = ticks;
    }

    public static int nextPortalTicks() {
        return nextPortalTicks;
    }

    public static void nextPortalTicks(int ticks) {
        nextPortalTicks = ticks;
    }

    public static int portalsEntered() {
        return portalsEntered;
    }

    public static void portalsEntered(int count) {
        portalsEntered = count;
    }

    public static void incrementPortalsEntered() {
        portalsEntered++;
    }

    public static boolean loginSent() {
        return loginSent;
    }

    public static void loginSent(boolean sent) {
        loginSent = sent;
    }

    public static boolean portalChainStarted() {
        return portalChainStarted;
    }

    public static void portalChainStarted(boolean started) {
        portalChainStarted = started;
    }

    public static boolean pendingNextPortal() {
        return pendingNextPortal;
    }

    public static void pendingNextPortal(boolean pending) {
        pendingNextPortal = pending;
    }

    public static boolean shouldPreservePortalState(int portalCount) {
        return portalChainStarted && portalsEntered > 0 && portalsEntered < portalCount;
    }

    public static void resetLogin() {
        loginTicksLeft = INACTIVE_TICKS;
        loginSent = false;
    }

    public static void resetPortals() {
        portalStartTicks = INACTIVE_TICKS;
        portalTicksLeft = INACTIVE_TICKS;
        nextPortalTicks = INACTIVE_TICKS;
        portalsEntered = 0;
        portalChainStarted = false;
        pendingNextPortal = false;
    }

    public static void resetAll() {
        resetLogin();
        resetPortals();
    }
}
