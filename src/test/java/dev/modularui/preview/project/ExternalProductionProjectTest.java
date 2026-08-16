package dev.modularui.preview.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.modularui.preview.PreviewEngine;
import dev.modularui.preview.PreviewScreen;
import dev.modularui.preview.PreviewSession;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalProductionProjectTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversCachesAndInvalidatesAnExternalProductionClasspathWithoutChangingItsBuildFiles() throws Exception {
        Path production = Files.createDirectories(temporaryDirectory.resolve("production"));
        Path productionSource = production.resolve("src/main/java/fixture/ProductionGui.java");
        Path productionClasses = production.resolve("build/classes/java/main");
        Files.createDirectories(productionSource.getParent());
        Files.createDirectories(productionClasses);
        Files.writeString(productionSource, "package fixture; public final class ProductionGui {}\n");
        compile(productionSource, productionClasses, List.of());
        Path buildFile = Files.writeString(production.resolve("build.gradle.kts"), "plugins { java }\n");
        byte[] originalBuildFile = Files.readAllBytes(buildFile);
        Path duplicateRuntime = production.resolve("libs/ModularUI2-duplicate.jar");
        Files.createDirectories(duplicateRuntime.getParent());
        Files.write(duplicateRuntime, new byte[0]);
        writeFakeGradleWrapper(production, productionClasses, duplicateRuntime);

        Path preview = Files.createDirectories(temporaryDirectory.resolve("preview"));
        Path previewSource = preview.resolve("external-src/fixture/ExternalPreview.java");
        Files.createDirectories(previewSource.getParent());
        Files.writeString(
            preview.resolve("preview.properties"),
            "preview.entrypoint=fixture.ExternalPreview\n"
                + "preview.sources=external-src\n"
                + "production.project=../production\n"
                + "screen.width=800\n"
                + "screen.height=600\n"
                + "gui.scale=1\n"
                + "screen.background=#101820\n");
        Files.writeString(
            previewSource,
            """
                package fixture;

                import com.cleanroommc.modularui.screen.ModularPanel;
                import dev.modularui.preview.PreviewEntrypoint;

                public final class ExternalPreview implements PreviewEntrypoint {
                    @Override
                    public Class<?> previewedClass() {
                        return ProductionGui.class;
                    }

                    @Override
                    public Object createPanel(Context context) {
                        return ModularPanel.defaultPanel("external_production", 176, 90);
                    }
                }
                """);

        PreviewProject first = PreviewProject.open(preview);
        assertEquals(preview.resolve("external-src"), first.previewSources());
        assertEquals(List.of(productionClasses), first.productionRuntime());
        assertFalse(first.runtimeArtifacts().contains(duplicateRuntime));
        assertEquals(1, wrapperRuns(production));

        try (PreviewSession session = PreviewEngine.open(
            preview,
            "fixture.ExternalPreview",
            new PreviewScreen(800, 600, 1))) {
            assertEquals("fixture.ProductionGui", session.previewedClassName());
            assertEquals(productionClasses.toRealPath(), session.previewedCodeSource().toRealPath());
        }
        assertEquals(1, wrapperRuns(production));
        assertEquals(List.of(productionClasses), PreviewProject.open(preview).productionRuntime());
        assertEquals(1, wrapperRuns(production));

        Files.writeString(productionSource, "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        assertEquals(List.of(productionClasses), PreviewProject.open(preview).productionRuntime());
        assertEquals(2, wrapperRuns(production));
        assertTrue(java.util.Arrays.equals(originalBuildFile, Files.readAllBytes(buildFile)));
    }

    private static void compile(Path source, Path output, List<Path> classpath) {
        String joinedClasspath = classpath.stream()
            .map(Path::toString)
            .collect(java.util.stream.Collectors.joining(File.pathSeparator));
        int result = ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-classpath",
            joinedClasspath,
            "-d",
            output.toString(),
            source.toString());
        assertEquals(0, result);
    }

    private static void writeFakeGradleWrapper(Path production, Path classes, Path duplicateRuntime) throws Exception {
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            Files.writeString(
                production.resolve("gradlew.bat"),
                "@echo off\r\n"
                    + "echo run>>\"%~dp0wrapper-runs.txt\"\r\n"
                    + ">\"%MODULAR_UI_PREVIEW_CLASSPATH_OUTPUT%\" echo " + classes + "\r\n"
                    + ">>\"%MODULAR_UI_PREVIEW_CLASSPATH_OUTPUT%\" echo " + duplicateRuntime + "\r\n");
            return;
        }
        Path wrapper = Files.writeString(
            production.resolve("gradlew"),
            "#!/usr/bin/env sh\n"
                + "printf 'run\\n' >> \"$(dirname \"$0\")/wrapper-runs.txt\"\n"
                + "printf '%s\\n' '" + classes + "' '" + duplicateRuntime
                + "' > \"$MODULAR_UI_PREVIEW_CLASSPATH_OUTPUT\"\n");
        wrapper.toFile().setExecutable(true);
    }

    private static long wrapperRuns(Path production) throws Exception {
        Path runs = production.resolve("wrapper-runs.txt");
        return Files.notExists(runs) ? 0 : Files.readAllLines(runs).size();
    }
}
