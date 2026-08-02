package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewGenerationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void failedReplacementLeavesTheLastSuccessfulSessionUsable() throws Exception {
        Path project = temporaryDirectory.resolve("watched-preview");
        Path generations = project.resolve("build/preview-generations");
        PreviewProjectInitializer.initialize(project);
        Path source = project.resolve("src/preview/java/example/StarterPanelPreview.java");
        String validSource = Files.readString(source, StandardCharsets.UTF_8);
        Path firstRoot;
        Path secondRoot;

        try (PreviewGeneration first = PreviewGeneration.open(
            project,
            "example.StarterPanelPreview",
            PreviewScreen.fullHd(),
            generations)) {
            firstRoot = first.root();
            assertTrue(Files.isDirectory(firstRoot));

            Files.writeString(source, validSource + "\nthis does not compile\n", StandardCharsets.UTF_8);
            assertThrows(
                IllegalArgumentException.class,
                () -> PreviewGeneration.open(
                    project,
                    "example.StarterPanelPreview",
                    PreviewScreen.fullHd(),
                    generations));
            assertDoesNotThrow(() -> first.session().render());

            Files.writeString(source, validSource, StandardCharsets.UTF_8);
            try (PreviewGeneration second = PreviewGeneration.open(
                project,
                "example.StarterPanelPreview",
                PreviewScreen.fullHd(),
                generations)) {
                secondRoot = second.root();
                assertNotEquals(firstRoot, secondRoot);
                assertTrue(Files.isDirectory(secondRoot));
            }
            assertTrue(Files.notExists(secondRoot));
        }
        assertTrue(Files.notExists(firstRoot));
    }
}
