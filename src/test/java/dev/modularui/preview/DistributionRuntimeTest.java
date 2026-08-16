package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DistributionRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void extractedReleaseRunsHelpListsACatalogAndVerifiesItsDefaultScenario() throws Exception {
        Path archive = Path.of(System.getProperty("preview.distribution.zip"));
        Path extracted = temporaryDirectory.resolve("release");
        extract(archive, extracted);
        Path root;
        try (var directories = Files.list(extracted)) {
            root = directories.filter(Files::isDirectory).findFirst().orElseThrow();
        }

        CommandResult help = run(root, "help");
        assertEquals(0, help.exitCode(), help.output());
        assertTrue(help.output().contains("verify"));

        Path catalog = root.resolve("examples/catalog-demo");
        CommandResult list = run(root, "list", catalog.toString());
        assertEquals(0, list.exitCode(), list.output());
        assertTrue(list.output().contains("demo/default"));

        Path output = temporaryDirectory.resolve("verify-output");
        CommandResult verify = run(root, "verify", catalog.toString(), "--output", output.toString());
        assertEquals(0, verify.exitCode(), verify.output());
        assertTrue(Files.isRegularFile(output.resolve("summary.json")));
        assertTrue(Files.isRegularFile(output.resolve("demo/default/preview.png")));
    }

    private static CommandResult run(Path root, String... arguments) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        java.util.List<String> command = new java.util.ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
            command.add(root.resolve("preview.bat").toString());
        } else {
            command.add("sh");
            command.add(root.resolve("preview.sh").toString());
        }
        command.addAll(java.util.List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Release command timed out: " + command);
        }
        return new CommandResult(process.exitValue(), output);
    }

    private static void extract(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (var entry : zip.stream().toList()) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Unsafe archive entry: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var input = zip.getInputStream(entry)) {
                        Files.copy(input, target);
                    }
                }
            }
        }
    }

    private record CommandResult(int exitCode, String output) {}
}
