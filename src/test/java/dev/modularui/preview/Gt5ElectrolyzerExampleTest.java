package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Gt5ElectrolyzerExampleTest {

    private static final int BUTTON_TEXTURE_SIZE = 18;

    @Test
    void autoOutputControlStartsReleasedAndChangesToTheSelectedTextureWhenClicked() throws Exception {
        Path projectRoot = Path.of("examples", "gt5-electrolyzer-direct").toAbsolutePath();

        try (PreviewSession session = PreviewEngine.open(
            projectRoot,
            "example.Gt5ElectrolyzerDirectPreview",
            new PreviewScreen(1920, 1080, 1))) {
            WidgetBounds button = session.widgets()
                .stream()
                .filter(widget -> widget.path()
                    .equals("0/10"))
                .findFirst()
                .orElseThrow();
            assertEquals("ToggleButton", button.type());

            BufferedImage released = session.render().image();
            assertTrue(cornerBrightness(released, button.screen(), true)
                > cornerBrightness(released, button.screen(), false));

            session.moveMouse(
                button.screen().x() + button.screen().width() / 2,
                button.screen().y() + button.screen().height() / 2);
            assertTrue(session.click(MouseButton.LEFT));

            BufferedImage selected = session.render().image();
            assertTrue(cornerBrightness(selected, button.screen(), true)
                < cornerBrightness(selected, button.screen(), false));
        }
    }

    private static int cornerBrightness(BufferedImage image, Bounds bounds, boolean topLeft) {
        int pixelScale = Math.max(1, bounds.width() / BUTTON_TEXTURE_SIZE);
        int inset = Math.max(0, pixelScale / 2);
        int x = topLeft ? bounds.x() + inset : bounds.x() + bounds.width() - 1 - inset;
        int y = topLeft ? bounds.y() + inset : bounds.y() + bounds.height() - 1 - inset;
        Color color = new Color(image.getRGB(x, y), true);
        return color.getRed() + color.getGreen() + color.getBlue();
    }
}
