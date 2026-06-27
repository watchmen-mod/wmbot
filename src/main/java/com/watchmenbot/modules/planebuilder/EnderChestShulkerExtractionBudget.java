package com.watchmenbot.modules.planebuilder;

final class EnderChestShulkerExtractionBudget {
    static final int DEFAULT_PHASE_TIMEOUT_TICKS = 120;
    static final int DEFAULT_STALLED_TAKE_TICKS = 20;

    private final int phaseTimeoutTicks;
    private final int stalledTakeTicks;

    private Phase phase;
    private int phaseTicks;
    private int lastLooseEnderChests = -1;
    private int stalledTake;

    EnderChestShulkerExtractionBudget() {
        this(DEFAULT_PHASE_TIMEOUT_TICKS, DEFAULT_STALLED_TAKE_TICKS);
    }

    EnderChestShulkerExtractionBudget(int phaseTimeoutTicks, int stalledTakeTicks) {
        this.phaseTimeoutTicks = Math.max(1, phaseTimeoutTicks);
        this.stalledTakeTicks = Math.max(1, stalledTakeTicks);
    }

    void reset() {
        phase = null;
        phaseTicks = 0;
        lastLooseEnderChests = -1;
        stalledTake = 0;
    }

    boolean timedOut(Phase currentPhase) {
        tick(currentPhase);
        return phaseTicks > phaseTimeoutTicks;
    }

    boolean stalledTake(int looseEnderChests) {
        tick(Phase.TAKING_ENDER_CHESTS_FROM_SHULKER);
        if (looseEnderChests > lastLooseEnderChests) {
            lastLooseEnderChests = looseEnderChests;
            stalledTake = 0;
            return false;
        }

        if (lastLooseEnderChests < 0) lastLooseEnderChests = looseEnderChests;
        stalledTake++;
        return stalledTake >= stalledTakeTicks;
    }

    private void tick(Phase currentPhase) {
        if (phase != currentPhase) {
            phase = currentPhase;
            phaseTicks = 0;
            if (currentPhase != Phase.TAKING_ENDER_CHESTS_FROM_SHULKER) {
                lastLooseEnderChests = -1;
                stalledTake = 0;
            }
        }

        phaseTicks++;
    }
}
