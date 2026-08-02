package dev.modularui.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

public final class UiPreviewRunner {

    private static final List<String> ARTIFACT_NAMES = List.of("preview.png", "bounds.json");

    public PreviewResult preview(Path projectRoot, String className, Path outputDirectory) throws IOException {
        return preview(projectRoot, className, outputDirectory, PreviewScreen.fullHd());
    }

    public PreviewResult preview(Path projectRoot, String className, Path outputDirectory, PreviewScreen screen)
        throws IOException {
        try (PreviewSession session = PreviewEngine.open(projectRoot, className, screen)) {
            PreviewResult result = session.render();
            writeArtifacts(outputDirectory, className, session, result);
            return result;
        }
    }

    void writeArtifacts(Path outputDirectory, String className, PreviewSession session, PreviewResult result)
        throws IOException {
        Files.createDirectories(outputDirectory);
        Path transaction = Files.createTempDirectory(outputDirectory, ".preview-artifacts-");
        try {
            ImageIO.write(result.image(), "png", transaction.resolve("preview.png").toFile());
            Files.writeString(
                transaction.resolve("bounds.json"),
                toJson(className, session, result),
                StandardCharsets.UTF_8);
            commitArtifacts(outputDirectory, transaction);
        } catch (IOException | RuntimeException | Error failure) {
            cleanupAfterFailure(transaction, failure);
            throw failure;
        }
        cleanupQuietly(transaction);
    }

    private void commitArtifacts(Path outputDirectory, Path transaction) throws IOException {
        List<String> backedUp = new ArrayList<>();
        List<String> installed = new ArrayList<>();
        for (String name : ARTIFACT_NAMES) {
            Path target = outputDirectory.resolve(name);
            if (Files.exists(target) && !Files.isRegularFile(target)) {
                throw new IOException("Preview artifact target is not a regular file: " + target);
            }
        }
        try {
            for (String name : ARTIFACT_NAMES) {
                Path target = outputDirectory.resolve(name);
                if (!Files.exists(target)) continue;
                Files.move(target, transaction.resolve(name + ".backup"), StandardCopyOption.REPLACE_EXISTING);
                backedUp.add(name);
            }
            for (String name : ARTIFACT_NAMES) {
                Files.move(
                    transaction.resolve(name),
                    outputDirectory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
                installed.add(name);
            }
        } catch (IOException failure) {
            rollbackArtifacts(outputDirectory, transaction, installed, backedUp, failure);
            throw failure;
        }
    }

    private void rollbackArtifacts(Path outputDirectory, Path transaction, List<String> installed,
        List<String> backedUp, IOException failure) {
        Collections.reverse(installed);
        for (String name : installed) {
            try {
                Files.deleteIfExists(outputDirectory.resolve(name));
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        Collections.reverse(backedUp);
        for (String name : backedUp) {
            try {
                Files.move(
                    transaction.resolve(name + ".backup"),
                    outputDirectory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void cleanupAfterFailure(Path transaction, Throwable failure) {
        try {
            cleanupCandidateArtifacts(transaction);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void cleanupCandidateArtifacts(Path transaction) throws IOException {
        IOException failure = null;
        for (String name : ARTIFACT_NAMES) {
            try {
                Files.deleteIfExists(transaction.resolve(name));
            } catch (IOException cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
        }
        try {
            Files.deleteIfExists(transaction);
        } catch (DirectoryNotEmptyException ignored) {
            // An incomplete rollback leaves last-good backups here for recovery.
        } catch (IOException cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        if (failure != null) throw failure;
    }

    private void cleanupQuietly(Path transaction) {
        try {
            cleanup(transaction);
        } catch (IOException ignored) {
            // Published artifacts are valid; a stale transaction directory is harmless.
        }
    }

    private void cleanup(Path transaction) throws IOException {
        IOException failure = null;
        for (String name : ARTIFACT_NAMES) {
            for (Path path : List.of(transaction.resolve(name), transaction.resolve(name + ".backup"))) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupFailure) {
                    if (failure == null) failure = cleanupFailure;
                    else failure.addSuppressed(cleanupFailure);
                }
            }
        }
        try {
            Files.deleteIfExists(transaction);
        } catch (IOException cleanupFailure) {
            if (failure == null) failure = cleanupFailure;
            else failure.addSuppressed(cleanupFailure);
        }
        if (failure != null) throw failure;
    }

    String toJson(String className, PreviewSession session, PreviewResult result) {
        ScreenLayout layout = result.layout();
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": 1");
        json.append(",\n  \"previewClass\": \"")
            .append(escapeJson(className))
            .append("\"");
        json.append(",\n  \"status\": \"")
            .append(result.warnings().isEmpty() ? "complete" : "warnings")
            .append("\"");
        json.append(",\n  \"entrypointClass\": \"")
            .append(escapeJson(session.entrypointClassName()))
            .append("\"");
        json.append(",\n  \"entrypointCodeSource\": \"")
            .append(escapeJson(session.entrypointCodeSource().toString()))
            .append("\"");
        json.append(",\n  \"previewedClass\": \"")
            .append(escapeJson(session.previewedClassName()))
            .append("\"");
        json.append(",\n  \"previewedCodeSource\": \"")
            .append(escapeJson(session.previewedCodeSource().toString()))
            .append("\"");
        json.append(",\n  \"panelName\": \"")
            .append(escapeJson(session.panelName()))
            .append("\"");
        json.append(",\n  \"panelClass\": \"")
            .append(escapeJson(session.panelClassName()))
            .append("\"");
        json.append(",\n  \"panelCodeSource\": \"")
            .append(escapeJson(session.panelCodeSource().toString()))
            .append("\"");
        json.append(",\n  \"screen\": {\"width\": ")
            .append(layout.screenWidth())
            .append(", \"height\": ")
            .append(layout.screenHeight())
            .append(", \"guiScale\": ")
            .append(layout.guiScale())
            .append(", \"logicalWidth\": ")
            .append(layout.logicalWidth())
            .append(", \"logicalHeight\": ")
            .append(layout.logicalHeight())
            .append('}');
        Bounds panelLogical = layout.panelLogical();
        json.append(",\n  \"panel\": {\"local\": ");
        appendBounds(json, new Bounds(0, 0, panelLogical.width(), panelLogical.height()));
        json.append(", \"logical\": ");
        appendBounds(json, panelLogical);
        json.append(", \"screen\": ");
        appendBounds(json, layout.panelScreen());
        json.append('}');
        json.append(",\n  \"widgets\": [");
        appendWidgets(json, result.widgets());
        json.append("\n  ],\n  \"assets\": [");
        appendWarnings(json, result.assetSources());
        json.append("\n  ],\n  \"warnings\": [");
        appendWarnings(json, result.warnings());
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private void appendWidgets(StringBuilder json, List<WidgetBounds> widgets) {
        for (int index = 0; index < widgets.size(); index++) {
            WidgetBounds widget = widgets.get(index);
            json.append(index == 0 ? "\n" : ",\n");
            json.append("    {\"path\": \"")
                .append(escapeJson(widget.path()))
                .append("\", \"type\": \"")
                .append(escapeJson(widget.type()))
                .append("\"");
            json.append(", \"local\": ");
            appendBounds(json, widget.local());
            json.append(", \"logical\": ");
            appendBounds(json, widget.logical());
            json.append(", \"screen\": ");
            appendBounds(json, widget.screen());
            json.append(", \"visible\": ")
                .append(widget.visible());
            json.append(", \"clipped\": ")
                .append(widget.clipped())
                .append('}');
        }
    }

    private void appendBounds(StringBuilder json, Bounds bounds) {
        json.append("{\"x\": ")
            .append(bounds.x())
            .append(", \"y\": ")
            .append(bounds.y())
            .append(", \"width\": ")
            .append(bounds.width())
            .append(", \"height\": ")
            .append(bounds.height())
            .append('}');
    }

    private void appendWarnings(StringBuilder json, List<String> warnings) {
        for (int index = 0; index < warnings.size(); index++) {
            json.append(index == 0 ? "\n" : ",\n");
            json.append("    \"")
                .append(escapeJson(warnings.get(index)))
                .append("\"");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
