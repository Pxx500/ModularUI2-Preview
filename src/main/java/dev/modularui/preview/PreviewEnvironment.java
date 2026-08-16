package dev.modularui.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class PreviewEnvironment {

    private PreviewEnvironment() {}

    static String version() {
        String configured = System.getProperty("preview.version");
        if (configured != null && !configured.isBlank()) return configured;
        String packaged = UiPreviewMain.class.getPackage().getImplementationVersion();
        return packaged == null ? "development" : packaged;
    }

    static String javaVersion() {
        return System.getProperty("java.version", "unknown");
    }

    static String projectCommit(Path projectRoot) {
        Process process = null;
        try {
            process = new ProcessBuilder("git", "-C", projectRoot.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) return "unknown";
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return value.isBlank() ? "unknown" : value;
        } catch (IOException exception) {
            return "unknown";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "unknown";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
