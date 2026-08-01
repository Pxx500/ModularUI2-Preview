package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import dev.modularui.preview.fixture.LayoutPreview;
import dev.modularui.preview.fixture.ProductionShapedPreview;

class UiPreviewRendererTest {

    @TempDir
    Path outputDirectory;

    @Test
    void rendersProductionShapedWidgetTreeInScaledFullScreenImage() {
        String runtimeSource = UiPreviewRenderer.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString();
        String iKeySource = IKey.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString();
        assertEquals(runtimeSource, iKeySource, "preview runtime must load the shim before the real library");

        ModularPanel panel = ModularPanel.defaultPanel("contract", 176, 96)
            .child(
                IKey.str("Preview")
                    .asWidget()
                    .pos(8, 10)
                    .size(60, 12));

        PreviewResult result = new UiPreviewRenderer().render(panel, new PreviewScreen(640, 480, 2));
        BufferedImage image = result.image();

        assertEquals(640, image.getWidth());
        assertEquals(480, image.getHeight());
        assertEquals(new Bounds(72, 72, 176, 96), result.layout().panelLogical());
        assertEquals(
            new WidgetBounds(
                "0/0",
                "TextWidget",
                new Bounds(8, 10, 60, 12),
                new Bounds(80, 82, 60, 12),
                new Bounds(160, 164, 120, 24),
                true,
                false),
            result.widgets()
                .get(1));
        assertTrue(image.getRGB(162, 166) != 0, "text widget area should be rendered at physical screen scale");
    }

    @Test
    void runsProductionCompiledGuiHolderAndWritesAgentReadableArtifacts() throws Exception {
        new UiPreviewRunner().preview(ProductionShapedPreview.class.getName(), outputDirectory);

        BufferedImage image = javax.imageio.ImageIO.read(
            outputDirectory.resolve("preview.png")
                .toFile());
        String bounds = Files.readString(outputDirectory.resolve("bounds.json"));

        assertEquals(1920, image.getWidth());
        assertEquals(1080, image.getHeight());
        assertTrue(bounds.contains("\"schemaVersion\": 1"));
        assertTrue(bounds.contains("\"screen\""));
        assertTrue(bounds.contains("\"guiScale\": 4"));
        assertTrue(bounds.contains("\"path\": \"0/0\""));
        assertTrue(bounds.contains("\"type\": \"TextWidget\""));
        assertTrue(bounds.contains("\"local\": {\"x\": 7, \"y\": 9"));
    }

    @Test
    void resolvesProductionCompiledRelativeLayoutAndClipsScrollChildren() throws Exception {
        LayoutPreview.resetDrawableCalls();
        PreviewResult result = new UiPreviewRunner()
            .preview(LayoutPreview.class.getName(), outputDirectory, new PreviewScreen(160, 100, 1));

        assertEquals(2, LayoutPreview.drawableCalls(), "production background and overlay drawables must execute");
        assertTrue(
            result.widgets()
                .stream()
                .anyMatch(widget -> widget.type().equals("ParentWidget") && widget.local().equals(new Bounds(15, 50, 90, 12))));
        assertTrue(
            result.widgets()
                .stream()
                .anyMatch(widget -> widget.type().equals("ButtonWidget") && widget.local().equals(new Bounds(80, 24, 24, 14))));
        assertTrue(
            result.widgets()
                .stream()
                .anyMatch(
                    widget -> widget.type()
                        .equals("TextWidget") && widget.local().x() == 14
                        && widget.local().y() == 25
                        && widget.local().width() > 0
                        && widget.local().height() > 0));
        assertFalse(
            result.warnings()
                .stream()
                .anyMatch(warning -> warning.contains("Custom drawable skipped")));
        assertEquals(
            result.widgets()
                .size(),
            result.widgets()
                .stream()
                .map(WidgetBounds::path)
                .distinct()
                .count(),
            "widget paths must be unique");
        WidgetBounds clippedText = result.widgets()
            .stream()
            .filter(widget -> widget.path().equals("0/1/0"))
            .findFirst()
            .orElseThrow();
        assertFalse(clippedText.visible());
        assertTrue(clippedText.clipped());
        assertEquals(
            0xFF202020,
            result.image()
                .getRGB(3, 99),
            "child below the scroll viewport must be clipped");
    }
}
