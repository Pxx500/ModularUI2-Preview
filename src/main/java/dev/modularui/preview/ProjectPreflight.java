package dev.modularui.preview;

import dev.modularui.preview.PreviewEngine.Diagnostic;
import dev.modularui.preview.PreviewEngine.Preflight;
import dev.modularui.preview.PreviewEngine.Severity;
import dev.modularui.preview.PreviewEngine.Status;
import dev.modularui.preview.project.PreviewProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ProjectPreflight {

    private ProjectPreflight() {}

    static Preflight inspect(Path projectRoot, String entrypoint) {
        Path normalizedRoot = projectRoot.toAbsolutePath()
            .normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return failed("project.root.missing", "Preview project directory does not exist: " + normalizedRoot);
        }
        return inspect(PreviewProject.open(normalizedRoot), entrypoint);
    }

    static Preflight inspect(PreviewProject project, String entrypoint) {
        ProjectArtifactIndex index = ProjectArtifactIndex.inspect(project);
        List<Diagnostic> diagnostics = new ArrayList<>(index.diagnostics());
        diagnostics.addAll(RuntimeProfile.validate(index.classOwners()));
        String classEntry = entrypoint.replace('.', '/') + ".class";
        if (!index.classOwners()
            .containsKey(classEntry)) {
            diagnostics.add(error("entrypoint.missing", "Preview entrypoint was not found: " + entrypoint));
        }
        boolean failed = diagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        return new Preflight(failed ? Status.FAILED : diagnostics.isEmpty() ? Status.COMPLETE : Status.PARTIAL, diagnostics);
    }

    private static Preflight failed(String code, String message) {
        return new Preflight(Status.FAILED, List.of(error(code, message)));
    }

    private static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message);
    }
}
