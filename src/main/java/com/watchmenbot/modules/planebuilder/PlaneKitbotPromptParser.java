package com.watchmenbot.modules.planebuilder;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlaneKitbotPromptParser {
    private static final String PLAYER_NAME = "([A-Za-z0-9_]{1,16})";
    private static final Pattern TYPE_ACCEPT = Pattern.compile(
        "\\btype\\s+/(?:tpy|tpaccept)\\s+" + PLAYER_NAME + "\\b.*\\baccept\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DIRECT_REQUEST = Pattern.compile(
        "\\b" + PLAYER_NAME + "\\s+(?:wants to teleport(?: to you)?|has requested to teleport(?: to you)?|requested to teleport(?: to you)?|requests to teleport(?: to you)?|is requesting to teleport(?: to you)?|sent you a /?tpa request|has sent you a /?tpa request|sent you a teleport request|has sent you a teleport request|sent a teleport request to you)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REQUEST_FROM = Pattern.compile(
        "\\b(?:teleport|tpa) request from:?\\s+" + PLAYER_NAME + "\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REQUEST_BY = Pattern.compile(
        "\\b(?:teleport|tpa) request (?:sent )?by:?\\s+" + PLAYER_NAME + "\\b",
        Pattern.CASE_INSENSITIVE
    );

    private PlaneKitbotPromptParser() {
    }

    static boolean teleportPromptMatches(String message, String nickname) {
        String cleanNickname = PlaneKitbotRefillDecisions.clean(nickname);
        if (message == null || cleanNickname.isEmpty()) return false;

        String lower = message.toLowerCase(Locale.ROOT);
        String lowerNickname = cleanNickname.toLowerCase(Locale.ROOT);
        if (!lower.contains(lowerNickname)) return false;

        String requester = teleportPromptRequester(message);
        if (requester != null) return requester.equalsIgnoreCase(cleanNickname);

        return lower.contains(lowerNickname + " wants to teleport")
            || lower.contains(lowerNickname + " has requested to teleport")
            || lower.contains(lowerNickname + " requested to teleport")
            || lower.contains(lowerNickname + " requests to teleport")
            || lower.contains(lowerNickname + " is requesting to teleport")
            || lower.contains("teleport request from " + lowerNickname)
            || lower.contains("teleport request from: " + lowerNickname)
            || lower.contains("tpa request from " + lowerNickname)
            || lower.contains("tpa request from: " + lowerNickname)
            || lower.contains("teleport request by " + lowerNickname)
            || lower.contains("tpa request by " + lowerNickname)
            || lower.contains(lowerNickname + " sent you a /tpa request")
            || lower.contains(lowerNickname + " sent you a tpa request")
            || lower.contains(lowerNickname + " sent you a teleport request")
            || (lower.contains("type /tpy " + lowerNickname) && lower.contains(" to accept"))
            || (lower.contains("type /tpaccept " + lowerNickname) && lower.contains(" to accept"))
            || (lower.contains("/tpa") && lower.contains("accept"));
    }

    static String teleportPromptRequester(String message) {
        if (message == null) return null;

        String requester = find(TYPE_ACCEPT, message);
        if (requester != null) return requester;

        requester = find(DIRECT_REQUEST, message);
        if (requester != null) return requester;

        requester = find(REQUEST_FROM, message);
        if (requester != null) return requester;

        return find(REQUEST_BY, message);
    }

    private static String find(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
