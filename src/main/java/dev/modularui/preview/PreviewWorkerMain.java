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
        PreviewSession observedSession = null;
        try {
            Files.createDirectories(output);
            PreviewScreen screen = PreviewScreen.load(configuration);
            try (PreviewSession session = PreviewEngine.openPrepared(
                projectRoot, entrypoint, scenarioId, screen, compiledOutput)) {
                observedSession = session;
                PreviewResult rendered = session.render();
                new UiPreviewRunner().writeArtifacts(output, entrypoint, session, rendered);
                PreviewScenario.Metadata scenario = session.scenario()
                    .orElseThrow(() -> new WorkerFailure("render_error", "Worker did not load scenario metadata"));
                session.validateRender(rendered);
                if ((runActions || scenario.knownFailure() != null) && scenario.actions() != null) {
                    Path actions = projectRoot.resolve(scenario.actions()).normalize();
                    if (!actions.startsWith(projectRoot) || !Files.isRegularFile(actions)) {
                        throw new WorkerFailure("interaction_error", "Scenario action script is missing: " + actions);
                    }
                    try {
                        new PreviewActionRunner().run(session, entrypoint, actions, output);
                    } catch (PreviewSession.RenderFailure exception) {
                        throw exception;
                    } catch (IOException | RuntimeException exception) {
                        throw new WorkerFailure("interaction_error", message(exception), exception);
                    }
                }
                if (scenario.knownFailure() != null) {
                    throw new WorkerFailure("unexpected_pass", "Known failure no longer reproduced: "
                        + scenario.knownFailure().reason());
                }
                PreviewWorkerResult result = result(
                    projectRoot,
                    scenarioId,
                    "passed",
                    null,
                    "rendered successfully",
                    started,
                    session,
                    session.lastRender(),
                    output);
                writeDiagnostic(output, result);
                return 0;
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            String category = failure instanceof WorkerFailure workerFailure
                ? workerFailure.category()
                : failure instanceof PreviewSession.RenderFailure renderFailure ? renderFailure.category()
                : category(failure);
            PreviewScenario.KnownFailure expected = observedSession == null ? null
                : observedSession.scenario().map(PreviewScenario.Metadata::knownFailure).orElse(null);
            boolean known = expected != null && expected.matches(category, failure);
            PreviewWorkerResult result = result(
                projectRoot,
                scenarioId,
                known ? "known_failure" : "failed",
                category,
                known ? expected.reason() + ": " + expected.causeMessage() : message(failure),
                started,
                observedSession,
                observedSession == null ? null : observedSession.lastRender(),
                output);
            try {
                writeDiagnostic(output, result);
            } catch (IOException diagnosticFailure) {
                diagnosticFailure.printStackTrace(System.err);
            }
            return 1;
        }
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
