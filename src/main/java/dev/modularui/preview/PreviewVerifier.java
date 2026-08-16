package dev.modularui.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Coordinates isolated catalog workers and owns the stable verification reports. */
final class PreviewVerifier {

    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    VerificationSummary verify(Path projectRoot, String entrypoint, String selection, Path output,
        Path configuration, PreviewCommand.VerifyOptions options) throws IOException {
        List<PreviewScenario.Metadata> catalog;
        try {
            catalog = PreviewEngine.scenarios(projectRoot, entrypoint);
        } catch (RuntimeException failure) {
            Files.createDirectories(output);
            PreviewWorkerResult result = syntheticFailure(
                projectRoot,
                "catalog",
                preparationCategory(failure),
                failureMessage(failure),
                output.resolve("catalog"),
                0);
            VerificationSummary summary = summary(projectRoot, entrypoint, options, List.of(result));
            writeReports(output, summary);
            return summary;
        }
        validateCanonicalScenarios(catalog);
        List<PreviewScenario.Metadata> selected = select(catalog, selection, options.full() || options.failedOnly());
        if (options.failedOnly()) selected = retainPreviousFailures(output, projectRoot, entrypoint, selected);
        if (selected.isEmpty()) throw new IllegalArgumentException("No preview scenarios matched this verification run");

        Files.createDirectories(output);
        Path compiledOutput = projectRoot.resolve("build/classes/java/preview").toAbsolutePath().normalize();
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(options.jobs(), selected.size()));
        List<PreviewWorkerResult> results = new ArrayList<>();
        try {
            List<Future<PreviewWorkerResult>> futures = selected.stream()
                .map(scenario -> workers.submit(() -> runWorker(
                    projectRoot,
                    entrypoint,
                    scenario,
                    output.resolve(scenario.id()),
                    configuration,
                    compiledOutput,
                    options.full(),
                    timeout(scenario, options))))
                .toList();
            for (Future<PreviewWorkerResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Preview verification was interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IOException("Preview worker coordination failed", exception.getCause());
                }
            }
        } finally {
            workers.shutdownNow();
        }
        results.sort(Comparator.comparing(PreviewWorkerResult::scenarioId));
        VerificationSummary summary = summary(projectRoot, entrypoint, options, results);
        writeReports(output, summary);
        return summary;
    }

    private PreviewWorkerResult runWorker(Path projectRoot, String entrypoint, PreviewScenario.Metadata scenario,
        Path output, Path configuration, Path compiledOutput, boolean runActions, Duration timeout) throws IOException {
        resetOutput(output);
        Files.createDirectories(output);
        Path log = output.resolve("error.log");
        Process process = new ProcessBuilder(
            javaExecutable(),
            "-Djoml.nounsafe=true",
            "-cp",
            childClasspath(),
            PreviewWorkerMain.class.getName(),
            projectRoot.toString(),
            entrypoint,
            scenario.id(),
            output.toString(),
            configuration.toString(),
            compiledOutput.toString(),
            Boolean.toString(runActions))
            .directory(output.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        boolean completed;
        try {
            completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Preview worker was interrupted: " + scenario.id(), exception);
        }
        if (!completed) {
            process.destroyForcibly();
            awaitTermination(process);
            return syntheticFailure(
                projectRoot,
                scenario.id(),
                "timeout",
                "Worker exceeded its " + timeout.toSeconds() + " second timeout",
                output,
                timeout.toMillis());
        }
        Path diagnostic = output.resolve("diagnostic.json");
        if (!Files.isRegularFile(diagnostic)) {
            return syntheticFailure(
                projectRoot,
                scenario.id(),
                "render_error",
                "Worker exited with code " + process.exitValue() + " without a diagnostic",
                output,
                0);
        }
        PreviewWorkerResult result;
        try (var reader = Files.newBufferedReader(diagnostic, StandardCharsets.UTF_8)) {
            result = JSON.fromJson(reader, PreviewWorkerResult.class);
        } catch (RuntimeException exception) {
            return syntheticFailure(
                projectRoot,
                scenario.id(),
                "render_error",
                "Worker produced malformed diagnostic output",
                output,
                0);
        }
        if (result == null || !scenario.id().equals(result.scenarioId())
            || !(result.status().equals("passed") || result.status().equals("failed"))) {
            return syntheticFailure(
                projectRoot,
                scenario.id(),
                "render_error",
                "Worker diagnostic did not match the selected scenario",
                output,
                0);
        }
        if (process.exitValue() != 0 && result.passed()) {
            return syntheticFailure(
                projectRoot,
                scenario.id(),
                "render_error",
                "Worker exited with code " + process.exitValue() + " after reporting success",
                output,
                result.durationMillis());
        }
        return withErrorLog(result, output);
    }

    private PreviewWorkerResult syntheticFailure(Path projectRoot, String scenarioId, String category,
        String message, Path output, long durationMillis) throws IOException {
        Map<String, String> artifacts = new LinkedHashMap<>();
        if (Files.isRegularFile(output.resolve("error.log"))) artifacts.put("error", "error.log");
        PreviewWorkerResult result = new PreviewWorkerResult(
            1,
            scenarioId,
            "failed",
            category,
            message,
            durationMillis,
            PreviewEnvironment.version(),
            PreviewEnvironment.javaVersion(),
            PreviewEnvironment.projectCommit(projectRoot),
            null,
            null,
            null,
            List.of(),
            List.of(),
            artifacts);
        writeDiagnostic(output, result);
        return result;
    }

    private PreviewWorkerResult withErrorLog(PreviewWorkerResult result, Path output) {
        if (!Files.isRegularFile(output.resolve("error.log"))) return result;
        Map<String, String> artifacts = new LinkedHashMap<>(result.artifacts());
        artifacts.put("error", "error.log");
        return new PreviewWorkerResult(
            result.schemaVersion(),
            result.scenarioId(),
            result.status(),
            result.category(),
            result.message(),
            result.durationMillis(),
            result.previewerVersion(),
            result.javaVersion(),
            result.projectCommit(),
            result.previewedClass(),
            result.previewedCodeSource(),
            result.panelCodeSource(),
            result.assets(),
            result.warnings(),
            artifacts);
    }

    private List<PreviewScenario.Metadata> select(List<PreviewScenario.Metadata> catalog, String selection,
        boolean full) {
        if (selection != null) {
            List<PreviewScenario.Metadata> exact = catalog.stream()
                .filter(scenario -> scenario.id().equals(selection))
                .toList();
            if (!exact.isEmpty()) return exact;
            List<PreviewScenario.Metadata> family = catalog.stream()
                .filter(scenario -> scenario.family().equals(selection))
                .toList();
            if (family.isEmpty()) throw new IllegalArgumentException("Unknown preview family or scenario: " + selection);
            return family;
        }
        if (full) return catalog;
        return catalog.stream().filter(scenario -> scenario.tags().contains("default")).toList();
    }

    private void validateCanonicalScenarios(List<PreviewScenario.Metadata> catalog) {
        Map<String, List<PreviewScenario.Metadata>> roots = catalog.stream()
            .collect(Collectors.groupingBy(PreviewScenario.Metadata::previewedClass));
        for (Map.Entry<String, List<PreviewScenario.Metadata>> root : roots.entrySet()) {
            List<String> defaults = root.getValue().stream()
                .filter(scenario -> scenario.tags().contains("default"))
                .map(PreviewScenario.Metadata::id)
                .toList();
            if (defaults.size() != 1) {
                throw new IllegalArgumentException("Production GUI " + root.getKey()
                    + " must have exactly one default preview scenario; found " + defaults);
            }
        }
    }

    private List<PreviewScenario.Metadata> retainPreviousFailures(Path output, Path projectRoot, String entrypoint,
        List<PreviewScenario.Metadata> selected) throws IOException {
        Path summaryFile = output.resolve("summary.json");
        if (!Files.isRegularFile(summaryFile)) {
            throw new IllegalArgumentException("No previous verification summary exists: " + summaryFile);
        }
        VerificationSummary previous;
        try (var reader = Files.newBufferedReader(summaryFile, StandardCharsets.UTF_8)) {
            previous = JSON.fromJson(reader, VerificationSummary.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Previous verification summary is malformed: " + summaryFile,
                exception);
        }
        String normalizedRoot = projectRoot.toAbsolutePath().normalize().toString();
        if (previous == null || !normalizedRoot.equals(previous.projectRoot())
            || !entrypoint.equals(previous.entrypoint())) {
            throw new IllegalArgumentException("Previous verification summary is not compatible with this project");
        }
        Set<String> failed = previous.results().stream()
            .filter(result -> !result.passed())
            .map(PreviewWorkerResult::scenarioId)
            .collect(Collectors.toSet());
        return selected.stream().filter(scenario -> failed.contains(scenario.id())).toList();
    }

    private VerificationSummary summary(Path projectRoot, String entrypoint, PreviewCommand.VerifyOptions options,
        List<PreviewWorkerResult> results) {
        List<PreviewWorkerResult> reportResults = results.stream().map(this::withReportArtifactPaths).toList();
        int passed = (int) reportResults.stream().filter(PreviewWorkerResult::passed).count();
        return new VerificationSummary(
            1,
            reportResults.stream().allMatch(PreviewWorkerResult::passed) ? "passed" : "failed",
            projectRoot.toAbsolutePath().normalize().toString(),
            entrypoint,
            PreviewEnvironment.version(),
            PreviewEnvironment.javaVersion(),
            PreviewEnvironment.projectCommit(projectRoot),
            options.full() ? "full" : options.failedOnly() ? "failed" : "fast",
            reportResults.size(),
            passed,
            reportResults.size() - passed,
            reportResults);
    }

    private PreviewWorkerResult withReportArtifactPaths(PreviewWorkerResult result) {
        Map<String, String> artifacts = result.artifacts().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> result.scenarioId() + "/" + entry.getValue(),
                (first, ignored) -> first,
                LinkedHashMap::new));
        return new PreviewWorkerResult(
            result.schemaVersion(),
            result.scenarioId(),
            result.status(),
            result.category(),
            result.message(),
            result.durationMillis(),
            result.previewerVersion(),
            result.javaVersion(),
            result.projectCommit(),
            result.previewedClass(),
            result.previewedCodeSource(),
            result.panelCodeSource(),
            result.assets(),
            result.warnings(),
            artifacts);
    }

    private void writeReports(Path output, VerificationSummary summary) throws IOException {
        writeAtomically(output.resolve("summary.json"), JSON.toJson(summary) + System.lineSeparator());
        StringBuilder text = new StringBuilder();
        text.append("preview verification: ")
            .append(summary.passed()).append(" passed, ")
            .append(summary.failed()).append(" failed\n");
        for (PreviewWorkerResult result : summary.results()) {
            text.append(result.passed() ? "PASS " : "FAIL ")
                .append(result.scenarioId())
                .append(" (").append(result.durationMillis()).append(" ms)");
            if (!result.passed()) text.append(" [").append(result.category()).append("] ").append(result.message());
            if (!result.passed() && result.artifacts().containsKey("error")) {
                text.append(" -> ").append(result.artifacts().get("error"));
            }
            text.append('\n');
        }
        writeAtomically(output.resolve("summary.txt"), text.toString());
    }

    private void writeDiagnostic(Path output, PreviewWorkerResult result) throws IOException {
        writeAtomically(output.resolve("diagnostic.json"), JSON.toJson(result) + System.lineSeparator());
    }

    private void writeAtomically(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path candidate = target.resolveSibling(target.getFileName() + ".candidate");
        Files.writeString(candidate, contents, StandardCharsets.UTF_8);
        Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void resetOutput(Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (Files.notExists(normalized)) return;
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private Duration timeout(PreviewScenario.Metadata scenario, PreviewCommand.VerifyOptions options) {
        return scenario.timeout() == PreviewScenario.TimeoutCategory.EXTENDED
            ? options.extendedTimeout()
            : options.defaultTimeout();
    }

    private String javaExecutable() {
        String name = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

    private String childClasspath() {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return java.util.Arrays.stream(System.getProperty("java.class.path").split(
            java.util.regex.Pattern.quote(File.pathSeparator)))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path : base.resolve(path).normalize())
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
    }

    private void awaitTermination(Process process) {
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String preparationCategory(RuntimeException failure) {
        String message = failureMessage(failure);
        if (message.contains("Preview source compilation failed")) return "compile_error";
        if (message.startsWith("[classpath_error]") || message.startsWith("[gradle_error]")
            || message.startsWith("[missing_output]")) {
            return "classpath_error";
        }
        if (message.contains("entrypoint.missing")) return "missing_class";
        return "render_error";
    }

    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    record VerificationSummary(
        int schemaVersion,
        String status,
        String projectRoot,
        String entrypoint,
        String previewerVersion,
        String javaVersion,
        String projectCommit,
        String mode,
        int selected,
        int passed,
        int failed,
        List<PreviewWorkerResult> results) {

        VerificationSummary {
            results = List.copyOf(results);
        }

        boolean allPassed() {
            return failed == 0;
        }
    }
}
