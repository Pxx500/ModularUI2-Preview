package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesCatalogsInIsolatedWorkersAndContinuesAfterCrashesTimeoutsAndMalformedResults() throws Exception {
        Path project = temporaryDirectory.resolve("verification-preview");
        Path output = temporaryDirectory.resolve("verification-output");
        writeVerificationProject(project);

        RunResult family = run("verify", project.toString(), "healthy", "--output", output.toString());

        assertEquals(0, family.exitCode(), family.error());
        assertTrue(Files.isRegularFile(output.resolve("healthy/default/preview.png")));
        String familySummary = Files.readString(output.resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(familySummary.contains("\"scenarioId\": \"healthy/default\""));
        assertFalse(familySummary.contains("failures/crash"));

        RunResult full = run(
            "verify",
            project.toString(),
            "--full",
            "--output",
            output.toString(),
            "--jobs",
            "2",
            "--timeout-default",
            "1");

        assertEquals(1, full.exitCode());
        String summary = Files.readString(output.resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("\"passed\": 1"));
        assertTrue(summary.contains("\"failed\": 3"));
        assertTrue(summary.contains("\"category\": \"timeout\""));
        assertTrue(summary.contains("\"category\": \"render_error\""));
        assertTrue(Files.isRegularFile(output.resolve("healthy/default/preview.png")));
        assertTrue(Files.isRegularFile(output.resolve("healthy/default/actions.json")));
        assertTrue(Files.isRegularFile(output.resolve("healthy/default/captures/clicked/preview.png")));
        assertTrue(Files.isRegularFile(output.resolve("failures/crash/error.log")));
        assertTrue(Files.isRegularFile(output.resolve("failures/timeout/error.log")));
        assertTrue(Files.isRegularFile(output.resolve("failures/malformed/error.log")));

        RunResult failedOnly = run(
            "verify",
            project.toString(),
            "--failed",
            "--output",
            output.toString(),
            "--jobs",
            "2",
            "--timeout-default",
            "1");

        assertEquals(1, failedOnly.exitCode());
        String failedSummary = Files.readString(output.resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(failedSummary.contains("\"selected\": 3"));
        assertFalse(failedSummary.contains("\"scenarioId\": \"healthy/default\""));
    }

    @Test
    void reportsCatalogCompilationFailuresWithoutStartingWorkers() throws Exception {
        Path project = Files.createDirectories(temporaryDirectory.resolve("broken-preview"));
        Path output = temporaryDirectory.resolve("broken-output");
        Path source = project.resolve("src/preview/java/example/BrokenCatalog.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example; public class BrokenCatalog { this is not Java }");
        Files.writeString(project.resolve("preview.properties"), "preview.entrypoint=example.BrokenCatalog\n");

        RunResult result = run("verify", project.toString(), "--output", output.toString());

        assertEquals(1, result.exitCode(), result.error());
        String summary = Files.readString(output.resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("\"category\": \"compile_error\""));
        assertTrue(Files.isRegularFile(output.resolve("catalog/diagnostic.json")));
    }

    // Bug regression: a warning raised only while pressed must not turn into a successful catalog check.
    @Test
    void verifiesPressedFramesAndPreservesTheirDiagnosticEvidence() throws Exception {
        Path project = temporaryDirectory.resolve("warning-preview");
        Path output = temporaryDirectory.resolve("warning-output");
        writeWarningProject(project, "", "GL11.glBlendFunc(1, 1);");
        RunResult result = run("verify", project.toString(), "--full", "--output", output.toString());
        assertEquals(1, result.exitCode());
        var diagnostic = com.google.gson.JsonParser.parseString(
            Files.readString(output.resolve("warning/default/diagnostic.json"))).getAsJsonObject();
        assertEquals("unexpected_warning", diagnostic.get("category").getAsString(), diagnostic.toString());
        assertEquals("example.WarningCatalog", diagnostic.get("previewedClass").getAsString());
        assertTrue(diagnostic.getAsJsonArray("warnings").toString().contains("unsupported.blend-function"));
        assertTrue(Files.readString(output.resolve("warning/default/actions.json")).contains("move-widget"));
    }

    @Test
    void distinguishesKnownFailuresFromDifferentFailuresAndUnexpectedPasses() throws Exception {
        Path project = temporaryDirectory.resolve("known-preview");
        Path output = temporaryDirectory.resolve("known-output");
        String expectation = ".knownFailure(\"interaction_error\", \"missing fixture texture\", \"asset not supplied\")";
        writeWarningProject(project, expectation, "throw new IllegalStateException(\"missing fixture texture\");");
        RunResult known = run("verify", project.toString(), "--output", output.toString());
        assertEquals(1, known.exitCode());
        var summary = com.google.gson.JsonParser.parseString(Files.readString(output.resolve("summary.json")))
            .getAsJsonObject();
        assertEquals(1, summary.get("knownFailures").getAsInt());
        assertEquals(0, summary.get("passed").getAsInt());
        assertEquals(0, summary.get("failed").getAsInt());

        writeWarningProject(project, expectation, "throw new IllegalStateException(\"different failure\");");
        assertEquals(1, run("verify", project.toString(), "--output", output.toString()).exitCode());
        var different = com.google.gson.JsonParser.parseString(Files.readString(output.resolve("summary.json")))
            .getAsJsonObject();
        assertEquals(0, different.get("knownFailures").getAsInt());
        assertEquals(1, different.get("failed").getAsInt());

        writeWarningProject(project, expectation, "");
        assertEquals(1, run("verify", project.toString(), "--output", output.toString()).exitCode());
        assertTrue(Files.readString(output.resolve("summary.json")).contains("unexpected_pass"));
    }

    private static void writeWarningProject(Path project, String expectation, String pressedDrawing) throws Exception {
        Path source = project.resolve("src/preview/java/example/WarningCatalog.java");
        Files.createDirectories(source.getParent());
        Files.writeString(project.resolve("preview.properties"), "preview.entrypoint=example.WarningCatalog\n"
            + "screen.width=800\nscreen.height=600\ngui.scale=1\nscreen.background=#101820\n");
        Files.writeString(project.resolve("actions.txt"), "move-widget 0/0\nclick left\n");
        Files.writeString(source, """
            package example;
            import com.cleanroommc.modularui.screen.ModularPanel;
            import com.cleanroommc.modularui.widgets.ButtonWidget;
            import com.cleanroommc.modularui.api.drawable.IDrawable;
            import dev.modularui.preview.*;
            import java.util.List;
            import org.lwjgl.opengl.GL11;
            public class WarningCatalog implements PreviewCatalog, PreviewEntrypoint {
                private boolean pressed;
                public List<PreviewScenario> scenarios() {
                    return List.of(PreviewScenario.define("warning/default", "pressed warning", "warning",
                        WarningCatalog.class, WarningCatalog::new).tags("default").actions("actions.txt")%s);
                }
                public Object createPanel(Context context) {
                    return ModularPanel.defaultPanel("warning", 176, 100)
                        .child(new ButtonWidget<>().pos(20, 20).size(40, 20)
                            .background((IDrawable) (gui,x,y,w,h,theme) -> {
                                if (pressed) { %s }
                            })
                            .onMousePressed(button -> { pressed = true; return true; })
                            .onMouseReleased(button -> { pressed = false; return true; }));
                }
            }
            """.formatted(expectation, pressedDrawing));
    }

    private static RunResult run(String... arguments) {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        int exitCode = UiPreviewMain.run(
            arguments,
            new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
            new PrintStream(errorBytes, true, StandardCharsets.UTF_8));
        return new RunResult(
            exitCode,
            outputBytes.toString(StandardCharsets.UTF_8),
            errorBytes.toString(StandardCharsets.UTF_8));
    }

    private static void writeVerificationProject(Path project) throws Exception {
        Files.createDirectories(project.resolve("src/preview/java/example"));
        Files.createDirectories(project.resolve("actions"));
        Files.writeString(project.resolve("actions/healthy.txt"), "move-widget 0/0\nclick left\ncapture clicked\n");
        Files.writeString(
            project.resolve("preview.properties"),
            "preview.entrypoint=example.VerificationCatalog\n"
                + "screen.width=800\n"
                + "screen.height=600\n"
                + "gui.scale=1\n"
                + "screen.background=#101820\n");
        Files.writeString(
            project.resolve("src/preview/java/example/VerificationCatalog.java"),
            """
                package example;

                import com.cleanroommc.modularui.screen.ModularPanel;
                import com.cleanroommc.modularui.widgets.ButtonWidget;
                import dev.modularui.preview.PreviewCatalog;
                import dev.modularui.preview.PreviewEntrypoint;
                import dev.modularui.preview.PreviewScenario;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.List;

                public final class VerificationCatalog implements PreviewCatalog {
                    @Override
                    public List<PreviewScenario> scenarios() {
                        return List.of(
                            scenario("failures/crash", "crashed worker", () -> Runtime.getRuntime().halt(17)),
                            scenario("failures/malformed", "malformed worker result", () -> {
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                    try {
                                        Files.writeString(Path.of("diagnostic.json"), "{broken");
                                    } catch (Exception ignored) {}
                                }));
                                throw new IllegalStateException("worker failed after installing its shutdown hook");
                            }),
                            scenario("failures/timeout", "timed out worker", () -> {
                                try {
                                    Thread.sleep(10_000L);
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            }),
                            scenario("healthy/default", "healthy interactive panel", () -> {})
                                .tags("default", "interaction")
                                .timeout(PreviewScenario.TimeoutCategory.EXTENDED)
                                .actions("actions/healthy.txt"));
                    }

                    private static PreviewScenario scenario(String id, String description, Runnable beforePanel) {
                        return PreviewScenario.define(
                            id,
                            description,
                            id.substring(0, id.indexOf('/')),
                            VerificationCatalog.class,
                            () -> new PreviewEntrypoint() {
                                @Override
                                public Class<?> previewedClass() {
                                    return VerificationCatalog.class;
                                }

                                @Override
                                public Object createPanel(Context context) {
                                    beforePanel.run();
                                    return ModularPanel.defaultPanel(id.replace('/', '_'), 176, 90)
                                        .child(new ButtonWidget<>()
                                            .pos(68, 35)
                                            .size(40, 20)
                                            .onMousePressed(button -> true));
                                }
                            });
                    }
                }
                """);
    }

    private record RunResult(int exitCode, String output, String error) {}
}
