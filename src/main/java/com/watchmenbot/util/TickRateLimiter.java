package com.watchmenbot.util;

public final class TickRateLimiter {
    private int cooldownTicks;

    public void reset() {
        cooldownTicks = 0;
    }

    public boolean ready(int resetTicks) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }

        cooldownTicks = resetTicks;
        return true;
    }
}
