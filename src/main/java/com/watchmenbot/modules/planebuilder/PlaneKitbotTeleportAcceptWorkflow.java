package com.watchmenbot.modules.planebuilder;

final class PlaneKitbotTeleportAcceptWorkflow {
    private static final int REQUEST_ACCEPT_INITIAL_DELAY_TICKS = 40;
    private static final int REQUEST_ACCEPT_RETRY_TICKS = 100;

    private final PlaneKitbotMessenger messenger;

    private boolean promptAccepted;
    private String queuedCommand;
    private AcceptSource queuedSource = AcceptSource.PROMPT;
    private boolean requestAcceptArmed;
    private int requestAcceptTicks;

    PlaneKitbotTeleportAcceptWorkflow(PlaneKitbotMessenger messenger) {
        this.messenger = messenger;
    }

    void reset() {
        promptAccepted = false;
        requestAcceptArmed = false;
        requestAcceptTicks = 0;
        clearQueue();
    }

    void clearQueue() {
        queuedCommand = null;
        queuedSource = AcceptSource.PROMPT;
    }

    AcceptResult handlePrompt(String message) {
        if (!messenger.teleportPromptMatches(message)) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROMPT);
        if (promptAccepted || queuedCommand != null) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROMPT);

        String command = messenger.teleportAcceptCommand(message);
        if (command == null) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, null, AcceptSource.PROMPT);

        queuedCommand = command;
        queuedSource = AcceptSource.PROMPT;
        return new AcceptResult(AcceptStatus.QUEUED, command, AcceptSource.PROMPT);
    }

    boolean queueFromPrompt(String message) {
        return handlePrompt(message).accepted();
    }

    AcceptResult queueConfiguredAcceptForRequest() {
        promptAccepted = false;
        requestAcceptArmed = true;
        requestAcceptTicks = REQUEST_ACCEPT_INITIAL_DELAY_TICKS;
        String command = messenger.teleportAcceptCommand();
        return new AcceptResult(command == null ? AcceptStatus.FAILED_CLIENT_NOT_READY : AcceptStatus.ARMED, command, AcceptSource.PROACTIVE);
    }

    boolean hasQueuedCommand() {
        return queuedCommand != null;
    }

    AcceptResult tick() {
        if (queuedCommand != null) return sendQueuedCommand();
        return tickRequestAccept();
    }

    private AcceptResult sendQueuedCommand() {
        AcceptSource source = queuedSource;
        String command = queuedCommand;
        if (!messenger.sendCommand(command)) return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, command, source);

        if (source == AcceptSource.PROMPT) promptAccepted = true;
        clearQueue();
        return new AcceptResult(AcceptStatus.SENT, command, source);
    }

    private AcceptResult tickRequestAccept() {
        if (!requestAcceptArmed) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROACTIVE);
        if (requestAcceptTicks > 0) {
            requestAcceptTicks--;
            if (requestAcceptTicks > 0) return new AcceptResult(AcceptStatus.IGNORED, null, AcceptSource.PROACTIVE);
        }

        String command = messenger.teleportAcceptCommand();
        requestAcceptTicks = REQUEST_ACCEPT_RETRY_TICKS;
        if (command == null || !messenger.sendCommand(command)) {
            return new AcceptResult(AcceptStatus.FAILED_CLIENT_NOT_READY, command, AcceptSource.PROACTIVE);
        }

        return new AcceptResult(AcceptStatus.SENT, command, AcceptSource.PROACTIVE);
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
        ARMED,
        QUEUED,
        SENT,
        FAILED_CLIENT_NOT_READY
    }

    enum AcceptSource {
        PROMPT,
        PROACTIVE
    }
}
