package dev.modularui.preview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class PreviewGeneration implements AutoCloseable {

    private final Path root;
    private final PreviewSession session;
    private final PreviewResult initialResult;

    private PreviewGeneration(Path root, PreviewSession session, PreviewResult initialResult) {
        this.root = root;
        this.session = session;
        this.initialResult = initialResult;
    }

    static PreviewGeneration open(Path projectRoot, String className, PreviewScreen screen, Path generationsRoot) {
        Path root = createRoot(generationsRoot);
        PreviewSession session = null;
        try {
            session = PreviewEngine.open(projectRoot, className, screen, root.resolve("classes"));
            return new PreviewGeneration(root, session, session.render());
        } catch (RuntimeException | Error failure) {
            cleanupFailedOpen(root, session, failure);
            throw failure;
        }
    }

    Path root() {
        return root;
    }

    PreviewSession session() {
        return session;
    }

    PreviewResult initialResult() {
        return initialResult;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            session.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            deleteTree(root);
        } catch (IOException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private static Path createRoot(Path generationsRoot) {
        try {
            Files.createDirectories(generationsRoot);
            return Files.createTempDirectory(generationsRoot, "generation-");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create preview generation below " + generationsRoot, exception);
        }
    }

    private static void cleanupFailedOpen(Path root, PreviewSession session, Throwable failure) {
        if (session != null) {
            try {
                session.close();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        try {
            deleteTree(root);
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            try {
                paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    });
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }
}
