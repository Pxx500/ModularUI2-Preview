package net.minecraft.client.gui;

import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiAccessor;
import dev.modularui.preview.PreviewDrawContext;

public class Gui implements GuiAccessor {

    private float zLevel;

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        PreviewDrawContext.drawRect(left, top, right, bottom, color);
    }

    @Override
    public float getZLevel() {
        return zLevel;
    }

    @Override
    public void setZLevel(float z) {
        zLevel = z;
    }
}
