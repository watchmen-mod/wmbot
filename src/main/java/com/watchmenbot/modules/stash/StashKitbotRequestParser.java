package com.watchmenbot.modules.stash;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StashKitbotRequestParser {
    private static final String KIT_LIST_COMMAND = "kits";
    private static final Pattern KIT_COMMAND_PREFIX = Pattern.compile("^\\s*kit\\b\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANGLED_WHISPER = Pattern.compile("^\\s*<?(?<sender>[A-Za-z0-9_]{1,16})>?\\s*(?:->|»|whispers?|msgs?|tells?)\\s*(?:to\\s+)?(?:you|me|[A-Za-z0-9_]{1,16})\\s*:?\\s*(?<body>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_WHISPER = Pattern.compile("^\\s*\\[(?<sender>[A-Za-z0-9_]{1,16})\\s*(?:->|»|whispers?|msgs?|tells?)\\s*(?:to\\s+)?(?:you|me|[A-Za-z0-9_]{1,16})]\\s*:?\\s*(?<body>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_WHISPER = Pattern.compile("^\\s*(?:from|\\[from])\\s+(?<sender>[A-Za-z0-9_]{1,16})\\s*:?\\s*(?<body>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_COMMAND = Pattern.compile("^\\s*\"(?<name>[^\"]+)\"\\s+(?<count>\\d+)\\s*$");

    Whisper parseWhisper(String message) {
        for (Pattern pattern : List.of(BRACKET_WHISPER, FROM_WHISPER, ANGLED_WHISPER)) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.matches()) return new Whisper(matcher.group("sender"), matcher.group("body"));
        }

        return null;
    }

    KitCommand parseCommand(String body) {
        return parseCommand(body, false, null);
    }

    KitRequestIntent parseIntent(String body) {
        return KIT_LIST_COMMAND.equalsIgnoreCase(body.trim()) ? KitRequestIntent.listKits() : KitRequestIntent.delivery();
    }

    KitCommand parseCommand(String body, boolean allowCountOnly, String defaultKitName) {
        String trimmed = stripKitCommandPrefix(body.trim());
        Matcher quoted = QUOTED_COMMAND.matcher(trimmed);
        if (quoted.matches()) {
            return new KitCommand(quoted.group("name").trim(), Integer.parseInt(quoted.group("count")), true);
        }

        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace <= 0 || lastSpace == trimmed.length() - 1) {
            if (!allowCountOnly || defaultKitName == null || defaultKitName.isBlank()) return null;

            try {
                return new KitCommand(defaultKitName.trim(), Integer.parseInt(trimmed));
            }
            catch (NumberFormatException ignored) {
                return null;
            }
        }

        String name = trimmed.substring(0, lastSpace).trim();
        String countText = trimmed.substring(lastSpace + 1).trim();
        if (name.isEmpty()) return null;

        try {
            return new KitCommand(name, Integer.parseInt(countText));
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stripKitCommandPrefix(String body) {
        Matcher matcher = KIT_COMMAND_PREFIX.matcher(body);
        return matcher.matches() ? matcher.group(1).trim() : body;
    }

    boolean isAllowed(String sender, String allowedRequesters) {
        String normalizedSender = sender.toLowerCase(Locale.ROOT);
        for (String allowed : allowedRequesters.split(",")) {
            if (allowed.trim().toLowerCase(Locale.ROOT).equals(normalizedSender)) return true;
        }

        return false;
    }
}
