package dev.modularui.preview;

import dev.modularui.preview.project.PreviewProject;
import dev.modularui.preview.runtime.ProjectRuntime;
import java.nio.file.Path;
import java.util.List;

/** Public owner of project validation and preview sessions. */
public final class PreviewEngine {

    private PreviewEngine() {}

    public static Preflight preflight(Path projectRoot, String entrypoint) {
        return ProjectPreflight.inspect(projectRoot, entrypoint);
    }

    public static PreviewSession open(Path projectRoot, String entrypoint, PreviewScreen screen) {
        return open(projectRoot, entrypoint, null, screen);
    }

    public static PreviewSession open(Path projectRoot, String entrypoint, String scenarioId, PreviewScreen screen) {
        PreviewProject project = PreviewProject.open(projectRoot);
        return open(project, entrypoint, scenarioId, screen);
    }

    static PreviewSession open(Path projectRoot, String entrypoint, PreviewScreen screen, Path compiledOutput) {
        return open(projectRoot, entrypoint, null, screen, compiledOutput);
    }

    static PreviewSession open(Path projectRoot, String entrypoint, String scenarioId, PreviewScreen screen,
        Path compiledOutput) {
        return open(PreviewProject.open(projectRoot, compiledOutput), entrypoint, scenarioId, screen);
    }

    static PreviewSession openPrepared(Path projectRoot, String entrypoint, String scenarioId, PreviewScreen screen,
        Path compiledOutput) {
        PreviewProject project = PreviewProject.open(projectRoot, compiledOutput);
        requireValidProject(project, entrypoint);
        return ProjectRuntime.openSession(project, entrypoint, scenarioId, screen);
    }

    public static List<PreviewScenario.Metadata> scenarios(Path projectRoot, String entrypoint) {
        PreviewProject project = PreviewProject.open(projectRoot);
        project.compileSources();
        requireValidProject(project, entrypoint);
        return ProjectRuntime.listScenarios(project, entrypoint);
    }

    private static PreviewSession open(PreviewProject project, String entrypoint, String scenarioId,
        PreviewScreen screen) {
        project.compileSources();
        requireValidProject(project, entrypoint);
        return ProjectRuntime.openSession(project, entrypoint, scenarioId, screen);
    }

    private static void requireValidProject(PreviewProject project, String entrypoint) {
        Preflight preflight = ProjectPreflight.inspect(project, entrypoint);
        if (preflight.status() == Status.FAILED) {
            throw new IllegalArgumentException("Preview project preflight failed: " + preflight.diagnostics());
        }
    }

    public enum Status {
        COMPLETE,
        PARTIAL,
        FAILED
    }

    public enum Severity {
        WARNING,
        ERROR
    }

    public record Diagnostic(Severity severity, String code, String message) {}

    public record Preflight(Status status, List<Diagnostic> diagnostics) {

        public Preflight {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
