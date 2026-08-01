package com.cleanroommc.modularui.core.mixins.early.minecraft;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.renderer.entity.RenderItem;

public interface GuiScreenAccessor {

    RenderItem ITEM_RENDERER = new RenderItem();

    int getTouchValue();

    void setTouchValue(int value);

    int getEventButton();

    void setEventButton(int button);

    long getLastMouseEvent();

    void setLastMouseEvent(long event);

    static RenderItem getItemRender() {
        return ITEM_RENDERER;
    }

    FontRenderer getFontRenderer();

    List<GuiButton> getButtonList();

    void setButtonList(List<GuiButton> buttonList);

    List<GuiLabel> getLabelList();

    void invokeKeyTyped(char typedChar, int keyCode) throws IOException;

    void invokeMouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException;

    void invokeMouseReleased(int mouseX, int mouseY, int state);

    void invokeMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick);
}
