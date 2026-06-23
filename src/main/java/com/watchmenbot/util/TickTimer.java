package com.watchmenbot.util;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class TickTimer {
    private int ticks;
    private int initialTicks;
    private long startedAtNanos;

    public void reset(int ticks) {
        this.ticks = ticks;
        initialTicks = ticks;
        startedAtNanos = System.nanoTime();
    }

    public int ticks() {
        return ticks;
    }

    public boolean tickExpired() {
        return ticks-- <= 0;
    }

    public boolean tickOrElapsedExpired() {
        if (elapsedExpired()) return true;
        return tickExpired();
    }

    public boolean tickDelay() {
        if (ticks > 0) {
            ticks--;
            return true;
        }

        return false;
    }

    public static boolean tickDelay(IntSupplier getter, IntConsumer setter) {
        int ticks = getter.getAsInt();
        if (ticks > 0) {
            setter.accept(ticks - 1);
            return true;
        }

        return false;
    }

    private boolean elapsedExpired() {
        if (initialTicks <= 0) return false;

        long timeoutNanos = initialTicks * 50_000_000L;
        return System.nanoTime() - startedAtNanos >= timeoutNanos;
    }
}
