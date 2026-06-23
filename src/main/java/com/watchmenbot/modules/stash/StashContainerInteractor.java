package com.watchmenbot.modules.stash;

import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class StashContainerInteractor {
    private final MinecraftClient mc;
    private final StashContainerReader reader;

    StashContainerInteractor(MinecraftClient mc, StashContainerReader reader) {
        this.mc = mc;
        this.reader = reader;
    }

    void interactWithTarget(StashTarget target, BooleanSupplier stillCurrent) {
        interactWithTarget(target, stillCurrent, result -> {
        });
    }

    void interactWithTarget(StashTarget target, BooleanSupplier stillCurrent, Consumer<StashScannerOpenAttempts.InteractionResult> resultConsumer) {
        if (target == null || !StashClientUtils.canUse(mc)) return;

        Vec3d hit = Vec3d.ofCenter(target.interactionPos());
        Rotations.rotate(Rotations.getYaw(hit), Rotations.getPitch(hit), () -> {
            if (!StashClientUtils.canUse(mc) || !stillCurrent.getAsBoolean()) {
                resultConsumer.accept(new StashScannerOpenAttempts.InteractionResult(true, false));
                return;
            }

            BlockHitResult hitResult = new BlockHitResult(hit, hitSide(hit), target.interactionPos(), false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            resultConsumer.accept(new StashScannerOpenAttempts.InteractionResult(true, result.isAccepted()));
        });
    }

    int openedSize(ScreenHandler handler) {
        return reader.openedStorageSize(handler);
    }

    boolean screenMatchesTarget(ScreenHandler handler, StashTarget target) {
        return reader.screenMatchesTarget(handler, target);
    }

    private Direction hitSide(Vec3d hit) {
        if (mc.player == null) return Direction.UP;

        Vec3d delta = hit.subtract(mc.player.getEyePos());
        double absX = Math.abs(delta.x);
        double absY = Math.abs(delta.y);
        double absZ = Math.abs(delta.z);
        if (absY >= absX && absY >= absZ) return delta.y > 0 ? Direction.DOWN : Direction.UP;
        if (absX >= absZ) return delta.x > 0 ? Direction.WEST : Direction.EAST;
        return delta.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }
}
