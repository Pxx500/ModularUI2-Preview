package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreviewScreenTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesMinecraftAutoScaleAndCentersPanelOnFullHdFramebuffer() {
        ScreenLayout layout = PreviewScreen.fullHd()
            .layout(300, 264);

        assertEquals(4, layout.guiScale());
        assertEquals(480, layout.logicalWidth());
        assertEquals(270, layout.logicalHeight());
        assertEquals(new Bounds(90, 3, 300, 264), layout.panelLogical());
        assertEquals(new Bounds(360, 12, 1200, 1056), layout.panelScreen());
    }

    @Test
    void honorsExplicitGuiScale() {
        ScreenLayout layout = new PreviewScreen(1920, 1080, 2).layout(300, 264);

        assertEquals(2, layout.guiScale());
        assertEquals(960, layout.logicalWidth());
        assertEquals(540, layout.logicalHeight());
        assertEquals(new Bounds(330, 138, 300, 264), layout.panelLogical());
        assertEquals(new Bounds(660, 276, 600, 528), layout.panelScreen());
    }

    @Test
    void rejectsUnsupportedScreenConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PreviewScreen(0, 1080, PreviewScreen.AUTO));
        assertThrows(IllegalArgumentException.class, () -> new PreviewScreen(1920, 1080, 5));
    }

    @Test
    void loadsPortableScreenConfiguration() throws Exception {
        Path configuration = temporaryDirectory.resolve("preview.properties");
        Files.writeString(
            configuration,
            "screen.width=2560\n"
                + "screen.height=1440\n"
                + "gui.scale=2\n"
                + "screen.background=#112233\n");

        PreviewScreen screen = PreviewScreen.load(configuration);

        assertEquals(2560, screen.width());
        assertEquals(1440, screen.height());
        assertEquals(2, screen.requestedGuiScale());
        assertEquals(0xFF112233, screen.backgroundColor());
    }
}
