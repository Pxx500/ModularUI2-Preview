package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiPreviewMainTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void initCreatesARunnableStarterWithoutOverwritingExistingProjects() throws Exception {
        Path project = temporaryDirectory.resolve("my-preview");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int created = UiPreviewMain.run(
            new String[] { "init", project.toString() },
            new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
            new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertEquals(0, created);
        assertTrue(Files.isRegularFile(project.resolve("preview.properties")));
        assertTrue(Files.isRegularFile(
            project.resolve("src/preview/java/example/StarterPanelPreview.java")));
        assertTrue(Files.isDirectory(project.resolve("src/preview/resources/assets")));
        assertTrue(outputBytes.toString(StandardCharsets.UTF_8).contains("watch"));
        assertEquals("", errorBytes.toString(StandardCharsets.UTF_8));

        Path userFile = project.resolve("keep-me.txt");
        Files.writeString(userFile, "owned by developer", StandardCharsets.UTF_8);
        outputBytes.reset();
        errorBytes.reset();

        int refused = UiPreviewMain.run(
            new String[] { "init", project.toString() },
            new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
            new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertEquals(2, refused);
        assertEquals("owned by developer", Files.readString(userFile, StandardCharsets.UTF_8));
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains(project.toString()));
    }

    @Test
    void helpListsEveryCommandAndUnknownOptionsFailBeforeOpeningAProject() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        int help = UiPreviewMain.run(new String[] { "help" }, output, error);

        assertEquals(0, help);
        String usage = outputBytes.toString(StandardCharsets.UTF_8);
        assertTrue(usage.contains("init"));
        assertTrue(usage.contains("render"));
        assertTrue(usage.contains("open"));
        assertTrue(usage.contains("watch"));
        assertEquals("", errorBytes.toString(StandardCharsets.UTF_8));

        outputBytes.reset();
        int invalid = UiPreviewMain.run(
            new String[] { "render", temporaryDirectory.resolve("missing").toString(), "--watc" },
            output,
            error);

        assertEquals(2, invalid);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("--watc"));
    }

    @Test
    void initializedProjectRendersThroughThePublishedCommand() {
        Path project = temporaryDirectory.resolve("renderable-preview");
        Path outputDirectory = temporaryDirectory.resolve("render-output");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        assertEquals(0, UiPreviewMain.run(new String[] { "init", project.toString() }, output, error));
        outputBytes.reset();

        int rendered = UiPreviewMain.run(
            new String[] { "render", project.toString(), "--output", outputDirectory.toString() },
            output,
            error);

        assertEquals(0, rendered, errorBytes.toString(StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("preview.png")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("bounds.json")));
    }

    @Test
    void failedArtifactPublicationPreservesThePreviousPreview() throws Exception {
        Path project = temporaryDirectory.resolve("transactional-preview");
        Path outputDirectory = temporaryDirectory.resolve("transactional-output");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        assertEquals(0, UiPreviewMain.run(new String[] { "init", project.toString() }, output, error));
        assertEquals(0, UiPreviewMain.run(
            new String[] { "render", project.toString(), "--output", outputDirectory.toString() },
            output,
            error));
        byte[] previousPreview = Files.readAllBytes(outputDirectory.resolve("preview.png"));

        Path source = project.resolve("src/preview/java/example/StarterPanelPreview.java");
        Files.writeString(
            source,
            Files.readString(source, StandardCharsets.UTF_8)
                .replace("Edit the Java class", "Changed candidate image"),
            StandardCharsets.UTF_8);
        Files.delete(outputDirectory.resolve("bounds.json"));
        Files.createDirectory(outputDirectory.resolve("bounds.json"));
        outputBytes.reset();
        errorBytes.reset();

        int failed = UiPreviewMain.run(
            new String[] { "render", project.toString(), "--output", outputDirectory.toString() },
            output,
            error);

        assertEquals(1, failed);
        assertArrayEquals(previousPreview, Files.readAllBytes(outputDirectory.resolve("preview.png")));
    }
}
