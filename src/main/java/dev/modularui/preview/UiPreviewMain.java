package dev.modularui.preview;

import dev.modularui.preview.project.PreviewProject;
import java.io.PrintStream;
import java.nio.file.Path;

public final class UiPreviewMain {

    private UiPreviewMain() {}

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, PrintStream output, PrintStream error) {
        try {
            return execute(PreviewCommand.parse(args), output);
        } catch (IllegalArgumentException exception) {
            error.println(exception.getMessage());
            error.println("Run 'preview help' to see the supported commands.");
            return 2;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            error.println("Preview failed: " + failureMessage(exception));
            return 1;
        }
    }

    private static int execute(PreviewCommand command, PrintStream output) throws Exception {
        if (command.mode() == PreviewCommand.Mode.HELP) {
            output.print(PreviewCommand.usage());
            return 0;
        }
        if (command.mode() == PreviewCommand.Mode.INIT) {
            initialize(command.projectRoot(), output);
            return 0;
        }

        Path projectRoot = command.projectRoot();
        String className = command.className() == null ? defaultClassName(projectRoot) : command.className();
        Path configuration = command.configuration() == null
            ? projectRoot.resolve("preview.properties")
                .toAbsolutePath()
            : command.configuration();
        Path outputDirectory = command.outputDirectory() == null
            ? defaultOutputDirectory(command, className)
                .toAbsolutePath()
            : command.outputDirectory();
        if (command.mode() == PreviewCommand.Mode.LIST) {
            list(projectRoot, className, output);
            return 0;
        }
        if (command.mode() == PreviewCommand.Mode.DOCTOR) {
            doctor(projectRoot, className, output);
            return 0;
        }
        if (command.mode() == PreviewCommand.Mode.VERIFY) {
            PreviewVerifier.VerificationSummary summary = new PreviewVerifier().verify(
                projectRoot,
                className,
                command.scenarioId(),
                outputDirectory,
                configuration,
                command.verification());
            output.println("Verification summary: " + outputDirectory.resolve("summary.txt"));
            output.println("Machine summary: " + outputDirectory.resolve("summary.json"));
            output.println(summary.passed() + " passed, " + summary.failed() + " failed, "
                + summary.knownFailures() + " known failures");
            return summary.allPassed() ? 0 : 1;
        }
        if (command.mode() == PreviewCommand.Mode.WATCH) {
            new PreviewWindow().watch(projectRoot, className, command.scenarioId(), outputDirectory, configuration);
            return 0;
        }
        PreviewScreen screen = PreviewScreen.load(configuration);

        return switch (command.mode()) {
            case RENDER -> {
                render(command, projectRoot, className, outputDirectory, screen, output);
                yield 0;
            }
            case OPEN -> {
                new PreviewWindow().open(projectRoot, className, command.scenarioId(), screen);
                yield 0;
            }
            default -> throw new IllegalArgumentException("Unsupported preview command: " + command.mode());
        };
    }

    private static void initialize(Path projectRoot, PrintStream output) {
        PreviewProject.initialize(projectRoot);
        output.println("Preview project created: " + projectRoot);
        output.println("Render on Windows: preview.bat render \"" + projectRoot + "\"");
        output.println("Render on Linux/macOS: ./preview.sh render \"" + projectRoot + "\"");
        output.println("Watch on Windows: preview.bat watch \"" + projectRoot + "\"");
        output.println("Watch on Linux/macOS: ./preview.sh watch \"" + projectRoot + "\"");
    }

    private static void render(PreviewCommand command, Path projectRoot, String className, Path outputDirectory,
        PreviewScreen screen, PrintStream output) throws Exception {
        if (command.actions() != null) {
            new PreviewActionRunner().run(
                projectRoot,
                className,
                command.scenarioId(),
                command.actions(),
                outputDirectory,
                screen);
            output.println("Action results: " + outputDirectory.resolve("actions.json"));
            output.println("Captures: " + outputDirectory.resolve("captures"));
            return;
        }
        PreviewResult result = new UiPreviewRunner().preview(
            projectRoot,
            className,
            command.scenarioId(),
            outputDirectory,
            screen);
        output.println("Preview PNG: " + outputDirectory.resolve("preview.png"));
        output.println("Layout data: " + outputDirectory.resolve("bounds.json"));
        output.println("Warnings: " + result.warnings().size());
    }

    private static void list(Path projectRoot, String className, PrintStream output) {
        for (PreviewScenario.Metadata scenario : PreviewEngine.scenarios(projectRoot, className)) {
            output.println(scenario.id() + "\t" + scenario.family() + "\t" + scenario.description());
        }
    }

    private static void doctor(Path projectRoot, String className, PrintStream output) {
        PreviewProject project = PreviewProject.open(projectRoot);
        java.util.List<PreviewScenario.Metadata> scenarios = PreviewEngine.scenarios(projectRoot, className);
        output.println("ModularUI2 Preview: " + PreviewEnvironment.version());
        output.println("JDK: " + Runtime.version().feature() + " (" + PreviewEnvironment.javaVersion() + ")");
        output.println("Project: " + projectRoot.toAbsolutePath().normalize());
        output.println("Entrypoint: " + className);
        output.println("Preview sources: " + project.previewSources());
        output.println("Runtime entries: " + project.productionRuntime().size());
        output.println("Scenarios: " + scenarios.size());
        output.println("Project commit: " + PreviewEnvironment.projectCommit(projectRoot));
    }

    private static String defaultClassName(Path projectRoot) {
        return PreviewProject.open(projectRoot)
            .property("preview.entrypoint")
            .orElseThrow(() -> new IllegalArgumentException(
                "Missing preview.entrypoint in " + projectRoot.resolve("preview.properties")));
    }

    private static String simpleName(String className) {
        int packageSeparator = className.lastIndexOf('.');
        return packageSeparator < 0 ? className : className.substring(packageSeparator + 1);
    }

    private static Path defaultOutputDirectory(PreviewCommand command, String className) {
        if (command.mode() == PreviewCommand.Mode.VERIFY) return command.projectRoot().resolve("output/verify");
        return command.scenarioId() == null
            ? Path.of("output", simpleName(className))
            : Path.of("output").resolve(command.scenarioId());
    }

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
