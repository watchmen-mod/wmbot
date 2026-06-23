package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class PlaneElytraFlySession {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PlaneModuleAccess moduleAccess = new PlaneModuleAccess();
    private final List<PlaneModuleAccess.PlaneModuleSettingSnapshot> snapshots = new ArrayList<>();
    private ElytraFly elytraFly;
    private boolean wasActive;
    private boolean applied;

    boolean start() {
        if (applied) return elytraFly != null;
        if (!hasUsableElytra()) return false;

        elytraFly = moduleAccess.get(ElytraFly.class) instanceof ElytraFly module ? module : null;
        if (elytraFly == null) return false;

        snapshots.clear();
        snapshotSettings();
        wasActive = moduleAccess.active(elytraFly);
        applyPlaneSettings();
        if (!wasActive) moduleAccess.toggle(elytraFly);
        applied = true;
        return true;
    }

    boolean active() {
        return applied;
    }

    void stop() {
        if (!applied || elytraFly == null) return;

        if (!wasActive && moduleAccess.active(elytraFly)) moduleAccess.toggle(elytraFly);
        restoreSettings();
        snapshots.clear();
        elytraFly = null;
        wasActive = false;
        applied = false;
    }

    private void snapshotSettings() {
        snapshots.add(moduleAccess.snapshot(elytraFly, "mode"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "auto-take-off"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "fall-multiplier"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "horizontal-speed"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "vertical-speed"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "acceleration"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "acceleration-step"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "auto-hover"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "no-crash"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "crash-look-ahead"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "elytra-replace"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "chest-swap"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "replenish-fireworks"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "auto-pilot"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "use-fireworks"));
        snapshots.add(moduleAccess.snapshot(elytraFly, "minimum-height"));
    }

    private void applyPlaneSettings() {
        elytraFly.flightMode.set(ElytraFlightModes.Vanilla);
        elytraFly.autoTakeOff.set(true);
        elytraFly.fallMultiplier.set(0.08);
        elytraFly.horizontalSpeed.set(1.0);
        elytraFly.verticalSpeed.set(1.0);
        elytraFly.acceleration.set(true);
        elytraFly.accelerationStep.set(0.5);
        elytraFly.autoHover.set(true);
        elytraFly.noCrash.set(true);
        elytraFly.crashLookAhead.set(5);
        elytraFly.replace.set(true);
        elytraFly.chestSwap.set(ElytraFly.ChestSwapMode.Always);
        elytraFly.autoReplenish.set(false);
        elytraFly.autoPilot.set(false);
        elytraFly.useFireworks.set(false);
        elytraFly.autoPilotMinimumHeight.set(0.0);
    }

    private boolean hasUsableElytra() {
        if (mc.player == null) return false;

        if (PlaneItemClassifier.isUsableElytraStack(mc.player.getEquippedStack(EquipmentSlot.CHEST))) return true;

        int inventorySlots = mc.player.getInventory().size();
        for (int slot = 0; slot < inventorySlots; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (PlaneItemClassifier.isUsableElytraStack(stack)) return true;
        }

        return false;
    }

    private void restoreSettings() {
        for (PlaneModuleAccess.PlaneModuleSettingSnapshot snapshot : snapshots) {
            snapshot.restore();
        }
    }
}
