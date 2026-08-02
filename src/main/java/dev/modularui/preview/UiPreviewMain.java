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
            execute(PreviewCommand.parse(args), output);
            return 0;
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

    private static void execute(PreviewCommand command, PrintStream output) throws Exception {
        if (command.mode() == PreviewCommand.Mode.HELP) {
            output.print(PreviewCommand.usage());
            return;
        }
        if (command.mode() == PreviewCommand.Mode.INIT) {
            initialize(command.projectRoot(), output);
            return;
        }

        Path projectRoot = command.projectRoot();
        String className = command.className() == null ? defaultClassName(projectRoot) : command.className();
        Path configuration = command.configuration() == null
            ? projectRoot.resolve("preview.properties")
                .toAbsolutePath()
            : command.configuration();
        Path outputDirectory = command.outputDirectory() == null
            ? Path.of("output", simpleName(className))
                .toAbsolutePath()
            : command.outputDirectory();
        if (command.mode() == PreviewCommand.Mode.WATCH) {
            new PreviewWindow().watch(projectRoot, className, outputDirectory, configuration);
            return;
        }
        PreviewScreen screen = PreviewScreen.load(configuration);

        switch (command.mode()) {
            case RENDER -> render(command, projectRoot, className, outputDirectory, screen, output);
            case OPEN -> new PreviewWindow().open(projectRoot, className, screen);
            default -> throw new IllegalArgumentException("Unsupported preview command: " + command.mode());
        }
    }

    private static void initialize(Path projectRoot, PrintStream output) {
        PreviewProjectInitializer.initialize(projectRoot);
        output.println("Preview project created: " + projectRoot);
        output.println("Render it: preview.bat render \"" + projectRoot + "\"");
        output.println("Watch it: preview.bat watch \"" + projectRoot + "\"");
    }

    private static void render(PreviewCommand command, Path projectRoot, String className, Path outputDirectory,
        PreviewScreen screen, PrintStream output) throws Exception {
        if (command.actions() != null) {
            new PreviewActionRunner().run(
                projectRoot,
                className,
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
            outputDirectory,
            screen);
        output.println("Preview PNG: " + outputDirectory.resolve("preview.png"));
        output.println("Layout data: " + outputDirectory.resolve("bounds.json"));
        output.println("Warnings: " + result.warnings().size());
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

    private static String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
