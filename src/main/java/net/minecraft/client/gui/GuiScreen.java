package net.minecraft.client.gui;

import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiScreenAccessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Headless type boundary for the screen wrapped by ModularUI2. */
public class GuiScreen extends Gui implements GuiScreenAccessor {

    public final Minecraft mc = Minecraft.getMinecraft();
    private int touchValue;
    private int eventButton;
    private long lastMouseEvent;
    private List<GuiButton> buttonList = new ArrayList<>();
    private final List<GuiLabel> labelList = new ArrayList<>();

    public void drawWorldBackground(int tint) {}

    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public int getTouchValue() {
        return touchValue;
    }

    @Override
    public void setTouchValue(int value) {
        touchValue = value;
    }

    @Override
    public int getEventButton() {
        return eventButton;
    }

    @Override
    public void setEventButton(int button) {
        eventButton = button;
    }

    @Override
    public long getLastMouseEvent() {
        return lastMouseEvent;
    }

    @Override
    public void setLastMouseEvent(long event) {
        lastMouseEvent = event;
    }

    @Override
    public FontRenderer getFontRenderer() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    @Override
    public List<GuiButton> getButtonList() {
        return buttonList;
    }

    @Override
    public void setButtonList(List<GuiButton> buttons) {
        buttonList = buttons;
    }

    @Override
    public List<GuiLabel> getLabelList() {
        return labelList;
    }

    @Override
    public void invokeKeyTyped(char typedChar, int keyCode) throws IOException {}

    @Override
    public void invokeMouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {}

    @Override
    public void invokeMouseReleased(int mouseX, int mouseY, int state) {}

    @Override
    public void invokeMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {}
}
