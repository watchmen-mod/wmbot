package com.watchmenbot.modules.planebuilder;

final class PlaneHotbarMutationGuard {
    static final int DEFAULT_MAX_REPEATED_ATTEMPTS = 6;
    static final int DEFAULT_COOLDOWN_TICKS = 4;

    private final int maxRepeatedAttempts;
    private final int cooldownTicks;

    private Mutation lastMutation;
    private int repeatedAttempts;
    private int cooldown;

    PlaneHotbarMutationGuard() {
        this(DEFAULT_MAX_REPEATED_ATTEMPTS, DEFAULT_COOLDOWN_TICKS);
    }

    PlaneHotbarMutationGuard(int maxRepeatedAttempts, int cooldownTicks) {
        this.maxRepeatedAttempts = Math.max(1, maxRepeatedAttempts);
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    boolean allow(String itemRole, int sourceSlot, int hotbarSlot, int selectedSlot) {
        if (sourceSlot < 0 || hotbarSlot < 0) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        Mutation mutation = new Mutation(itemRole, sourceSlot, hotbarSlot, selectedSlot);
        if (mutation.equals(lastMutation)) repeatedAttempts++;
        else {
            lastMutation = mutation;
            repeatedAttempts = 1;
        }

        if (repeatedAttempts > maxRepeatedAttempts) {
            cooldown = cooldownTicks;
            lastMutation = null;
            repeatedAttempts = 0;
            return false;
        }

        return true;
    }

    void reset() {
        lastMutation = null;
        repeatedAttempts = 0;
        cooldown = 0;
    }

    int repeatedAttempts() {
        return repeatedAttempts;
    }

    private record Mutation(String itemRole, int sourceSlot, int hotbarSlot, int selectedSlot) {
    }
}
