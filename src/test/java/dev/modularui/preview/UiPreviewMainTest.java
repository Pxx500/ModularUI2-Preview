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
        assertTrue(usage.contains("list"));
        assertTrue(usage.contains("render"));
        assertTrue(usage.contains("open"));
        assertTrue(usage.contains("watch"));
        assertTrue(usage.contains("doctor"));
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
    void catalogProjectsListDeterministicallyAndRenderTheSelectedScenario() throws Exception {
        Path project = temporaryDirectory.resolve("catalog-preview");
        Path outputDirectory = temporaryDirectory.resolve("catalog-output");
        writeCatalogProject(project);
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        int listed = UiPreviewMain.run(new String[] { "list", project.toString() }, output, error);

        assertEquals(0, listed, errorBytes.toString(StandardCharsets.UTF_8));
        String listing = outputBytes.toString(StandardCharsets.UTF_8);
        assertTrue(listing.indexOf("machines/empty") < listing.indexOf("machines/running"));
        assertTrue(listing.contains("idle machine"));
        assertTrue(listing.contains("running machine"));

        outputBytes.reset();
        int diagnosed = UiPreviewMain.run(new String[] { "doctor", project.toString() }, output, error);
        assertEquals(0, diagnosed, errorBytes.toString(StandardCharsets.UTF_8));
        String diagnosis = outputBytes.toString(StandardCharsets.UTF_8);
        assertTrue(diagnosis.contains("JDK: 25"));
        assertTrue(diagnosis.contains("Scenarios: 2"));

        outputBytes.reset();
        int rendered = UiPreviewMain.run(
            new String[] {
                "render", project.toString(), "machines/running", "--output", outputDirectory.toString()
            },
            output,
            error);

        assertEquals(0, rendered, errorBytes.toString(StandardCharsets.UTF_8));
        String bounds = Files.readString(outputDirectory.resolve("bounds.json"), StandardCharsets.UTF_8);
        assertTrue(bounds.contains("\"id\": \"machines/running\""));
        assertTrue(bounds.contains("\"family\": \"machines\""));
        assertTrue(bounds.contains("\"previewedClass\": \"example.CatalogPreview\""));
        assertTrue(bounds.contains("\"panelName\": \"running_machine\""));
    }

    @Test
    void catalogProjectsRejectUnknownAndDuplicateScenarioIdsBeforeRendering() throws Exception {
        Path project = temporaryDirectory.resolve("invalid-catalog-preview");
        writeCatalogProject(project);
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        int unknown = UiPreviewMain.run(
            new String[] { "render", project.toString(), "machines/missing" },
            output,
            error);

        assertEquals(2, unknown);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Unknown preview scenario: machines/missing"));

        Path source = project.resolve("src/preview/java/example/CatalogPreview.java");
        Files.writeString(
            source,
            Files.readString(source, StandardCharsets.UTF_8)
                .replace("\"machines/empty\"", "\"machines/running\""),
            StandardCharsets.UTF_8);
        outputBytes.reset();
        errorBytes.reset();

        int duplicate = UiPreviewMain.run(new String[] { "list", project.toString() }, output, error);

        assertEquals(2, duplicate);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Duplicate preview scenario ID: machines/running"));
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

    private static void writeCatalogProject(Path project) throws Exception {
        Files.createDirectories(project.resolve("src/preview/java/example"));
        Files.writeString(
            project.resolve("preview.properties"),
            "preview.entrypoint=example.CatalogPreview\n"
                + "screen.width=1920\n"
                + "screen.height=1080\n"
                + "gui.scale=auto\n"
                + "screen.background=#101820\n");
        Files.writeString(
            project.resolve("src/preview/java/example/CatalogPreview.java"),
            """
                package example;

                import com.cleanroommc.modularui.screen.ModularPanel;
                import dev.modularui.preview.PreviewCatalog;
                import dev.modularui.preview.PreviewEntrypoint;
                import dev.modularui.preview.PreviewScenario;
                import java.util.List;

                public final class CatalogPreview implements PreviewCatalog {
                    @Override
                    public List<PreviewScenario> scenarios() {
                        return List.of(
                            PreviewScenario.define(
                                "machines/running",
                                "running machine",
                                "machines",
                                CatalogPreview.class,
                                () -> panel("running_machine"))
                                .tags("default", "interaction")
                                .expectAssets("example:textures/gui/machine.png")
                                .actions("actions/running.txt"),
                            PreviewScenario.define(
                                "machines/empty",
                                "idle machine",
                                "machines",
                                CatalogPreview.class,
                                () -> panel("empty_machine")));
                    }

                    private static PreviewEntrypoint panel(String name) {
                        return new PreviewEntrypoint() {
                            @Override
                            public Class<?> previewedClass() {
                                return CatalogPreview.class;
                            }

                            @Override
                            public Object createPanel(Context context) {
                                return ModularPanel.defaultPanel(name, 176, 90);
                            }
                        };
                    }
                }
                """);
    }
}
