package dev.modularui.preview;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public record ScreenLayout(
    int screenWidth,
    int screenHeight,
    int guiScale,
    int logicalWidth,
    int logicalHeight,
    Bounds panelLogical,
    Bounds panelScreen) {

    public BufferedImage toFramebuffer(BufferedImage logicalImage) {
        BufferedImage framebuffer = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = framebuffer.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.scale(guiScale, guiScale);
        graphics.drawImage(logicalImage, 0, 0, null);
        graphics.dispose();
        return framebuffer;
    }
}
