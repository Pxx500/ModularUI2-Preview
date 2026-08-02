package dev.modularui.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class PreviewProjectInitializer {

    private static final String TEMPLATE_ROOT = "/dev/modularui/preview/template/";

    private PreviewProjectInitializer() {}

    static void initialize(Path projectRoot) {
        ensureEmpty(projectRoot);
        try {
            Files.createDirectories(projectRoot.resolve("src/preview/resources/assets"));
            copy("preview.properties", projectRoot.resolve("preview.properties"));
            copy(
                "StarterPanelPreview.java",
                projectRoot.resolve("src/preview/java/example/StarterPanelPreview.java"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create preview project at " + projectRoot, exception);
        }
    }

    private static void ensureEmpty(Path projectRoot) {
        if (!Files.exists(projectRoot)) return;
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("Preview project target is not a directory: " + projectRoot);
        }
        try (var files = Files.list(projectRoot)) {
            if (files.findAny().isPresent()) {
                throw new IllegalArgumentException("Preview project target is not empty: " + projectRoot);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview project target: " + projectRoot, exception);
        }
    }

    private static void copy(String resourceName, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream source = PreviewProjectInitializer.class.getResourceAsStream(TEMPLATE_ROOT + resourceName)) {
            if (source == null) throw new IOException("Missing packaged preview template: " + resourceName);
            Files.copy(source, target);
        }
    }
}
