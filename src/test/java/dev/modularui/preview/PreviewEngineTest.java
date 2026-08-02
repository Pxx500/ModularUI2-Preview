package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import example.ProjectClass;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewEngineTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsAMissingProjectRootBeforeLoadingRuntimeClasses() {
        PreviewEngine.Preflight result = PreviewEngine.preflight(
            temporaryDirectory.resolve("missing-project"),
            "example.MachinePreview");

        assertEquals(PreviewEngine.Status.FAILED, result.status());
        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.code()
                .equals("project.root.missing")));
    }

    @Test
    void reportsAMissingEntrypointBeforeOpeningASession() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, "example.MachinePreview");

        assertEquals(PreviewEngine.Status.FAILED, result.status());
        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.code()
                .equals("entrypoint.missing")));
    }

    @Test
    void reportsDeterministicClasspathShadowingWithoutRejectingTheRuntime() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));
        Path libraries = Files.createDirectories(projectRoot.resolve("libs"));
        writeJar(libraries.resolve("machine.jar"), "example/MachinePreview.class", "shared/State.class");
        writeJar(libraries.resolve("support.jar"), "shared/State.class");

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, "example.MachinePreview");

        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.severity() == PreviewEngine.Severity.WARNING
                && diagnostic.code()
                    .equals("classpath.shadowed-class")
                && diagnostic.message()
                    .contains("shared.State")));
    }

    @Test
    void reportsDeterministicResourceShadowingAcrossRuntimeArtifacts() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));
        Path libraries = Files.createDirectories(projectRoot.resolve("libs"));
        writeJar(
            libraries.resolve("machine.jar"),
            "example/MachinePreview.class",
            "assets/example/textures/gui/machine.png");
        writeJar(libraries.resolve("support.jar"), "assets/example/textures/gui/machine.png");

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, "example.MachinePreview");

        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.severity() == PreviewEngine.Severity.WARNING
                && diagnostic.code()
                    .equals("resources.shadowed")
                && diagnostic.message()
                    .contains("assets/example/textures/gui/machine.png")));
    }

    @Test
    void acceptsAPortableProjectWithoutCopyingModularUiIntoTheProject() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("standalone-preview"));
        Path libraries = Files.createDirectories(projectRoot.resolve("libs"));
        writeClassJar(libraries.resolve("preview-entrypoint.jar"), ProjectClass.class);

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, ProjectClass.class.getName());

        assertEquals(PreviewEngine.Status.COMPLETE, result.status());
        assertTrue(result.diagnostics()
            .isEmpty());
    }

    @Test
    void rendersTheBundledGt5ExampleWithoutAnExternalClasspath() throws Exception {
        Path projectRoot = Path.of("examples/gt5-electrolyzer-direct")
            .toAbsolutePath()
            .normalize();
        assertTrue(Files.notExists(projectRoot.resolve("runtime-classpath.txt")));

        try (PreviewSession session = PreviewEngine.open(
            projectRoot,
            "example.Gt5ElectrolyzerDirectPreview",
            new PreviewScreen(1920, 1080, 0))) {
            PreviewResult result = session.render();

            assertEquals(1920, result.image().getWidth());
            assertEquals(1080, result.image().getHeight());
            assertTrue(result.warnings().isEmpty());
            assertTrue(result.assetSources()
                .stream()
                .anyMatch(source -> source.replace('\\', '/')
                    .endsWith("assets/gregtech/textures/gui/progressbar/extract.png")));
            assertEquals(
                Path.of(System.getProperty("modularui.test.jar")).toRealPath(),
                session.panelCodeSource().toRealPath());
        }
    }

    @Test
    void opensARealModularUiPanelWithALinkedSyncHandlerAndUsesItsLayoutAndArtifact() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("real-panel-preview"));
        Path modularUiJar = Path.of(System.getProperty("modularui.test.jar"));
        Path classes = projectRoot.resolve("build/classes/java/preview");
        Path texture = projectRoot.resolve("src/preview/resources/assets/example/textures/gui/colors.png");
        Files.createDirectories(texture.getParent());
        BufferedImage textureImage = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        textureImage.setRGB(0, 0, Color.RED.getRGB());
        textureImage.setRGB(1, 0, Color.BLUE.getRGB());
        ImageIO.write(textureImage, "png", texture.toFile());
        writeRealPanelEntrypoint(projectRoot);

        try (PreviewSession session = PreviewEngine.open(
            projectRoot,
            "example.RealPanelPreview",
            new PreviewScreen(800, 600, 1))) {
            assertEquals("real_panel", session.panelName());
            assertEquals("example.RealPanelPreview", session.entrypointClassName());
            assertEquals(classes.toRealPath(), session.entrypointCodeSource().toRealPath());
            assertEquals("example.RealPanelPreview", session.previewedClassName());
            assertEquals(classes.toRealPath(), session.previewedCodeSource().toRealPath());
            assertEquals("com.cleanroommc.modularui.screen.ModularPanel", session.panelClassName());
            assertEquals(new Bounds(312, 190, 176, 220), session.panelBounds());
            assertEquals(modularUiJar.toRealPath(), session.panelCodeSource().toRealPath());
            assertEquals(5, session.widgets().size());
            assertEquals(
                new WidgetBounds(
                    "0/0",
                    "Widget",
                    new Bounds(10, 15, 40, 10),
                    new Bounds(322, 205, 40, 10),
                    new Bounds(322, 205, 40, 10),
                    true,
                    false),
                session.widgets().get(1));
            PreviewResult rendered = session.render();
            assertEquals(800, rendered.image().getWidth());
            assertEquals(600, rendered.image().getHeight());
            assertEquals(new Color(10, 20, 30, 255).getRGB(), rendered.image().getRGB(330, 210));
            assertTrue(containsColor(rendered.image(), new Bounds(322, 225, 40, 10), 0xFF55FF55));
            assertEquals(Color.RED.getRGB(), rendered.image().getRGB(373, 207));
            assertEquals(Color.BLUE.getRGB(), rendered.image().getRGB(375, 207));
            assertTrue(rendered.assetSources().contains(texture.toString()));
        }
    }

    @Test
    void reportsAnExtensionServiceWhoseProviderCannotBeLoaded() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));
        Path libraries = Files.createDirectories(projectRoot.resolve("libs"));
        Path extensions = Files.createDirectories(projectRoot.resolve("extensions"));
        writeJar(libraries.resolve("machine.jar"), "example/MachinePreview.class");
        writeTextJar(
            extensions.resolve("broken-extension.jar"),
            "META-INF/services/dev.modularui.preview.api.PreviewExtension",
            "missing.ExtensionProvider\n");

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, "example.MachinePreview");

        assertEquals(PreviewEngine.Status.FAILED, result.status());
        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.code()
                .equals("extension.service.failure") && diagnostic.message()
                    .contains("missing.ExtensionProvider")));
    }

    @Test
    void reportsAmbiguousResourcesAcrossLocalProjectAssetRoots() throws Exception {
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));
        Path resourceAsset = projectRoot.resolve("src/preview/resources/assets/example/textures/gui/machine.png");
        Path localAsset = projectRoot.resolve("assets/example/textures/gui/machine.png");
        Files.createDirectories(resourceAsset.getParent());
        Files.createDirectories(localAsset.getParent());
        Files.write(resourceAsset, new byte[] { 1 });
        Files.write(localAsset, new byte[] { 2 });

        PreviewEngine.Preflight result = PreviewEngine.preflight(projectRoot, "example.MachinePreview");

        assertEquals(PreviewEngine.Status.FAILED, result.status());
        assertTrue(result.diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.code()
                .equals("resources.ambiguous") && diagnostic.message()
                    .contains("assets/example/textures/gui/machine.png")));
    }

    private static void writeJar(Path file, String... entries) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            writeEntries(jar, entries);
        }
    }

    private static void writeRealPanelEntrypoint(Path projectRoot) throws Exception {
        Path source = projectRoot.resolve("src/preview/java/example/RealPanelPreview.java");
        Files.createDirectories(source.getParent());
        Files.writeString(
            source,
            """
                package example;

                import com.cleanroommc.modularui.screen.ModularPanel;
                import com.cleanroommc.modularui.api.value.ISyncOrValue;
                import com.cleanroommc.modularui.drawable.Rectangle;
                import com.cleanroommc.modularui.drawable.ColorType;
                import com.cleanroommc.modularui.drawable.UITexture;
                import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
                import com.cleanroommc.modularui.value.sync.PanelSyncManager;
                import com.cleanroommc.modularui.widget.Widget;
                import com.cleanroommc.modularui.widgets.TextWidget;
                import dev.modularui.preview.PreviewEntrypoint;
                import net.minecraft.util.ResourceLocation;

                public final class RealPanelPreview implements PreviewEntrypoint {
                    @Override
                    public Object createPanel(PreviewEntrypoint.Context context) {
                        PanelSyncManager syncManager = (PanelSyncManager) context.panelSyncManager();
                        syncManager.registerSlotGroup("player_inventory", 9, 100);
                        syncManager.syncValue("probe", new InteractionSyncHandler());
                        return ModularPanel.defaultPanel("real_panel", 176, 220)
                            .child(new Widget<>()
                                .name("title")
                                .pos(10, 15)
                                .size(40, 10)
                                .background(new Rectangle().setColor(0xFF0A141E)))
                            .child(new TextWidget<>("\u00A7aH")
                                .pos(10, 35)
                                .size(40, 10)
                                .color(0xFFFF00FF)
                                .shadow(false))
                            .child(new Widget<>()
                                .name("texture")
                                .pos(60, 15)
                                .size(4, 4)
                                .background(new UITexture(
                                    new ResourceLocation("example", "textures/gui/colors.png"),
                                    0F,
                                    0F,
                                    1F,
                                    1F,
                                    ColorType.DEFAULT,
                                    false,
                                    0)))
                            .child(new LinkedSyncProbe()
                                .syncHandler("probe", 0)
                                .pos(10, 55)
                                .size(18));
                    }

                    private static final class LinkedSyncProbe extends Widget<LinkedSyncProbe> {
                        @Override
                        public boolean isValidSyncOrValue(ISyncOrValue syncOrValue) {
                            return syncOrValue.isSyncHandler();
                        }

                        @Override
                        public void onUpdate() {
                            if (!isSynced()) {
                                throw new IllegalStateException("linked sync handler was not initialised");
                            }
                        }
                    }
                }
                """);
    }

    private static boolean containsColor(BufferedImage image, Bounds bounds, int color) {
        for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++) {
            for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
                if (image.getRGB(x, y) == color) return true;
            }
        }
        return false;
    }

    private static void writeEntries(JarOutputStream jar, String... entries) throws Exception {
        for (String entry : entries) {
            jar.putNextEntry(new JarEntry(entry));
            jar.write(0);
            jar.closeEntry();
        }
    }

    private static void writeClassJar(Path file, Class<?> type) throws Exception {
        String entryName = type.getName()
            .replace('.', '/') + ".class";
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file));
            InputStream bytes = type.getClassLoader()
                .getResourceAsStream(entryName)) {
            jar.putNextEntry(new JarEntry(entryName));
            jar.write(bytes.readAllBytes());
            jar.closeEntry();
        }
    }

    private static void writeTextJar(Path file, String entryName, String contents) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry(entryName));
            jar.write(contents.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

}
