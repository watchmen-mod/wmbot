package com.watchmenbot.modules.planebuilder;

import com.watchmenbot.util.WorkflowLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;

final class PlaneDroppedItemSafety {
    private static final int LOG_INTERVAL_TICKS = 20;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneMovementSafetyPolicy movementSafety;
    private final WorkflowLogger logger;
    private int logTicks;

    PlaneDroppedItemSafety(PlaneMovementSafetyPolicy movementSafety, WorkflowLogger logger) {
        this.movementSafety = movementSafety == null ? new PlaneMovementSafetyPolicy() : movementSafety;
        this.logger = logger == null ? PlaneWorkflowLoggers.NOOP : logger;
    }

    boolean safe(ItemEntity item) {
        return decision(item).accepted();
    }

    void logRejected(ItemEntity item, Phase phase, String reason) {
        if (logTicks++ < LOG_INTERVAL_TICKS) return;
        logTicks = 0;

        BlockPos pos = item == null ? null : item.getBlockPos();
        String stack = item == null ? "unknown" : item.getStack().getItem().toString();
        PlaneMovementSafetyPolicy.Decision decision = decision(item);
        logger.info(
            "Ignored unsafe dropped-item pickup target: phase=%s item=%s pos=%s reason=%s safety=%s.",
            phase.label(),
            stack,
            pos,
            reason,
            decision.reason()
        );
    }

    private PlaneMovementSafetyPolicy.Decision decision(ItemEntity item) {
        if (item == null) return PlaneMovementSafetyPolicy.Decision.reject(PlaneMovementSafetyPolicy.RejectReason.MISSING_GOAL);

        BlockPos playerPos = mc.player == null ? null : mc.player.getBlockPos();
        return movementSafety.validatePlatformGoal(playerPos, item.getBlockPos());
    }
}
