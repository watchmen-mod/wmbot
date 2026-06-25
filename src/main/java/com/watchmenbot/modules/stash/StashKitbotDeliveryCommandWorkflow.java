package com.watchmenbot.modules.stash;

final class StashKitbotDeliveryCommandWorkflow {
    private static final int HOME_CONFIRM_TICKS = 80;
    private static final int TPA_COOLDOWN_GRACE_TICKS = 20;

    private final StashKitbotSession session;
    private final StashKitbotMessenger messenger;
    private final StashKitbotDelivery delivery;
    private final StashKitbotEvents events;
    private final TeleportCallbacks teleportCallbacks;

    StashKitbotDeliveryCommandWorkflow(
        StashKitbotSession session,
        StashKitbotMessenger messenger,
        StashKitbotDelivery delivery,
        StashKitbotEvents events,
        TeleportCallbacks teleportCallbacks
    ) {
        this.session = session;
        this.messenger = messenger;
        this.delivery = delivery;
        this.events = events;
        this.teleportCallbacks = teleportCallbacks;
    }

    void handleCooldownMessage(String message, StashKitbotDeliveryWorkflow.Settings settings) {
        if (StashKitbotDeliveryPlanner.ignoreForCooldownHandling(message)) return;

        KitRequest request = session.activeRequest();
        if (request != null && StashKitbotDeliveryPlanner.tpaRequestAccepted(message, request.requester)) {
            handleTpaAccepted(request);
            return;
        }

        if (!delivery.looksLikeCooldown(message)) return;

        boolean commandPendingWindow = request != null
            && request.delivery.commands.hasPending()
            && messenger.hasPendingCommandWindow();
        StashKitbotDeliveryPlanner.CooldownAttribution attribution = StashKitbotDeliveryPlanner.cooldownAttribution(
            commandPendingWindow,
            messenger.hasPendingWhisperWindow(),
            messenger.pendingCommandSentAfterWhisper()
        );

        if (attribution == StashKitbotDeliveryPlanner.CooldownAttribution.COMMAND) {
            if (request.delivery.commands.pendingCommand == PendingCommand.TPA && teleportCallbacks.tryStartDeliveryAfterTeleport(settings)) return;
            if (handlePendingCommandCooldown(request, message, settings)) return;
            ignoreStaleCommandCooldown(request, message);
            return;
        }

        if (attribution == StashKitbotDeliveryPlanner.CooldownAttribution.WHISPER) {
            int cooldownTicks = cooldownTicks(message, settings);

            PendingWhisper pendingWhisper = messenger.pendingWhisper();
            messenger.applyWhisperCooldown(cooldownTicks);
            events.warning("Whisper command is cooling down for about %s. Queued whisper to %s.", delivery.formatDuration(cooldownTicks), pendingWhisper.player());
        }
    }

    void tickPendingCommandCooldown(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        if (request == null || !request.delivery.pendingCommandCooldown.active()) return;

        if (request.delivery.pendingCommandCooldown.command == PendingCommand.TPA && teleportCallbacks.tryStartDeliveryAfterTeleport(settings)) {
            request.delivery.pendingCommandCooldown.clear();
            return;
        }

        StashKitbotDeliveryPlanner.PendingCooldownDecision decision = StashKitbotDeliveryPlanner.pendingCooldownDecision(
            false,
            false,
            --request.delivery.pendingCommandCooldown.graceTicks
        );
        if (decision == StashKitbotDeliveryPlanner.PendingCooldownDecision.WAIT) return;
        if (decision == StashKitbotDeliveryPlanner.PendingCooldownDecision.DISCARD) {
            request.delivery.pendingCommandCooldown.clear();
            return;
        }

        applyPendingCommandCooldown(request, settings);
    }

    void expirePendingCommandWindow() {
        KitRequest request = session.activeRequest();
        if (request == null) return;

        DeliveryCommandState commands = request.delivery.commands;
        if (!StashKitbotDeliveryPlanner.pendingCommandExpired(commands.hasPending(), messenger.hasPendingCommandWindow())) return;

        events.info("Delivery command %s attempt %d left cooldown-listen window in phase %s.",
            commands.pendingCommand,
            commands.pendingAttemptId,
            session.phase()
        );
        commands.clearPending();
    }

    void clearPendingCommandIf(PendingCommand command) {
        KitRequest request = session.activeRequest();
        if (request == null || request.delivery.commands.pendingCommand != command) return;

        request.delivery.commands.clearPending();
        if (messenger.pendingCommand() == command) messenger.clearPendingCommand();
    }

    void finishTpaAttempt(KitRequest request) {
        if (request == null) return;

        DeliveryCommandState commands = request.delivery.commands;
        if (commands.pendingCommand == PendingCommand.TPA) commands.clearPending();
        if (messenger.pendingCommand() == PendingCommand.TPA) messenger.clearPendingCommand();
        commands.tpaSent = true;
    }

    void requestHome(StashKitbotDeliveryWorkflow.Settings settings) {
        KitRequest request = session.activeRequest();
        DeliveryCommandState commands = request.delivery.commands;
        boolean pendingHome = commands.pendingCommand == PendingCommand.HOME && messenger.hasPendingCommandWindow();
        boolean confirmingHome = session.phase() == KitbotPhase.HOME_CONFIRM;
        boolean cooldownActive = false;
        if (!StashKitbotDeliveryPlanner.homeSendAllowed(cooldownActive, commands.homeSent, pendingHome, confirmingHome)) {
            if (!cooldownActive && (commands.homeSent || pendingHome || confirmingHome)) {
                session.phase(KitbotPhase.HOME_CONFIRM);
                request.cooldown.homeConfirm.reset(HOME_CONFIRM_TICKS);
            }
            return;
        }

        int attemptId = commands.beginPending(PendingCommand.HOME, "home", KitbotPhase.HOME_CONFIRM);
        request.delivery.homeRespawnRequired = StashKitbotDeliveryPlanner.homeCommandRequiresRespawn(settings.homeCommand());
        request.delivery.homeRespawnRequested = false;
        if (!messenger.sendHomeCommand(settings.homeCommand())) {
            commands.clearPending();
            commands.homeSent = false;
            request.delivery.homeRespawnRequired = false;
            request.delivery.homeRespawnRequested = false;
            teleportCallbacks.postTeleportFailure("Could not send home command. Delivery is paused with the kits.");
            return;
        }

        commands.markSent(PendingCommand.HOME);
        events.info("Sent home command attempt %d after stash kit delivery.", attemptId);
        session.phase(KitbotPhase.HOME_CONFIRM);
        request.cooldown.homeConfirm.reset(HOME_CONFIRM_TICKS);
    }

    private int cooldownTicks(String message, StashKitbotDeliveryWorkflow.Settings settings) {
        int cooldownTicks = delivery.parseCooldownTicks(message);
        return cooldownTicks <= 0 ? settings.fallbackCooldownTicks() : cooldownTicks;
    }

    private boolean handlePendingCommandCooldown(KitRequest request, String message, StashKitbotDeliveryWorkflow.Settings settings) {
        DeliveryCommandState commands = request.delivery.commands;
        PendingCommand pending = commands.pendingCommand;
        boolean applies = StashKitbotDeliveryPlanner.cooldownApplies(
            pending,
            pending,
            messenger.hasPendingCommandWindow() && messenger.pendingCommand() == pending,
            commands.hasPending(),
            pendingCommandTargetMatches(request, commands),
            session.phase(),
            commands.expectedCooldownPhase
        );
        if (!applies) return false;

        int cooldownTicks = cooldownTicks(message, settings);
        int attemptId = commands.pendingAttemptId;
        if (pending == PendingCommand.TPA) {
            if (!StashKitbotDeliveryPlanner.tpaCooldownMayRearm(session.phase(), commands.tpaSent)) {
                return false;
            }

            request.delivery.pendingCommandCooldown.start(PendingCommand.TPA, attemptId, cooldownTicks, TPA_COOLDOWN_GRACE_TICKS, message);
            events.info("Queued possible TPA cooldown for attempt %d: %s. Waiting briefly for server acceptance.", attemptId, delivery.formatDuration(cooldownTicks));
            return true;
        }

        if (pending == PendingCommand.HOME) {
            request.cooldown.homeCooldown.reset(cooldownTicks);
            commands.markCooldown(PendingCommand.HOME);
            messenger.clearPendingCommand();
            session.phase(KitbotPhase.HOME_COOLDOWN);
            events.info("Accepted home cooldown for attempt %d: %s.", attemptId, delivery.formatDuration(cooldownTicks));
            events.reply(request.requester, "Delivery is handled, but my home command is cooling down for about %s. I will retry.".formatted(delivery.formatDuration(cooldownTicks)));
            return true;
        }

        return false;
    }

    private void applyPendingCommandCooldown(KitRequest request, StashKitbotDeliveryWorkflow.Settings settings) {
        PendingCommandCooldown pendingCooldown = request.delivery.pendingCommandCooldown;
        if (!pendingCooldown.active()) return;

        if (pendingCooldown.command == PendingCommand.TPA) {
            request.cooldown.tpaCooldown.reset(pendingCooldown.cooldownTicks);
            request.delivery.teleportWait.reset(settings.teleportTimeoutTicks());
            request.delivery.commands.markCooldown(PendingCommand.TPA);
            messenger.clearPendingCommand();
            session.phase(KitbotPhase.TPA_REQUEST);
            events.info("Confirmed TPA cooldown for attempt %d: %s.", pendingCooldown.attemptId, delivery.formatDuration(pendingCooldown.cooldownTicks));
            events.reply(request.requester, "TPA is cooling down for about %s. I will retry then.".formatted(delivery.formatDuration(pendingCooldown.cooldownTicks)));
            pendingCooldown.clear();
        }
    }

    private void handleTpaAccepted(KitRequest request) {
        request.delivery.pendingCommandCooldown.clear();
        request.delivery.commands.tpaSent = true;
        clearPendingCommandIf(PendingCommand.TPA);
        if (session.phase() == KitbotPhase.TPA_REQUEST) session.phase(KitbotPhase.WAITING_FOR_TPY);
        events.info("TPA request accepted by server for %s.", request.requester);
    }

    private void ignoreStaleCommandCooldown(KitRequest request, String message) {
        DeliveryCommandState commands = request.delivery.commands;
        events.warning("Ignored stale %s cooldown while in %s for %s: %s", commands.pendingCommand, session.phase(), request.requester, message);
        commands.clearPending();
        messenger.clearPendingCommand();
    }

    private boolean pendingCommandTargetMatches(KitRequest request, DeliveryCommandState commands) {
        if (commands.pendingCommand == PendingCommand.TPA) return request.requester.equals(commands.pendingTarget);
        if (commands.pendingCommand == PendingCommand.HOME) return "home".equals(commands.pendingTarget);
        return false;
    }

    interface TeleportCallbacks {
        boolean tryStartDeliveryAfterTeleport(StashKitbotDeliveryWorkflow.Settings settings);

        void postTeleportFailure(String message);
    }
}
