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
        PreviewProject project = PreviewProject.open(projectRoot);
        project.compileSources();
        Preflight preflight = preflight(projectRoot, entrypoint);
        if (preflight.status() == Status.FAILED) {
            throw new IllegalArgumentException("Preview project preflight failed: " + preflight.diagnostics());
        }
        return ProjectRuntime.openSession(project, entrypoint, screen);
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
