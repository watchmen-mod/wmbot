package com.watchmenbot.modules.stash;

final class StashKitbotReturnCommand {
    static final String DEFAULT_HOME_COMMAND = "/home stash";
    static final String DEFAULT_KILL_COMMAND = "/kill";

    private StashKitbotReturnCommand() {
    }

    static String command(ReturnMethod method, String homeCommand, String customCommand) {
        ReturnMethod safeMethod = method == null ? ReturnMethod.KILL : method;
        return switch (safeMethod) {
            case KILL -> DEFAULT_KILL_COMMAND;
            case HOME -> blankToDefault(homeCommand, DEFAULT_HOME_COMMAND);
            case CUSTOM -> blankToDefault(customCommand, DEFAULT_KILL_COMMAND);
        };
    }

    private static String blankToDefault(String command, String fallback) {
        return command == null || command.isBlank() ? fallback : command.trim();
    }

    enum ReturnMethod {
        KILL,
        HOME,
        CUSTOM
    }
}
