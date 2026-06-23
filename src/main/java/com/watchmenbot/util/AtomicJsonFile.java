package com.watchmenbot.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicJsonFile {
    private AtomicJsonFile() {
    }

    public static <T> ReadResult<T> readIfExists(Path path, T missingValue, JsonReader<T> reader) {
        if (path == null || !Files.isRegularFile(path)) return ReadResult.ok(missingValue);

        try (Reader input = Files.newBufferedReader(path)) {
            return ReadResult.ok(reader.read(input));
        }
        catch (Exception exception) {
            return ReadResult.failed(missingValue, exception);
        }
    }

    public static void write(Path path, JsonWriter writer) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temp)) {
                writer.write(output);
            }

            moveAtomically(temp, path);
        }
        catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    private static void moveAtomically(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailed) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    public interface JsonReader<T> {
        T read(Reader reader) throws IOException;
    }

    @FunctionalInterface
    public interface JsonWriter {
        void write(Writer writer) throws IOException;
    }

    public record ReadResult<T>(T value, Exception error) {
        static <T> ReadResult<T> ok(T value) {
            return new ReadResult<>(value, null);
        }

        static <T> ReadResult<T> failed(T fallback, Exception error) {
            return new ReadResult<>(fallback, error);
        }

        public boolean failed() {
            return error != null;
        }
    }
}
