package net.minecraft.client.gui;

import dev.modularui.preview.PreviewDrawContext;

public class FontRenderer {

    private static final int DEFAULT_FONT_HEIGHT = 9;

    public int FONT_HEIGHT = DEFAULT_FONT_HEIGHT;

    public int getStringWidth(String text) {
        return PreviewDrawContext.stringWidth(text);
    }

    public int drawStringWithShadow(String text, int x, int y, int color) {
        PreviewDrawContext.drawString(text, x, y, color, true);
        return x + getStringWidth(text);
    }
}
