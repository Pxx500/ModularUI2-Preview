package dev.modularui.preview;

import java.nio.file.Path;

public final class UiPreviewMain {

    private static final int MIN_ARGUMENTS = 2;
    private static final int MAX_ARGUMENTS = 4;

    private UiPreviewMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < MIN_ARGUMENTS || args.length > MAX_ARGUMENTS) {
            throw new IllegalArgumentException(
                "Usage: <project-directory> <preview-class> [output-directory] [configuration]");
        }
        Path projectRoot = Path.of(args[0])
            .toAbsolutePath();
        String className = args[1];
        Path outputDirectory = Path.of(args.length >= 3 ? args[2] : "output/" + simpleName(className))
            .toAbsolutePath();
        Path configuration = Path.of(args.length == MAX_ARGUMENTS ? args[3] : projectRoot.resolve("preview.properties")
            .toString())
            .toAbsolutePath();
        PreviewResult result = new UiPreviewRunner()
            .preview(projectRoot, className, outputDirectory, PreviewScreen.load(configuration));
        System.out.println("Preview PNG: " + outputDirectory.resolve("preview.png"));
        System.out.println("Layout data: " + outputDirectory.resolve("bounds.json"));
        System.out.println(
            "Warnings: " + result.warnings()
                .size());
    }

    private static String simpleName(String className) {
        int packageSeparator = className.lastIndexOf('.');
        return packageSeparator < 0 ? className : className.substring(packageSeparator + 1);
    }
}
