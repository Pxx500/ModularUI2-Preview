package dev.modularui.preview;

import java.util.List;
import java.util.Map;

record PreviewWorkerResult(
    int schemaVersion,
    String scenarioId,
    String status,
    String category,
    String message,
    long durationMillis,
    String previewerVersion,
    String javaVersion,
    String projectCommit,
    String previewedClass,
    String previewedCodeSource,
    String panelCodeSource,
    List<String> assets,
    List<String> warnings,
    Map<String, String> artifacts) {

    PreviewWorkerResult {
        assets = List.copyOf(assets);
        warnings = List.copyOf(warnings);
        artifacts = Map.copyOf(artifacts);
    }

    boolean passed() {
        return status.equals("passed");
    }
}
