package com.watchmenbot.modules.planebuilder;

import java.util.function.Predicate;
import java.util.function.Supplier;

final class PlaneDroppedItemPickupWorkflow<T> {
    private final Supplier<T> targetSupplier;
    private final Predicate<T> targetPredicate;
    private final Predicate<T> targetPickupable;
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
        this.targetSupplier = targetSupplier;
        this.targetPredicate = targetPredicate;
        this.targetPickupable = targetPickupable;
        this.pickupNavigator = pickupNavigator;
        this.activePhase = activePhase;
        this.fallbackPhase = fallbackPhase;
        this.noTargetGraceTicks = Math.max(0, noTargetGraceTicks);
        this.maxTargetTicks = maxTargetTicks;
        this.waitForPickupableTarget = waitForPickupableTarget;
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

        return targetPredicate.test(pickupTarget) ? pickupTarget : null;
    }

    interface Navigator<T> {
        void pathTo(T target);

        void stop();
    }
}
