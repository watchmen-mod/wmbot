package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotTeleportAcceptWorkflow {
    private static final int PROMPT_ACCEPT_INITIAL_DELAY_TICKS = 40;
    private static final int LEGACY_FALLBACK_ACCEPT_DELAY_TICKS = 100;

    private final PlaneKitbotMessenger messenger;

    private boolean promptAccepted;
    private String queuedCommand;
    private AcceptSource queuedSource = AcceptSource.PROMPT;
    private int queuedCommandTicks;
    private boolean legacyFallbackArmed;
    private boolean legacyFallbackSent;
    private int legacyFallbackTicks;

    PlaneKitbotTeleportAcceptWorkflow(PlaneKitbotMessenger messenger) {
        this.messenger = messenger;
    }

    void reset() {
        promptAccepted = false;
        clearQueue();
        clearLegacyFallback();
    }

    void clearQueue() {
        queuedCommand = null;
        queuedSource = AcceptSource.PROMPT;
        queuedCommandTicks = 0;
    }

    AcceptResult queueConfiguredAcceptFromKitbotTpa() {
        if (promptAccepted || queuedCommand != null) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.KITBOT_TPA);

        String command = messenger.teleportAcceptCommand();
        if (command == null) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, null, AcceptSource.KITBOT_TPA);

        armLegacyFallback();
        return new AcceptResult(AcceptStatus.WAITING_FOR_PROMPT, command, AcceptSource.KITBOT_TPA);
    }

    AcceptResult armLegacyFallbackAfterActiveRequest() {
        if (promptAccepted || queuedCommand != null || legacyFallbackArmed || legacyFallbackSent) {
            return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.LEGACY_FALLBACK);
        }

        String command = messenger.teleportAcceptCommand();
        if (command == null) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, null, AcceptSource.LEGACY_FALLBACK);

        armLegacyFallback();
        return new AcceptResult(AcceptStatus.IGNORED, command, AcceptSource.LEGACY_FALLBACK);
    }

    AcceptResult clearAfterDeliveryFailure() {
        promptAccepted = true;
        clearQueue();
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.DELIVERY_FAILED, messenger.teleportAcceptCommand(), AcceptSource.DELIVERY);
    }

    AcceptResult clearAfterDeliveryFinished() {
        promptAccepted = true;
        clearQueue();
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.DELIVERY_FINISHED, messenger.teleportAcceptCommand(), AcceptSource.DELIVERY);
    }

    AcceptResult handleMessage(String message) {
        if (messenger.teleportAcceptConfirmed(message)) {
            promptAccepted = true;
            clearQueue();
            clearLegacyFallback();
            return new AcceptResult(AcceptStatus.CONFIRMED, messenger.teleportAcceptCommand(), AcceptSource.PROMPT);
        }

        if (messenger.teleportRequestGone(message)) {
            promptAccepted = true;
            clearQueue();
            clearLegacyFallback();
            return new AcceptResult(AcceptStatus.REQUEST_GONE, messenger.teleportAcceptCommand(), AcceptSource.PROMPT);
        }

        if (!messenger.teleportPromptMatches(message)) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROMPT);
        if (promptAccepted || queuedCommand != null) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROMPT);

        String command = messenger.teleportAcceptCommand(message);
        if (command == null) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, null, AcceptSource.PROMPT);

        queuedCommand = command;
        queuedSource = AcceptSource.PROMPT;
        queuedCommandTicks = PROMPT_ACCEPT_INITIAL_DELAY_TICKS;
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.QUEUED, command, AcceptSource.PROMPT);
    }

    boolean queueFromPrompt(String message) {
        return handleMessage(message).accepted();
    }

    AcceptResult queueConfiguredAcceptForRequest() {
        promptAccepted = false;
        legacyFallbackSent = false;
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.WAITING_FOR_PROMPT, messenger.teleportAcceptCommand(), AcceptSource.PROMPT);
    }

    boolean hasQueuedCommand() {
        return queuedCommand != null;
    }

    AcceptResult tick() {
        if (queuedCommand != null) return sendQueuedCommand();
        if (legacyFallbackArmed) return tickLegacyFallback();
        return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROMPT);
    }

    private AcceptResult sendQueuedCommand() {
        if (queuedCommandTicks > 0) {
            queuedCommandTicks--;
            return new AcceptResult(AcceptStatus.IGNORED, queuedCommand, AcceptSource.PROMPT);
        }

        AcceptSource source = queuedSource;
        String command = queuedCommand;
        if (!messenger.sendCommand(command)) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, command, source);

        promptAccepted = true;
        clearQueue();
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.SENT, command, source);
    }

    private void armLegacyFallback() {
        legacyFallbackArmed = true;
        legacyFallbackTicks = LEGACY_FALLBACK_ACCEPT_DELAY_TICKS;
    }

    private void clearLegacyFallback() {
        legacyFallbackArmed = false;
        legacyFallbackTicks = 0;
    }

    private AcceptResult tickLegacyFallback() {
        if (legacyFallbackTicks > 0) {
            legacyFallbackTicks--;
            return new AcceptResult(AcceptStatus.IGNORED, messenger.teleportAcceptCommand(), AcceptSource.LEGACY_FALLBACK);
        }

        String command = messenger.teleportAcceptCommand();
        if (!messenger.sendCommand(command)) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, command, AcceptSource.LEGACY_FALLBACK);

        promptAccepted = true;
        legacyFallbackSent = true;
        clearLegacyFallback();
        return new AcceptResult(AcceptStatus.SENT, command, AcceptSource.LEGACY_FALLBACK);
    }

    record AcceptResult(AcceptStatus status, String command, AcceptSource source) {
        AcceptResult(AcceptStatus status, String command) {
            this(status, command, AcceptSource.PROMPT);
        }

        boolean accepted() {
            return status != AcceptStatus.IGNORED;
        }
    }

    enum AcceptStatus {
        IGNORED,
        WAITING_FOR_PROMPT,
        QUEUED,
        SENT,
        CONFIRMED,
        REQUEST_GONE,
        DELIVERY_FAILED,
        DELIVERY_FINISHED,
        FAILED_CLIENT_NOT_READY
    }

    enum AcceptSource {
        PROMPT,
        KITBOT_TPA,
        LEGACY_FALLBACK,
        DELIVERY
    }
}
