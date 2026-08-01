package dev.modularui.preview;

import java.awt.Color;
import java.awt.Graphics2D;

public final class PreviewDrawContext {

    private static final ThreadLocal<Graphics2D> CURRENT_GRAPHICS = new ThreadLocal<>();

    private PreviewDrawContext() {}

    public static void run(Graphics2D graphics, Runnable drawable) {
        Graphics2D previous = CURRENT_GRAPHICS.get();
        CURRENT_GRAPHICS.set(graphics);
        try {
            drawable.run();
        } finally {
            if (previous == null) CURRENT_GRAPHICS.remove();
            else CURRENT_GRAPHICS.set(previous);
        }
    }

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        Graphics2D graphics = requireGraphics();
        graphics.setColor(new Color(color, true));
        graphics.fillRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public static int stringWidth(String text) {
        return MinecraftFont.stringWidth(text);
    }

    public static void drawString(String text, int x, int y, int color, boolean shadow) {
        MinecraftFont.drawString(requireGraphics(), text, x, y, color, shadow);
    }

    private static Graphics2D requireGraphics() {
        Graphics2D graphics = CURRENT_GRAPHICS.get();
        if (graphics == null) throw new IllegalStateException("No active preview draw context");
        return graphics;
    }
}
