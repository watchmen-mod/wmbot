package com.watchmenbot.modules.stash;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.watchmenbot.util.AtomicJsonFile;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class StashKitbotQueueState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SCHEMA_VERSION = 1;

    private StashKitbotQueueState() {
    }

    static LoadResult load(MinecraftClient mc) {
        Path path = queuePath(mc);
        if (path == null) return new LoadResult(new State(List.of(), null), false);

        AtomicJsonFile.ReadResult<State> result = AtomicJsonFile.readIfExists(
            path,
            new State(List.of(), null),
            reader -> sanitize(GSON.fromJson(reader, QueueFile.class))
        );
        return new LoadResult(result.value(), result.failed());
    }

    static State read(Path path) {
        return AtomicJsonFile.readIfExists(
            path,
            new State(List.of(), null),
            reader -> sanitize(GSON.fromJson(reader, QueueFile.class))
        ).value();
    }

    static State fromJson(String json) {
        if (json == null || json.isBlank()) return new State(List.of(), null);

        try {
            return sanitize(GSON.fromJson(json, QueueFile.class));
        }
        catch (Exception ignored) {
            return new State(List.of(), null);
        }
    }

    static void write(MinecraftClient mc, State state) throws IOException {
        Path path = queuePath(mc);
        if (path != null) write(path, state);
    }

    static void write(Path path, State state) throws IOException {
        State sanitized = sanitize(state);
        AtomicJsonFile.write(path, writer -> {
            GSON.toJson(new QueueFile(SCHEMA_VERSION, sanitized.queuedRequests(), sanitized.deliveryResume()), writer);
        });
    }

    static DeliveryResume resumeFromRequest(KitRequest request) {
        if (request == null) return null;
        return sanitize(new DeliveryResume(
            request.requester,
            request.kitName,
            request.count,
            request.gather.gathered,
            request.delivery.preTpaDimension,
            Instant.now().toString()
        ));
    }

    private static Path queuePath(MinecraftClient mc) {
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("watchmenbot").resolve("stash_kitbot_queue.json");
    }

    private static State sanitize(QueueFile file) {
        if (file == null) return new State(List.of(), null);
        return sanitize(new State(file.queuedRequests(), file.deliveryResume()));
    }

    static State sanitize(State state) {
        if (state == null) return new State(List.of(), null);

        List<QueuedKitRequest> requests = new ArrayList<>();
        if (state.queuedRequests() != null) {
            for (QueuedKitRequest request : state.queuedRequests()) {
                QueuedKitRequest sanitized = sanitize(request);
                if (sanitized != null) requests.add(sanitized);
            }
        }

        return new State(List.copyOf(requests), sanitize(state.deliveryResume()));
    }

    private static QueuedKitRequest sanitize(QueuedKitRequest request) {
        if (request == null || request.access() == null || request.command() == null) return null;

        KitbotRequesterAccess access = request.access();
        String requester = clean(access.requester());
        String normalized = clean(access.normalizedRequester());
        if (normalized.isEmpty()) normalized = StashKitbotAccessPlanner.normalize(requester);
        if (requester.isEmpty() || normalized.isEmpty() || access.tier() == null) return null;

        KitCommand command = request.command();
        String kitName = clean(command.name());
        if (kitName.isEmpty() || command.count() <= 0) return null;

        return new QueuedKitRequest(
            new KitbotRequesterAccess(requester, normalized, access.tier(), Math.max(0, access.cooldownTicks())),
            new KitCommand(kitName, command.count(), command.quotedSearch())
        );
    }

    private static DeliveryResume sanitize(DeliveryResume resume) {
        if (resume == null) return null;

        String requester = clean(resume.requester());
        String kitName = clean(resume.kitName());
        String preTpaDimension = clean(resume.preTpaDimension());
        if (requester.isEmpty() || kitName.isEmpty() || preTpaDimension.isEmpty()) return null;
        if (resume.requestedCount() <= 0 || resume.gatheredCount() <= 0) return null;

        return new DeliveryResume(
            requester,
            kitName,
            resume.requestedCount(),
            Math.min(resume.gatheredCount(), resume.requestedCount()),
            preTpaDimension,
            clean(resume.savedAt())
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    record LoadResult(State state, boolean failed) {
    }

    record State(List<QueuedKitRequest> queuedRequests, DeliveryResume deliveryResume) {
    }

    record DeliveryResume(String requester, String kitName, int requestedCount, int gatheredCount, String preTpaDimension, String savedAt) {
    }

    private record QueueFile(int schemaVersion, List<QueuedKitRequest> queuedRequests, DeliveryResume deliveryResume) {
    }
}
