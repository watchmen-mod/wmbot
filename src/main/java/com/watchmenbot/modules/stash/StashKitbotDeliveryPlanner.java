package com.watchmenbot.modules.stash;

import net.minecraft.util.math.BlockPos;

import java.util.Locale;

final class StashKitbotDeliveryPlanner {
    static final int DELIVERY_STUCK_TICKS = 80;
    private static final double MIN_WATCHDOG_MOVE_SQ = 0.25;
    private static final double MIN_WATCHDOG_DISTANCE_DELTA_SQ = 1.0;

    private StashKitbotDeliveryPlanner() {
    }

    static boolean tpaSendAllowed(KitbotPhase phase, boolean cooldownActive, boolean tpaSent) {
        return phase == KitbotPhase.TPA_REQUEST && !cooldownActive && !tpaSent;
    }

    static TpaRequestDecision tpaRequestDecision(boolean teleportDetected, boolean cooldownActive, boolean tpaSent) {
        if (teleportDetected) return TpaRequestDecision.START_DELIVERY;
        return tpaSendAllowed(KitbotPhase.TPA_REQUEST, cooldownActive, tpaSent)
            ? TpaRequestDecision.SEND_TPA
            : TpaRequestDecision.WAIT;
    }

    static boolean homeSendAllowed(boolean cooldownActive, boolean homeSent, boolean pendingHome, boolean confirmingHome) {
        return !cooldownActive && !homeSent && !pendingHome && !confirmingHome;
    }

    static boolean homeCommandRequiresRespawn(String homeCommand) {
        String command = normalize(homeCommand).trim();
        if (command.isBlank()) return false;
        return command.equals("/kill");
    }

    static HomeConfirmDecision homeConfirmDecision(boolean respawnRequired, boolean confirmExpired, boolean dead, boolean respawnRequested, boolean alive) {
        if (!respawnRequired) return confirmExpired ? HomeConfirmDecision.COMPLETE : HomeConfirmDecision.WAIT;
        if (!respawnRequested && dead) return HomeConfirmDecision.REQUEST_RESPAWN;
        if (respawnRequested && alive) return HomeConfirmDecision.COMPLETE;
        return HomeConfirmDecision.WAIT;
    }

    static boolean cooldownApplies(
        PendingCommand command,
        PendingCommand expectedCommand,
        boolean messengerPendingWindow,
        boolean requestPendingCommand,
        boolean targetMatches,
        KitbotPhase currentPhase,
        KitbotPhase expectedPhase
    ) {
        return command != PendingCommand.NONE
            && command == expectedCommand
            && messengerPendingWindow
            && requestPendingCommand
            && targetMatches
            && currentPhase == expectedPhase;
    }

    static boolean ignoreForCooldownHandling(String message) {
        String lower = normalize(message).trim();
        return lower.isBlank()
            || lower.contains("[meteor]")
            || lower.startsWith("you whisper to ")
            || lower.contains("] you whisper to ");
    }

    static CooldownAttribution cooldownAttribution(
        boolean commandPendingWindow,
        boolean whisperPendingWindow,
        boolean commandSentAfterWhisper
    ) {
        if (commandPendingWindow && (!whisperPendingWindow || commandSentAfterWhisper)) return CooldownAttribution.COMMAND;
        if (whisperPendingWindow) return CooldownAttribution.WHISPER;
        return CooldownAttribution.NONE;
    }

    static boolean pendingCommandExpired(boolean requestPendingCommand, boolean messengerPendingWindow) {
        return requestPendingCommand && !messengerPendingWindow;
    }

    static boolean tpaTimeoutShouldReturn(boolean teleportDetected, boolean teleportWaitExpired) {
        return !teleportDetected && teleportWaitExpired;
    }

    static boolean teleportArrivalDetected(BlockPos preTpaPos, BlockPos currentPos, String preTpaDimension, String currentDimension) {
        return movedFarAfterTpa(preTpaPos, currentPos) || dimensionChangedAfterTpa(preTpaDimension, currentDimension);
    }

    static boolean dimensionChangedAfterTpa(String preTpaDimension, String currentDimension) {
        if (preTpaDimension == null || preTpaDimension.isBlank()) return false;
        if (currentDimension == null || currentDimension.isBlank()) return false;
        return !preTpaDimension.equals(currentDimension);
    }

    static boolean shouldCorrectCrossDimensionFlag(boolean crossDimensionDelivery, String preTpaDimension, String currentDimension) {
        return !crossDimensionDelivery && dimensionChangedAfterTpa(preTpaDimension, currentDimension);
    }

    static int correctedCrossDimensionSettleTicks(int currentSettleTicks, int defaultSettleTicks) {
        return currentSettleTicks <= 0 ? defaultSettleTicks : currentSettleTicks;
    }

    static int effectiveRequesterSearchRadius(int configuredRadius, boolean crossDimensionDelivery) {
        int configured = Math.max(1, configuredRadius);
        return crossDimensionDelivery ? Math.max(configured, 128) : configured;
    }

    static boolean requesterNameMatches(String requesterName, String gameProfileName, String displayName, String entityName) {
        String requester = normalizePlayerName(requesterName);
        if (requester.isBlank()) return false;

        return playerNameCandidateMatches(requester, gameProfileName)
            || playerNameCandidateMatches(requester, displayName)
            || playerNameCandidateMatches(requester, entityName);
    }

    static CrossDimensionDeliveryDecision crossDimensionDeliveryDecision(
        boolean requesterVisible,
        double requesterDistanceSq,
        int deliveryDistance,
        boolean movementExpired,
        boolean directStepExpired
    ) {
        if (!requesterVisible) return CrossDimensionDeliveryDecision.FAIL_HOME;
        if (requesterDistanceSq < StashKitbotDelivery.MIN_THROW_DISTANCE_SQ) {
            return directStepExpired ? CrossDimensionDeliveryDecision.THROW_NOW : CrossDimensionDeliveryDecision.DIRECT_STEP_AWAY;
        }

        double maxThrowDistance = Math.max(8, deliveryDistance * 2);
        if (requesterDistanceSq <= maxThrowDistance * maxThrowDistance) return CrossDimensionDeliveryDecision.THROW_NOW;
        return movementExpired ? CrossDimensionDeliveryDecision.FAIL_HOME : CrossDimensionDeliveryDecision.APPROACH_REQUESTER;
    }

    static boolean shouldKeepReacquiringRequester(boolean requesterFound, boolean reacquireExpired) {
        return !requesterFound && !reacquireExpired;
    }

    static boolean requesterSearchReady(boolean worldReady, boolean playerReady, boolean requesterNamePresent) {
        return worldReady && playerReady && requesterNamePresent;
    }

    static boolean deliveryMovementProgressed(
        double playerMovementSq,
        double requesterDistanceImprovementSq,
        boolean deliverySpotChanged
    ) {
        return playerMovementSq >= MIN_WATCHDOG_MOVE_SQ
            || requesterDistanceImprovementSq >= MIN_WATCHDOG_DISTANCE_DELTA_SQ
            || deliverySpotChanged;
    }

    static DeliveryStuckDecision deliveryStuckDecision(
        boolean progressed,
        int stuckTicks,
        int stuckThresholdTicks,
        int recoveryAttempts,
        boolean requesterVisible
    ) {
        if (progressed) return DeliveryStuckDecision.TRACK;
        if (stuckTicks < Math.max(1, stuckThresholdTicks)) return DeliveryStuckDecision.TRACK;
        if (recoveryAttempts <= 0) return DeliveryStuckDecision.RESET_MOVEMENT;
        return requesterVisible ? DeliveryStuckDecision.THROW_NOW : DeliveryStuckDecision.REACQUIRE_REQUESTER;
    }

    static DeliveryPositionDecision deliveryPositionDecision(
        boolean requesterVisible,
        double requesterDistanceSq,
        double spotDistanceSq,
        int deliveryDistance,
        boolean positioningExpired,
        boolean movementExpired,
        boolean directStepExpired
    ) {
        if (!requesterVisible) return DeliveryPositionDecision.FAIL_HOME;
        if (readyToThrow(requesterDistanceSq, spotDistanceSq, deliveryDistance)) return DeliveryPositionDecision.THROW_NOW;
        if (directStepExpired) return DeliveryPositionDecision.THROW_NOW;
        if (!movementExpired && shouldStepAwayDirectly(requesterDistanceSq)) return DeliveryPositionDecision.DIRECT_STEP_AWAY;
        if (positioningExpired && requesterDistanceSq >= StashKitbotDelivery.MIN_THROW_DISTANCE_SQ) return DeliveryPositionDecision.THROW_NOW;
        if (movementExpired) return DeliveryPositionDecision.THROW_NOW;
        return DeliveryPositionDecision.PATH_TO_SPOT;
    }

    static boolean tpaCooldownShouldApply(boolean teleportDetected, boolean cooldownApplies) {
        return !teleportDetected && cooldownApplies;
    }

    static boolean tpaCooldownMayRearm(KitbotPhase phase, boolean tpaSent) {
        return phase == KitbotPhase.WAITING_FOR_TPY && tpaSent;
    }

    static boolean tpaRequestAccepted(String message, String requester) {
        String lower = normalize(message);
        String player = normalize(requester);
        if (lower.isBlank() || player.isBlank() || !lower.contains(player)) return false;

        return lower.contains("request sent to")
            || lower.contains("teleport request sent")
            || lower.contains("tpa request sent")
            || lower.contains("sent teleport request")
            || lower.contains("sent a teleport request");
    }

    static PendingCooldownDecision pendingCooldownDecision(boolean accepted, boolean teleportArrived, int graceTicks) {
        if (accepted || teleportArrived) return PendingCooldownDecision.DISCARD;
        return graceTicks <= 0 ? PendingCooldownDecision.APPLY : PendingCooldownDecision.WAIT;
    }

    private static boolean movedFarAfterTpa(BlockPos preTpaPos, BlockPos currentPos) {
        return preTpaPos == null || currentPos == null || currentPos.getSquaredDistance(preTpaPos) >= StashKitbotDelivery.TELEPORT_DISTANCE_SQ;
    }

    private static boolean readyToThrow(double requesterDistanceSq, double spotDistanceSq, int deliveryDistance) {
        double effectiveDistance = Math.max(5, deliveryDistance);
        double preferredDistanceSq = effectiveDistance * effectiveDistance * 0.8;
        return requesterDistanceSq >= StashKitbotDelivery.MIN_THROW_DISTANCE_SQ
            && (requesterDistanceSq >= preferredDistanceSq || spotDistanceSq <= 9);
    }

    private static boolean shouldStepAwayDirectly(double requesterDistanceSq) {
        return requesterDistanceSq < StashKitbotDelivery.DIRECT_STEP_DISTANCE_SQ;
    }

    private static boolean playerNameCandidateMatches(String requester, String candidate) {
        String normalized = normalizePlayerName(candidate);
        if (normalized.isBlank()) return false;
        if (normalized.equals(requester)) return true;

        for (String token : normalized.split("\\s+")) {
            if (token.equals(requester)) return true;
        }

        return false;
    }

    private static String normalizePlayerName(String value) {
        return normalize(value).replaceAll("[^a-z0-9_]+", " ").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    enum TpaRequestDecision {
        START_DELIVERY,
        SEND_TPA,
        WAIT
    }

    enum DeliveryPositionDecision {
        PATH_TO_SPOT,
        DIRECT_STEP_AWAY,
        THROW_NOW,
        FAIL_HOME
    }

    enum CrossDimensionDeliveryDecision {
        APPROACH_REQUESTER,
        DIRECT_STEP_AWAY,
        THROW_NOW,
        FAIL_HOME
    }

    enum DeliveryStuckDecision {
        TRACK,
        RESET_MOVEMENT,
        THROW_NOW,
        REACQUIRE_REQUESTER
    }

    enum CooldownAttribution {
        COMMAND,
        WHISPER,
        NONE
    }

    enum PendingCooldownDecision {
        WAIT,
        APPLY,
        DISCARD
    }

    enum HomeConfirmDecision {
        WAIT,
        REQUEST_RESPAWN,
        COMPLETE
    }
}
