package com.cleanroommc.modularui.core.mixins.early.minecraft;

import java.util.Set;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public interface GuiContainerAccessor {

    void setXSize(int value);

    int getXSize();

    void setYSize(int value);

    int getYSize();

    void setGuiLeft(int value);

    int getGuiLeft();

    void setGuiTop(int value);

    int getGuiTop();

    void setHoveredSlot(Slot slot);

    Slot getHoveredSlot();

    Slot getClickedSlot();

    ItemStack getDraggedStack();

    boolean getIsRightMouseClick();

    boolean getDragSplitting();

    Set<Slot> getDragSplittingSlots();

    int getDragSplittingLimit();

    void invokeUpdateDragSplitting();

    boolean isDragSplittingInternal();

    int getDragSplittingRemnant();

    ItemStack getReturningStack();

    void setReturningStack(ItemStack stack);

    Slot getReturningStackDestSlot();

    int getTouchUpX();

    int getTouchUpY();

    long getReturningStackTime();

    void invokeDrawGuiContainerForegroundLayer(int mouseX, int mouseY);

    void invokeDrawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY);
}
