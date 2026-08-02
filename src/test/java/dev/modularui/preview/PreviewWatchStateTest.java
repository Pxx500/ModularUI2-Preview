package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewWatchStateTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void snapshotChangesForPreviewAssetsConfigurationAndRuntimeInputs() throws Exception {
        Path project = temporaryDirectory.resolve("watched-preview");
        PreviewProjectInitializer.initialize(project);
        Path configuration = project.resolve("preview.properties");
        PreviewInputSnapshot initial = PreviewInputSnapshot.capture(project, configuration);

        Path texture = project.resolve("src/preview/resources/assets/example/textures/gui/machine.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] { 1, 2, 3 });
        PreviewInputSnapshot withTexture = PreviewInputSnapshot.capture(project, configuration);

        Files.writeString(configuration, "\nscreen.background=#000000\n", StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.APPEND);
        PreviewInputSnapshot withConfiguration = PreviewInputSnapshot.capture(project, configuration);

        Path runtimeClasses = Files.createDirectories(temporaryDirectory.resolve("production-classes"));
        Files.writeString(
            project.resolve("runtime-classpath.txt"),
            runtimeClasses.toString(),
            StandardCharsets.UTF_8);
        PreviewInputSnapshot withClasspath = PreviewInputSnapshot.capture(project, configuration);
        Files.write(runtimeClasses.resolve("Machine.class"), new byte[] { 4, 5, 6, 7 });
        PreviewInputSnapshot withRuntimeClass = PreviewInputSnapshot.capture(project, configuration);

        assertNotEquals(initial, withTexture);
        assertNotEquals(withTexture, withConfiguration);
        assertNotEquals(withConfiguration, withClasspath);
        assertNotEquals(withClasspath, withRuntimeClass);
    }

    @Test
    void rebuildBecomesReadyAfterInputsRemainStableForThreeHundredMilliseconds() {
        PreviewInputSnapshot first = PreviewInputSnapshot.synthetic("first");
        PreviewInputSnapshot second = PreviewInputSnapshot.synthetic("second");
        PreviewInputSnapshot third = PreviewInputSnapshot.synthetic("third");
        PreviewWatchState state = new PreviewWatchState(first, Duration.ofMillis(300));

        state.observe(second, Duration.ofMillis(100).toNanos());
        assertFalse(state.rebuildReady(Duration.ofMillis(399).toNanos()));
        state.observe(third, Duration.ofMillis(400).toNanos());
        assertFalse(state.rebuildReady(Duration.ofMillis(699).toNanos()));
        assertTrue(state.rebuildReady(Duration.ofMillis(700).toNanos()));
        assertFalse(state.rebuildReady(Duration.ofMillis(701).toNanos()));
    }
}
