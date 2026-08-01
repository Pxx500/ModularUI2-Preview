package dev.modularui.preview;

import java.nio.file.Path;

public final class UiPreviewMain {

    private static final int MIN_ARGUMENTS = 1;
    private static final int MAX_ARGUMENTS = 3;

    private UiPreviewMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < MIN_ARGUMENTS || args.length > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("Usage: <preview-class> [output-directory] [configuration]");
        }
        String className = args[0];
        Path outputDirectory = Path.of(args.length >= 2 ? args[1] : "output/" + simpleName(className))
            .toAbsolutePath();
        Path configuration = Path.of(args.length == MAX_ARGUMENTS ? args[2] : "preview.properties")
            .toAbsolutePath();
        PreviewResult result = new UiPreviewRunner()
            .preview(className, outputDirectory, PreviewScreen.load(configuration));
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
