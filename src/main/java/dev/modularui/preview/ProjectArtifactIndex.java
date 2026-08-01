package dev.modularui.preview;

import dev.modularui.preview.PreviewEngine.Diagnostic;
import dev.modularui.preview.PreviewEngine.Severity;
import dev.modularui.preview.project.PreviewProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

final class ProjectArtifactIndex {

    private final Map<String, Path> classOwners = new LinkedHashMap<>();
    private final Map<String, Path> resourceOwners = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private ProjectArtifactIndex() {}

    static ProjectArtifactIndex inspect(PreviewProject project) {
        ProjectArtifactIndex index = new ProjectArtifactIndex();
        project.runtimeArtifacts()
            .forEach(index::indexRuntimeArtifact);
        project.assetSources()
            .forEach(index::indexAssetSource);
        index.diagnostics.addAll(ExtensionServices.validate(project.extensions(), index.classOwners));
        return index;
    }

    Map<String, Path> classOwners() {
        return Map.copyOf(classOwners);
    }

    List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    private void indexRuntimeArtifact(Path artifact) {
        try {
            for (String entry : artifactEntries(artifact)) {
                if (entry.endsWith(".class")) recordClassOwner(entry, artifact);
                if (entry.startsWith("assets/")) recordResourceOwner(entry, artifact, false);
            }
        } catch (IOException exception) {
            diagnostics.add(error("artifact.unreadable", "Could not inspect runtime artifact: " + artifact));
        }
    }

    private void indexAssetSource(Path source) {
        try {
            for (String entry : artifactEntries(source)) {
                String resource = projectResourceName(source, entry);
                if (resource != null) recordResourceOwner(resource, source, true);
            }
        } catch (IOException exception) {
            diagnostics.add(error("artifact.unreadable", "Could not inspect project asset source: " + source));
        }
    }

    private void recordClassOwner(String classEntry, Path artifact) {
        Path previous = classOwners.putIfAbsent(classEntry, artifact);
        if (previous != null) {
            String className = classEntry.substring(0, classEntry.length() - ".class".length())
                .replace('/', '.');
            diagnostics.add(new Diagnostic(
                Severity.WARNING,
                "classpath.shadowed-class",
                "Class " + className + " is loaded from " + previous + " before " + artifact));
        }
    }

    private void recordResourceOwner(String resource, Path artifact, boolean strict) {
        Path previous = resourceOwners.putIfAbsent(resource, artifact);
        if (previous != null) {
            diagnostics.add(new Diagnostic(
                strict ? Severity.ERROR : Severity.WARNING,
                strict ? "resources.ambiguous" : "resources.shadowed",
                "Resource " + resource + " is loaded from " + previous + " before " + artifact));
        }
    }

    private static String projectResourceName(Path source, String entry) {
        if ("assets".equals(source.getFileName()
            .toString())) {
            return "assets/" + entry;
        }
        return entry.startsWith("assets/") ? entry : null;
    }

    private static List<String> artifactEntries(Path artifact) throws IOException {
        if (Files.isDirectory(artifact)) {
            try (var paths = Files.walk(artifact)) {
                return paths.filter(Files::isRegularFile)
                    .map(artifact::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
            }
        }
        try (JarFile jar = new JarFile(artifact.toFile())) {
            return jar.stream()
                .filter(entry -> !entry.isDirectory())
                .map(java.util.jar.JarEntry::getName)
                .sorted()
                .toList();
        }
    }

    private static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message);
    }
}
