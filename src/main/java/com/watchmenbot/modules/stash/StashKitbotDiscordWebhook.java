package com.watchmenbot.modules.stash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

final class StashKitbotDiscordWebhook {
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    CompletableFuture<Boolean> send(String webhookUrl, KitRequest request) {
        String url = webhookUrl == null ? "" : webhookUrl.trim();
        if (url.isEmpty()) return CompletableFuture.completedFuture(false);

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(request, Instant.now())))
                .build();
        }
        catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.discarding())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300);
    }

    static String payload(KitRequest request, Instant timestamp) {
        String title = "Kit delivered";
        String description = "%d `%s` shulker%s delivered to `%s`.".formatted(
            request.delivery.delivered,
            escapeMarkdown(request.kitName),
            request.delivery.delivered == 1 ? "" : "s",
            escapeMarkdown(request.requester)
        );

        return """
            {
              "username": "WMBot Kitbot",
              "embeds": [
                {
                  "title": "%s",
                  "description": "%s",
                  "color": 5763719,
                  "fields": [
                    {"name": "Requester", "value": "%s", "inline": true},
                    {"name": "Kit", "value": "%s", "inline": true},
                    {"name": "Delivered", "value": "%d/%d", "inline": true}
                  ],
                  "timestamp": "%s"
                }
              ]
            }
            """.formatted(
            escapeJson(title),
            escapeJson(description),
            escapeJson(request.requester),
            escapeJson(request.kitName),
            request.delivery.delivered,
            request.count,
            escapeJson(timestamp.toString())
        );
    }

    private static String escapeMarkdown(String value) {
        return value == null ? "" : value.replace("`", "'");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";

        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) escaped.append("\\u%04x".formatted((int) c));
                    else escaped.append(c);
                }
            }
        }

        return escaped.toString();
    }
}
