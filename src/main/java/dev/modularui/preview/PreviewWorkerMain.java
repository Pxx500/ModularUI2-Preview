package dev.modularui.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal one-scenario process entrypoint used by catalog verification. */
public final class PreviewWorkerMain {

    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private PreviewWorkerMain() {}

    public static void main(String[] arguments) {
        int exitCode = run(arguments);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] arguments) {
        if (arguments.length != 7) {
            System.err.println("Preview worker received an invalid argument count");
            return 2;
        }
        Path projectRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        String entrypoint = arguments[1];
        String scenarioId = arguments[2];
        Path output = Path.of(arguments[3]).toAbsolutePath().normalize();
        Path configuration = Path.of(arguments[4]).toAbsolutePath().normalize();
        Path compiledOutput = Path.of(arguments[5]).toAbsolutePath().normalize();
        boolean runActions = Boolean.parseBoolean(arguments[6]);
        long started = System.nanoTime();
        try {
            Files.createDirectories(output);
            PreviewScreen screen = PreviewScreen.load(configuration);
            try (PreviewSession session = PreviewEngine.openPrepared(
                projectRoot, entrypoint, scenarioId, screen, compiledOutput)) {
                PreviewResult rendered = session.render();
                new UiPreviewRunner().writeArtifacts(output, entrypoint, session, rendered);
                PreviewScenario.Metadata scenario = session.scenario()
                    .orElseThrow(() -> new WorkerFailure("render_error", "Worker did not load scenario metadata"));
                validate(session, rendered, scenario);
                if (runActions && scenario.actions() != null) {
                    Path actions = projectRoot.resolve(scenario.actions()).normalize();
                    if (!actions.startsWith(projectRoot) || !Files.isRegularFile(actions)) {
                        throw new WorkerFailure("interaction_error", "Scenario action script is missing: " + actions);
                    }
                    try {
                        new PreviewActionRunner().run(session, entrypoint, actions, output);
                    } catch (IOException | RuntimeException exception) {
                        throw new WorkerFailure("interaction_error", message(exception), exception);
                    }
                }
                PreviewWorkerResult result = result(
                    projectRoot,
                    scenarioId,
                    "passed",
                    null,
                    "rendered successfully",
                    started,
                    session,
                    rendered,
                    output);
                writeDiagnostic(output, result);
                return 0;
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            String category = failure instanceof WorkerFailure workerFailure
                ? workerFailure.category()
                : category(failure);
            PreviewWorkerResult result = result(
                projectRoot,
                scenarioId,
                "failed",
                category,
                message(failure),
                started,
                null,
                null,
                output);
            try {
                writeDiagnostic(output, result);
            } catch (IOException diagnosticFailure) {
                diagnosticFailure.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private static void validate(PreviewSession session, PreviewResult result, PreviewScenario.Metadata scenario) {
        if (!scenario.previewedClass().equals(session.previewedClassName())) {
            throw new WorkerFailure("missing_class", "Expected production class " + scenario.previewedClass()
                + " but loaded " + session.previewedClassName());
        }
        if (!result.warnings().isEmpty()) {
            throw new WorkerFailure("unexpected_warning", "Render emitted " + result.warnings().size() + " warning(s)");
        }
        if (result.widgets().isEmpty()) {
            throw new WorkerFailure("incomplete_bounds", "Render did not report any widget bounds");
        }
        List<String> missingAssets = scenario.expectedAssets().stream()
            .filter(expected -> result.assetSources().stream().noneMatch(actual -> matchesAsset(expected, actual)))
            .toList();
        if (!missingAssets.isEmpty()) {
            throw new WorkerFailure("missing_asset", "Expected assets were not rendered: " + missingAssets);
        }
    }

    private static boolean matchesAsset(String expected, String actual) {
        String normalizedExpected = expected.replace('\\', '/');
        int namespace = normalizedExpected.indexOf(':');
        if (namespace > 0) {
            normalizedExpected = "assets/" + normalizedExpected.substring(0, namespace) + "/"
                + normalizedExpected.substring(namespace + 1);
        }
        return actual.replace('\\', '/').endsWith(normalizedExpected);
    }

    private static PreviewWorkerResult result(Path projectRoot, String scenarioId, String status, String category,
        String message, long started, PreviewSession session, PreviewResult rendered, Path output) {
        Map<String, String> artifacts = new LinkedHashMap<>();
        recordArtifact(output, artifacts, "preview", "preview.png");
        recordArtifact(output, artifacts, "bounds", "bounds.json");
        recordArtifact(output, artifacts, "actions", "actions.json");
        return new PreviewWorkerResult(
            1,
            scenarioId,
            status,
            category,
            message,
            (System.nanoTime() - started) / 1_000_000L,
            PreviewEnvironment.version(),
            PreviewEnvironment.javaVersion(),
            PreviewEnvironment.projectCommit(projectRoot),
            session == null ? null : session.previewedClassName(),
            session == null ? null : session.previewedCodeSource().toString(),
            session == null ? null : session.panelCodeSource().toString(),
            rendered == null ? List.of() : rendered.assetSources(),
            rendered == null ? List.of() : rendered.warnings(),
            artifacts);
    }

    private static void recordArtifact(Path output, Map<String, String> artifacts, String key, String name) {
        if (Files.isRegularFile(output.resolve(name))) artifacts.put(key, name);
    }

    private static void writeDiagnostic(Path output, PreviewWorkerResult result) throws IOException {
        Files.createDirectories(output);
        Path candidate = output.resolve("diagnostic.json.candidate");
        Files.writeString(candidate, JSON.toJson(result) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.move(candidate, output.resolve("diagnostic.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String category(Throwable failure) {
        String message = message(failure);
        if (message.startsWith("[classpath_error]") || message.startsWith("[missing_output]")) {
            return "classpath_error";
        }
        if (message.contains("Could not load preview runtime class")) return "missing_class";
        if (message.contains("Could not invoke") || message.contains("Could not create")) return "missing_method";
        return "render_error";
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static final class WorkerFailure extends RuntimeException {

        private final String category;

        private WorkerFailure(String category, String message) {
            super(message);
            this.category = category;
        }

        private WorkerFailure(String category, String message, Throwable cause) {
            super(message, cause);
            this.category = category;
        }

        private String category() {
            return category;
        }
    }
}
