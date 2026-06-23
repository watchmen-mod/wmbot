package com.watchmenbot.modules.stash;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StashKitbotDelivery {
    private static final Pattern COMPACT_DURATION = Pattern.compile("(?:(?<minutes>\\d+)\\s*m(?:in(?:ute)?s?)?\\s*)?(?:(?<seconds>\\d+)\\s*s(?:ec(?:ond)?s?)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_DURATION = Pattern.compile("(?:(?<minutes>\\d+)\\s*minutes?\\s*)?(?:(?<seconds>\\d+)\\s*seconds?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK_DURATION = Pattern.compile("(?:(?<hours>\\d{1,2}):)?(?<minutes>\\d{1,2}):(?<seconds>\\d{2})");
    static final double TELEPORT_DISTANCE_SQ = 64.0;
    static final double MIN_THROW_DISTANCE_SQ = 16.0;
    static final double DIRECT_STEP_DISTANCE_SQ = 36.0;

    private final MinecraftClient mc;

    StashKitbotDelivery(MinecraftClient mc) {
        this.mc = mc;
    }

    boolean hasTeleportArrived(BlockPos preTpaPos, String preTpaDimension) {
        return StashKitbotDeliveryPlanner.teleportArrivalDetected(
            preTpaPos,
            mc.player == null ? null : mc.player.getBlockPos(),
            preTpaDimension,
            StashClientUtils.dimensionId(mc)
        );
    }

    boolean isRequesterNearby(AbstractClientPlayerEntity requester, int requesterSearchRadius) {
        double searchSq = requesterSearchRadius * requesterSearchRadius;
        return requester != null && mc.player != null && horizontalDistanceSq(requester) <= searchSq;
    }

    AbstractClientPlayerEntity findRequester(String requesterName, int requesterSearchRadius) {
        if (mc == null || !StashKitbotDeliveryPlanner.requesterSearchReady(mc.world != null, mc.player != null, requesterName != null)) return null;

        AbstractClientPlayerEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!matchesRequester(player, requesterName)) continue;

            double distanceSq = horizontalDistanceSq(player);
            if (distanceSq < nearestSq) {
                nearest = player;
                nearestSq = distanceSq;
            }
        }

        return nearest;
    }

    double horizontalDistanceSq(AbstractClientPlayerEntity requester) {
        if (mc == null || mc.player == null || requester == null) return Double.MAX_VALUE;

        double dx = mc.player.getX() - requester.getX();
        double dz = mc.player.getZ() - requester.getZ();
        return dx * dx + dz * dz;
    }

    String loadedPlayerDebug(AbstractClientPlayerEntity requester) {
        if (mc == null || mc.world == null) return "world-not-ready";

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (count > 0) builder.append(", ");
            builder.append(player.getGameProfile().getName());
            count++;
        }

        double distance = requester == null ? -1.0 : Math.sqrt(horizontalDistanceSq(requester));
        return "players=%d [%s], requesterDistance=%.1f".formatted(count, builder, distance);
    }

    BlockPos deliverySpot(AbstractClientPlayerEntity requester, int deliveryDistance) {
        Vec3d requesterPos = requester.getPos();
        Vec3d botPos = mc.player.getPos();
        Vec3d away = botPos.subtract(requesterPos);
        if (away.horizontalLengthSquared() < 0.25) away = new Vec3d(1.0, 0.0, 0.0);
        away = new Vec3d(away.x, 0.0, away.z).normalize();

        Vec3d spot = requesterPos.add(away.multiply(effectiveDeliveryDistance(deliveryDistance)));
        return BlockPos.ofFloored(spot.x, mc.player.getY(), spot.z).toImmutable();
    }

    void walkAwayFrom(AbstractClientPlayerEntity requester) {
        Vec3d requesterPos = requester.getPos();
        Vec3d botPos = mc.player.getPos();
        double dx = botPos.x - requesterPos.x;
        double dz = botPos.z - requesterPos.z;
        if (dx * dx + dz * dz < 0.0001) {
            dx = 1.0;
            dz = 0.0;
        }

        mc.player.setYaw((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        setWalkingKeys(true);
    }

    void walkToward(AbstractClientPlayerEntity requester) {
        Vec3d requesterPos = requester.getPos();
        Vec3d botPos = mc.player.getPos();
        double dx = requesterPos.x - botPos.x;
        double dz = requesterPos.z - botPos.z;
        if (dx * dx + dz * dz < 0.0001) {
            dx = 1.0;
            dz = 0.0;
        }

        mc.player.setYaw((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        setWalkingKeys(true);
    }

    void stopWalking() {
        setWalkingKeys(false);
    }

    int effectiveDeliveryDistance(int deliveryDistance) {
        return Math.max(5, deliveryDistance);
    }

    boolean readyToThrow(double requesterDistanceSq, double spotDistanceSq, int deliveryDistance) {
        double effectiveDistance = effectiveDeliveryDistance(deliveryDistance);
        double preferredDistanceSq = effectiveDistance * effectiveDistance * 0.8;
        return requesterDistanceSq >= MIN_THROW_DISTANCE_SQ
            && (requesterDistanceSq >= preferredDistanceSq || spotDistanceSq <= 9);
    }

    boolean shouldStepAwayDirectly(double requesterDistanceSq) {
        return requesterDistanceSq < DIRECT_STEP_DISTANCE_SQ;
    }

    void aimLowAt(AbstractClientPlayerEntity requester) {
        Vec3d target = requester.getPos().add(0.0, 0.35, 0.0);
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        mc.player.setYaw((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setPitch((float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, horizontal))));
    }

    void lookDown(int deliveryLookPitch) {
        if (mc.player == null) return;
        mc.player.setPitch(Math.min(90.0f, Math.max(45.0f, deliveryLookPitch)));
    }

    private void setWalkingKeys(boolean pressed) {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(pressed);
        mc.options.sprintKey.setPressed(pressed);
    }

    boolean looksLikeCooldown(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("teleporting") || lower.contains("teleportation will commence") || lower.contains("do not move")) {
            return false;
        }

        return lower.contains("cooldown")
            || lower.contains("wait")
            || lower.contains("try again")
            || lower.contains("available in")
            || lower.contains("use this command again")
            || lower.contains("before using");
    }

    int parseCooldownTicks(String message) {
        Matcher clock = CLOCK_DURATION.matcher(message);
        if (clock.find()) {
            int hours = parseGroup(clock, "hours");
            int minutes = parseGroup(clock, "minutes");
            int seconds = parseGroup(clock, "seconds");
            return Math.max(0, (hours * 3600 + minutes * 60 + seconds) * 20);
        }

        int compact = parseDurationPattern(COMPACT_DURATION, message);
        if (compact > 0) return compact;

        return parseDurationPattern(WORD_DURATION, message);
    }

    String formatDuration(int ticks) {
        int totalSeconds = Math.max(1, (int) Math.ceil(ticks / 20.0));
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes <= 0) return seconds + "s";
        if (seconds <= 0) return minutes + "m";
        return minutes + "m " + seconds + "s";
    }

    String botName() {
        if (mc.player == null || mc.player.getGameProfile() == null) return "the bot";
        return mc.player.getGameProfile().getName();
    }

    private boolean matchesRequester(AbstractClientPlayerEntity player, String requesterName) {
        String profileName = player.getGameProfile() == null ? null : player.getGameProfile().getName();
        String displayName = player.getDisplayName() == null ? null : player.getDisplayName().getString();
        String entityName = player.getName() == null ? null : player.getName().getString();
        return StashKitbotDeliveryPlanner.requesterNameMatches(requesterName, profileName, displayName, entityName);
    }

    private int parseDurationPattern(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            int minutes = parseGroup(matcher, "minutes");
            int seconds = parseGroup(matcher, "seconds");
            if (minutes > 0 || seconds > 0) return (minutes * 60 + seconds) * 20;
        }

        return 0;
    }

    private int parseGroup(Matcher matcher, String name) {
        try {
            String value = matcher.group(name);
            return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
        }
        catch (IllegalArgumentException ignored) {
            return 0;
        }
    }
}
