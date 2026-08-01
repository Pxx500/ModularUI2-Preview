package net.minecraft.client.gui.inventory;

import com.cleanroommc.modularui.core.mixins.early.minecraft.GuiContainerAccessor;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Headless type boundary for container-backed ModularUI2 screens. */
public class GuiContainer extends GuiScreen implements GuiContainerAccessor {

    public Container inventorySlots;
    private int xSize;
    private int ySize;
    private int guiLeft;
    private int guiTop;
    private Slot hoveredSlot;
    private Slot clickedSlot;
    private ItemStack draggedStack;
    private boolean rightMouseClick;
    private boolean dragSplitting;
    private final Set<Slot> dragSplittingSlots = new HashSet<>();
    private int dragSplittingLimit;
    private int dragSplittingRemnant;
    private ItemStack returningStack;
    private Slot returningStackDestSlot;
    private int touchUpX;
    private int touchUpY;
    private long returningStackTime;

    public GuiContainer(Container container) {
        this.inventorySlots = container;
    }

    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    public void setXSize(int value) {
        xSize = value;
    }

    @Override
    public int getXSize() {
        return xSize;
    }

    @Override
    public void setYSize(int value) {
        ySize = value;
    }

    @Override
    public int getYSize() {
        return ySize;
    }

    @Override
    public void setGuiLeft(int value) {
        guiLeft = value;
    }

    @Override
    public int getGuiLeft() {
        return guiLeft;
    }

    @Override
    public void setGuiTop(int value) {
        guiTop = value;
    }

    @Override
    public int getGuiTop() {
        return guiTop;
    }

    @Override
    public void setHoveredSlot(Slot slot) {
        hoveredSlot = slot;
    }

    @Override
    public Slot getHoveredSlot() {
        return hoveredSlot;
    }

    @Override
    public Slot getClickedSlot() {
        return clickedSlot;
    }

    @Override
    public ItemStack getDraggedStack() {
        return draggedStack;
    }

    @Override
    public boolean getIsRightMouseClick() {
        return rightMouseClick;
    }

    @Override
    public boolean getDragSplitting() {
        return dragSplitting;
    }

    @Override
    public Set<Slot> getDragSplittingSlots() {
        return dragSplittingSlots;
    }

    @Override
    public int getDragSplittingLimit() {
        return dragSplittingLimit;
    }

    @Override
    public void invokeUpdateDragSplitting() {}

    @Override
    public boolean isDragSplittingInternal() {
        return dragSplitting;
    }

    @Override
    public int getDragSplittingRemnant() {
        return dragSplittingRemnant;
    }

    @Override
    public ItemStack getReturningStack() {
        return returningStack;
    }

    @Override
    public void setReturningStack(ItemStack stack) {
        returningStack = stack;
    }

    @Override
    public Slot getReturningStackDestSlot() {
        return returningStackDestSlot;
    }

    @Override
    public int getTouchUpX() {
        return touchUpX;
    }

    @Override
    public int getTouchUpY() {
        return touchUpY;
    }

    @Override
    public long getReturningStackTime() {
        return returningStackTime;
    }

    @Override
    public void invokeDrawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    public void invokeDrawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
    }
}
