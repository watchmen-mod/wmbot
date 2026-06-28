package com.watchmenbot.modules.planebuilder;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

final class PlaneBuilderSettings {
    static final int REPLENISH_MAX_OBSIDIAN = 2368;
    static final PlaneBuildConfig BUILD_CONFIG = PlaneBuildConfig.DEFAULT;
    static final int BUILD_Y = BUILD_CONFIG.buildY();
    static final int MIN_X = BUILD_CONFIG.minX();
    static final int MAX_X = BUILD_CONFIG.maxX();
    static final int MIN_Z = BUILD_CONFIG.minZ();
    static final int MAX_Z = BUILD_CONFIG.maxZ();
    static final int SCAN_RADIUS = BUILD_CONFIG.scanRadius();
    static final int AUTO_WALK_LANE_SPACING = BUILD_CONFIG.autoWalkLaneSpacing();
    static final int ROTATION_PRIORITY = BUILD_CONFIG.rotationPriority();
    static final int REPLENISH_MIN_OBSIDIAN = BUILD_CONFIG.replenishMinBuildBlocks();
    static final int REPLENISH_TARGET_OBSIDIAN = BUILD_CONFIG.replenishTargetBuildBlocks();
    static final double BOW_DEFENSE_RANGE = 24.0;
    static final int BOW_DEFENSE_CHARGE_TICKS = 35;
    static final int AUTO_WALK_WAYPOINT_RADIUS = 2;
    static final int AUTO_ELYTRA_SOLID_LOOKAHEAD = 10;
    static final int PICKAXE_DURABILITY_THRESHOLD_PERCENT = 5;
    static final int WEAPON_DURABILITY_THRESHOLD_PERCENT = 10;
    static final double ENDERMAN_LOOK_RADIUS = 24.0;
    static final int SAFE_IDLE_LOOK_PITCH = 75;
    static final String KITBOT_WHISPER_COMMAND = "/w";
    static final String KITBOT_TELEPORT_ACCEPT_COMMAND = "/tpy";

    private PlaneBuilderSettings() {
    }

    static CompanionModules companionModules(SettingGroup group) {
        Setting<Boolean> autoTotem = group.add(new BoolSetting.Builder()
            .name("auto-totem")
            .description("Enables Auto Totem while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> autoEat = group.add(new BoolSetting.Builder()
            .name("auto-eat")
            .description("Enables Auto Eat while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> velocity = group.add(new BoolSetting.Builder()
            .name("velocity")
            .description("Enables Velocity while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> instantRebreak = group.add(new BoolSetting.Builder()
            .name("instant-rebreak")
            .description("Enables Instant Rebreak while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> fullbright = group.add(new BoolSetting.Builder()
            .name("fullbright")
            .description("Enables Fullbright while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> killAura = group.add(new BoolSetting.Builder()
            .name("kill-aura")
            .description("Enables Kill Aura while Plane Builder is active.")
            .defaultValue(true)
            .build()
        );

        Setting<Boolean> noRender = group.add(new BoolSetting.Builder()
            .name("performance-no-render")
            .description("Optionally enables safe NoRender performance defaults while Plane Builder is active.")
            .defaultValue(false)
            .build()
        );

        return new CompanionModules(autoTotem, autoEat, velocity, instantRebreak, fullbright, killAura, noRender);
    }

    static BowDefense bowDefense(SettingGroup group) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name("bow-defense")
            .description("Uses guarded Meteor bow modules to shoot visible mobs while Plane Builder is idle or in safe replenish phases.")
            .defaultValue(false)
            .build()
        );

        return new BowDefense(enabled);
    }

    static AutoWalk autoWalk(SettingGroup group) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name("auto-walk")
            .description("Uses Baritone to walk a deterministic 8-block snake route when no nearby plane target is placeable.")
            .defaultValue(false)
            .build()
        );

        Setting<Boolean> autoElytraFly = group.add(new BoolSetting.Builder()
            .name("auto-elytra-fly")
            .description("Uses low auto elytra flight while auto-walking across long solid or hazardous runs.")
            .defaultValue(false)
            .build()
        );

        return new AutoWalk(enabled, autoElytraFly);
    }

    static HoleEscape holeEscape(SettingGroup group) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name("hole-escape")
            .description("Uses safe Baritone pathing to step out when Plane Builder is boxed into a one-block hole.")
            .defaultValue(true)
            .build()
        );

        return new HoleEscape(enabled);
    }

    static Replenish replenish(SettingGroup group) {
        Setting<Boolean> useAvailableSafeInventorySpace = group.add(new BoolSetting.Builder()
            .name("use-available-safe-inventory-space")
            .description("Replenishes loose obsidian to safe available inventory capacity instead of the configured target.")
            .defaultValue(false)
            .build()
        );

        Setting<Integer> targetObsidian = group.add(new IntSetting.Builder()
            .name("replenish-target-obsidian")
            .description("Obsidian count that finishes Plane Builder replenishment. Runtime inventory capacity safely clamps this target.")
            .defaultValue(REPLENISH_TARGET_OBSIDIAN)
            .range(REPLENISH_MIN_OBSIDIAN, REPLENISH_MAX_OBSIDIAN)
            .sliderRange(REPLENISH_MIN_OBSIDIAN, 512)
            .visible(() -> !useAvailableSafeInventorySpace.get())
            .build()
        );

        Setting<Boolean> trashHoleCleanup = group.add(new BoolSetting.Builder()
            .name("trash-hole-cleanup")
            .description("After replenishment, walks to a nearby plane edge and drops conservative trash stacks off the side.")
            .defaultValue(true)
            .build()
        );

        return new Replenish(targetObsidian, useAvailableSafeInventorySpace, trashHoleCleanup);
    }

    static KitbotRefill kitbotRefill(SettingGroup group) {
        return PlaneKitbotRefillSettings.create(group);
    }

    static PickaxeSafety pickaxeSafety(SettingGroup group) {
        Setting<Integer> durabilityThresholdPercent = group.add(new IntSetting.Builder()
            .name("pickaxe-durability-threshold-percent")
            .description("Minimum remaining pickaxe durability percentage required before Plane Builder switches to another non-Silk Touch pickaxe.")
            .defaultValue(PICKAXE_DURABILITY_THRESHOLD_PERCENT)
            .range(1, 100)
            .sliderRange(1, 100)
            .build()
        );

        return new PickaxeSafety(durabilityThresholdPercent);
    }

    static EndermanLookSafety endermanLookSafety(SettingGroup group) {
        Setting<Boolean> enabled = group.add(new BoolSetting.Builder()
            .name("enderman-look-safety")
            .description("Avoids bot-owned rotations that would look at endermen.")
            .defaultValue(true)
            .build()
        );

        Setting<Double> radius = group.add(new DoubleSetting.Builder()
            .name("enderman-look-radius")
            .description("Maximum range to check for endermen before rotating.")
            .defaultValue(ENDERMAN_LOOK_RADIUS)
            .range(1.0, 128.0)
            .sliderRange(1.0, 128.0)
            .build()
        );

        return new EndermanLookSafety(enabled, radius);
    }

    static Block buildBlock() {
        return Blocks.OBSIDIAN;
    }

    record CompanionModules(
        Setting<Boolean> autoTotem,
        Setting<Boolean> autoEat,
        Setting<Boolean> velocity,
        Setting<Boolean> instantRebreak,
        Setting<Boolean> fullbright,
        Setting<Boolean> killAura,
        Setting<Boolean> noRender
    ) {
    }

    record BowDefense(
        Setting<Boolean> enabled
    ) {
    }

    record AutoWalk(
        Setting<Boolean> enabled,
        Setting<Boolean> autoElytraFly
    ) {
    }

    record HoleEscape(
        Setting<Boolean> enabled
    ) {
    }

    record Replenish(
        Setting<Integer> targetObsidian,
        Setting<Boolean> useAvailableSafeInventorySpace,
        Setting<Boolean> trashHoleCleanup
    ) {
    }

    record KitbotRefill(
        Setting<Boolean> enabled,
        Setting<String> nickname,
        Setting<String> kitName,
        Setting<Integer> kitCount
    ) {
    }

    record PickaxeSafety(
        Setting<Integer> durabilityThresholdPercent
    ) implements PlaneInventoryView.PickaxeSafetyConfig {
        @Override
        public int pickaxeDurabilityThresholdPercent() {
            return durabilityThresholdPercent.get();
        }
    }

    record EndermanLookSafety(
        Setting<Boolean> enabled,
        Setting<Double> radius
    ) implements PlaneEndermanLookSafety.Config {
        @Override
        public boolean endermanLookSafetyEnabled() {
            return enabled.get();
        }

        @Override
        public double endermanLookRadius() {
            return radius.get();
        }

    }
}
