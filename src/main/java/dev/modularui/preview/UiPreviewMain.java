package dev.modularui.preview;

import dev.modularui.preview.project.PreviewProject;
import java.nio.file.Path;

public final class UiPreviewMain {

    private static final int MIN_ARGUMENTS = 1;
    private static final int MAX_ARGUMENTS = 4;

    private UiPreviewMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < MIN_ARGUMENTS || args.length > MAX_ARGUMENTS) {
            throw new IllegalArgumentException(
                "Usage: <project-directory> [preview-class] [output-directory] [configuration]"
                    + " | <project-directory> --actions <actions-file> | <project-directory> --interactive");
        }
        Path projectRoot = Path.of(args[0])
            .toAbsolutePath();
        if (args.length >= 2 && args[1].equals("--interactive")) {
            runInteractive(args, projectRoot);
            return;
        }
        if (args.length >= 2 && args[1].equals("--actions")) {
            runActions(args, projectRoot);
            return;
        }
        String className = args.length >= 2 ? args[1] : defaultClassName(projectRoot);
        Path outputDirectory = defaultOutputDirectory(args, className);
        Path configuration = configuration(args, projectRoot);
        PreviewResult result = new UiPreviewRunner()
            .preview(projectRoot, className, outputDirectory, PreviewScreen.load(configuration));
        System.out.println("Preview PNG: " + outputDirectory.resolve("preview.png"));
        System.out.println("Layout data: " + outputDirectory.resolve("bounds.json"));
        System.out.println(
            "Warnings: " + result.warnings()
                .size());
    }

    private static void runActions(String[] args, Path projectRoot) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: <project-directory> --actions <actions-file>");
        }
        String className = defaultClassName(projectRoot);
        Path outputDirectory = Path.of("output/" + simpleName(className))
            .toAbsolutePath();
        Path configuration = projectRoot.resolve("preview.properties")
            .toAbsolutePath();
        Path actions = Path.of(args[2])
            .toAbsolutePath();
        new PreviewActionRunner().run(
            projectRoot,
            className,
            actions,
            outputDirectory,
            PreviewScreen.load(configuration));
        System.out.println("Action results: " + outputDirectory.resolve("actions.json"));
        System.out.println("Captures: " + outputDirectory.resolve("captures"));
    }

    private static void runInteractive(String[] args, Path projectRoot) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <project-directory> --interactive");
        }
        String className = defaultClassName(projectRoot);
        Path configuration = projectRoot.resolve("preview.properties")
            .toAbsolutePath();
        new PreviewWindow().open(projectRoot, className, PreviewScreen.load(configuration));
    }

    private static String defaultClassName(Path projectRoot) {
        return PreviewProject.open(projectRoot)
            .property("preview.entrypoint")
            .orElseThrow(() -> new IllegalArgumentException(
                "Missing preview.entrypoint in " + projectRoot.resolve("preview.properties")));
    }

    private static Path defaultOutputDirectory(String[] args, String className) {
        return Path.of(args.length >= 3 ? args[2] : "output/" + simpleName(className))
            .toAbsolutePath();
    }

    private static Path configuration(String[] args, Path projectRoot) {
        return Path.of(args.length == MAX_ARGUMENTS ? args[3] : projectRoot.resolve("preview.properties")
            .toString())
            .toAbsolutePath();
    }

    private static String simpleName(String className) {
        int packageSeparator = className.lastIndexOf('.');
        return packageSeparator < 0 ? className : className.substring(packageSeparator + 1);
    }
}
