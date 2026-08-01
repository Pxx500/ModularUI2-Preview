package net.minecraft.client.gui;

import dev.modularui.preview.PreviewDrawContext;

public class Gui {

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        PreviewDrawContext.drawRect(left, top, right, bottom, color);
    }
}
