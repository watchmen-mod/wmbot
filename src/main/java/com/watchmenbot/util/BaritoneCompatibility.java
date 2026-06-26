package com.watchmenbot.util;

import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneCompatibility {
    private static final String METEOR_BARITONE_MOD_ID = "baritone-meteor";
    private static final String NORMAL_BARITONE_MOD_ID = "baritone";
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";

    private BaritoneCompatibility() {
    }

    public static boolean available() {
        FabricLoader loader = FabricLoader.getInstance();
        return (loader.isModLoaded(METEOR_BARITONE_MOD_ID) || loader.isModLoaded(NORMAL_BARITONE_MOD_ID))
            && hasApiClass();
    }

    public static String missingMessage() {
        return "Install either Baritone for Meteor or normal Baritone for this Minecraft version to use Baritone pathing features.";
    }

    public static void requireAvailable() {
        if (!available()) throw new IllegalStateException(missingMessage());
    }

    private static boolean hasApiClass() {
        try {
            Class.forName(BARITONE_API_CLASS, false, BaritoneCompatibility.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
