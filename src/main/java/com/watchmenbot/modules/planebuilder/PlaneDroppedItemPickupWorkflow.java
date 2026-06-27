package com.watchmenbot.modules.planebuilder;

import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaneDroppedItemPickupWorkflow<T> {
    private final Supplier<T> targetSupplier;
    private final Predicate<T> targetPredicate;
    private final Predicate<T> targetPickupable;
    private final Predicate<T> targetSafe;
    private final RejectedTargetLogger<T> rejectedTargetLogger;
    private final Navigator<T> pickupNavigator;
    private final Phase activePhase;
    private final Phase fallbackPhase;
    private final int noTargetGraceTicks;
    private final int maxTargetTicks;
    private final boolean waitForPickupableTarget;

    private T pickupTarget;
    private int noTargetTicks;
    private int targetTicks;

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks
    ) {
        this(targetSupplier, targetPredicate, target -> true, pickupNavigator, activePhase, fallbackPhase, noTargetGraceTicks);
    }

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Predicate<T> targetPickupable,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks
    ) {
        this(targetSupplier, targetPredicate, targetPickupable, pickupNavigator, activePhase, fallbackPhase, noTargetGraceTicks, -1);
    }

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Predicate<T> targetPickupable,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks,
        int maxTargetTicks
    ) {
        this(targetSupplier, targetPredicate, targetPickupable, pickupNavigator, activePhase, fallbackPhase, noTargetGraceTicks, maxTargetTicks, false);
    }

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Predicate<T> targetPickupable,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks,
        int maxTargetTicks,
        boolean waitForPickupableTarget
    ) {
        this(
            targetSupplier,
            targetPredicate,
            targetPickupable,
            target -> true,
            noopRejectedTargetLogger(),
            pickupNavigator,
            activePhase,
            fallbackPhase,
            noTargetGraceTicks,
            maxTargetTicks,
            waitForPickupableTarget
        );
    }

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Predicate<T> targetPickupable,
        Predicate<T> targetSafe,
        RejectedTargetLogger<T> rejectedTargetLogger,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks,
        int maxTargetTicks,
        boolean waitForPickupableTarget
    ) {
        this.targetSupplier = targetSupplier;
        this.targetPredicate = targetPredicate;
        this.targetPickupable = targetPickupable;
        this.targetSafe = targetSafe == null ? target -> true : targetSafe;
        this.rejectedTargetLogger = rejectedTargetLogger == null ? noopRejectedTargetLogger() : rejectedTargetLogger;
        this.pickupNavigator = pickupNavigator;
        this.activePhase = activePhase;
        this.fallbackPhase = fallbackPhase;
        this.noTargetGraceTicks = Math.max(0, noTargetGraceTicks);
        this.maxTargetTicks = maxTargetTicks;
        this.waitForPickupableTarget = waitForPickupableTarget;
    }

    PlaneDroppedItemPickupWorkflow(
        Supplier<T> targetSupplier,
        Predicate<T> targetPredicate,
        Predicate<T> targetPickupable,
        Predicate<T> targetSafe,
        RejectedTargetLogger<T> rejectedTargetLogger,
        Navigator<T> pickupNavigator,
        Phase activePhase,
        Phase fallbackPhase,
        int noTargetGraceTicks,
        int maxTargetTicks
    ) {
        this(
            targetSupplier,
            targetPredicate,
            targetPickupable,
            targetSafe,
            rejectedTargetLogger,
            pickupNavigator,
            activePhase,
            fallbackPhase,
            noTargetGraceTicks,
            maxTargetTicks,
            false
        );
    }

    void reset() {
        pickupTarget = null;
        noTargetTicks = 0;
        targetTicks = 0;
        pickupNavigator.stop();
    }

    boolean hasTarget() {
        T target = acquireTarget();
        return target != null && (waitForPickupableTarget || targetPickupable.test(target));
    }

    Phase tick() {
        T previousTarget = pickupTarget;
        T target = acquireTarget();
        if (target != previousTarget) targetTicks = 0;

        if (target == null) {
            pickupNavigator.stop();
            targetTicks = 0;
            if (noTargetTicks++ < noTargetGraceTicks) return activePhase;

            reset();
            return fallbackPhase;
        }

        if (!targetPickupable.test(target)) {
            pickupNavigator.stop();
            noTargetTicks = 0;
            if (!waitForPickupableTarget) {
                pickupTarget = null;
                targetTicks = 0;
                return fallbackPhase;
            }
            if (maxTargetTicks >= 0 && targetTicks >= maxTargetTicks) {
                reset();
                return fallbackPhase;
            }

            targetTicks++;
            return activePhase;
        }

        noTargetTicks = 0;
        if (maxTargetTicks >= 0 && targetTicks >= maxTargetTicks) {
            reset();
            return fallbackPhase;
        }

        targetTicks++;
        pickupNavigator.pathTo(target);
        return activePhase;
    }

    private T acquireTarget() {
        if (!targetPredicate.test(pickupTarget)) {
            pickupTarget = targetSupplier.get();
        }

        if (!targetPredicate.test(pickupTarget)) return null;
        if (!targetSafe.test(pickupTarget)) {
            rejectedTargetLogger.rejected(pickupTarget, activePhase, "unsafe-platform-goal");
            pickupTarget = null;
            return null;
        }

        return pickupTarget;
    }

    interface Navigator<T> {
        void pathTo(T target);

        void stop();
    }

    interface RejectedTargetLogger<T> {
        void rejected(T target, Phase phase, String reason);
    }

    private static <T> RejectedTargetLogger<T> noopRejectedTargetLogger() {
        return (target, phase, reason) -> {
        };
    }
}
