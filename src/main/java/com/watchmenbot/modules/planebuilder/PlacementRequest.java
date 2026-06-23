package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

record PlacementRequest(
    BlockPos target,
    FindItemResult item,
    Block expectedBlock,
    Vec3d hit,
    BlockHitResult hitResult,
    Hand hand,
    Validator validator,
    Validator preparedValidator
) {
    boolean valid() {
        return target != null
            && item != null
            && hit != null
            && hitResult != null
            && hand != null
            && validator != null
            && preparedValidator != null
            && validator.valid();
    }

    boolean preparedValid() {
        return valid() && preparedValidator.valid();
    }

    interface Validator {
        boolean valid();
    }
}
