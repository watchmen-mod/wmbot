package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.systems.modules.combat.BowAimbot;
import meteordevelopment.meteorclient.utils.player.FindItemResult;

import java.util.ArrayList;
import java.util.List;

final class PlaneBowModuleSession {
    private final BowAimbotHandle bowAimbotHandle;
    private final HotbarSwapper hotbarSwapper;
    private final List<RestorableSetting> shotSettingSnapshots = new ArrayList<>();
    private final List<RestorableSetting> passiveSettingSnapshots = new ArrayList<>();

    private boolean shotBowAimbotWasActive;
    private boolean passiveBowAimbotWasActive;
    private boolean passiveLatched;
    private boolean swappedToBow;

    PlaneBowModuleSession() {
        this(new MeteorBowAimbotHandle(), new ActionHotbarSwapper());
    }

    PlaneBowModuleSession(BowAimbotHandle bowAimbotHandle, HotbarSwapper hotbarSwapper) {
        this.bowAimbotHandle = bowAimbotHandle;
        this.hotbarSwapper = hotbarSwapper;
    }

    boolean startPassiveLatch(double range) {
        if (passiveLatched) return true;
        if (!bowAimbotHandle.available()) return false;

        passiveBowAimbotWasActive = bowAimbotHandle.active();
        passiveSettingSnapshots.clear();
        passiveSettingSnapshots.addAll(bowAimbotHandle.applySettings(range));
        if (!bowAimbotHandle.active()) bowAimbotHandle.toggle();
        passiveLatched = true;
        return true;
    }

    boolean start(FindItemResult bow, double range, boolean passiveWindow) {
        if (!bowAimbotHandle.available()) return false;

        if (!passiveWindow) {
            shotBowAimbotWasActive = bowAimbotHandle.active();
            shotSettingSnapshots.clear();
            shotSettingSnapshots.addAll(bowAimbotHandle.applySettings(range));
        }

        if (!hotbarSwapper.swapToHotbarSlot(bow.slot())) {
            if (!passiveWindow) restoreShotSettings();
            return false;
        }
        swappedToBow = true;

        if (!bowAimbotHandle.active()) bowAimbotHandle.toggle();
        return true;
    }

    void stopShot() {
        if (!passiveLatched && bowAimbotHandle.available() && bowAimbotHandle.active() != shotBowAimbotWasActive) {
            bowAimbotHandle.toggle();
        }

        if (swappedToBow) {
            hotbarSwapper.swapBack();
            swappedToBow = false;
        }

        restoreShotSettings();
    }

    void releasePassiveLatch() {
        if (!passiveLatched) return;

        if (swappedToBow) {
            hotbarSwapper.swapBack();
            swappedToBow = false;
        }
        restoreShotSettings();

        if (bowAimbotHandle.available() && bowAimbotHandle.active() != passiveBowAimbotWasActive) {
            bowAimbotHandle.toggle();
        }
        restorePassiveSettings();
        passiveLatched = false;
    }

    void stopAll() {
        stopShot();
        releasePassiveLatch();
    }

    boolean active() {
        return swappedToBow || !shotSettingSnapshots.isEmpty() || passiveLatched;
    }

    boolean passiveLatched() {
        return passiveLatched;
    }

    private void restoreShotSettings() {
        for (RestorableSetting snapshot : shotSettingSnapshots) snapshot.restore();
        shotSettingSnapshots.clear();
    }

    private void restorePassiveSettings() {
        for (RestorableSetting snapshot : passiveSettingSnapshots) snapshot.restore();
        passiveSettingSnapshots.clear();
    }

    interface RestorableSetting {
        void restore();
    }

    interface BowAimbotHandle {
        boolean available();

        boolean active();

        void toggle();

        List<RestorableSetting> applySettings(double range);
    }

    interface HotbarSwapper {
        boolean swapToHotbarSlot(int slot);

        void swapBack();
    }

    private static final class MeteorBowAimbotHandle implements BowAimbotHandle {
        private final PlaneModuleAccess moduleAccess = new PlaneModuleAccess();
        private BowAimbot bowAimbot;

        @Override
        public boolean available() {
            if (bowAimbot == null && moduleAccess.get(BowAimbot.class) instanceof BowAimbot module) {
                bowAimbot = module;
            }

            return bowAimbot != null;
        }

        @Override
        public boolean active() {
            return available() && moduleAccess.active(bowAimbot);
        }

        @Override
        public void toggle() {
            if (available()) moduleAccess.toggle(bowAimbot);
        }

        @Override
        public List<RestorableSetting> applySettings(double range) {
            if (!available()) return List.of();

            List<CompanionModuleManager.SettingSnapshot> snapshots = BowAimbotCompanionSettings.apply(bowAimbot, range);
            List<RestorableSetting> restorable = new ArrayList<>(snapshots.size());
            for (CompanionModuleManager.SettingSnapshot snapshot : snapshots) restorable.add(snapshot::restore);
            return restorable;
        }
    }

    private static final class ActionHotbarSwapper implements HotbarSwapper {
        private final PlaneActionExecutor actions = new PlaneActionExecutor();

        @Override
        public boolean swapToHotbarSlot(int slot) {
            return actions.swapToHotbarSlot(slot);
        }

        @Override
        public void swapBack() {
            actions.swapBack();
        }
    }
}
