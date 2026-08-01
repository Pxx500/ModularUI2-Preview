package com.cleanroommc.modularui.factory;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.IGuiHolder;

public class SimpleGuiFactory {

    public SimpleGuiFactory(String name, IGuiHolder<GuiData> guiHolder) {}

    public SimpleGuiFactory(String name, Supplier<IGuiHolder<GuiData>> guiHolderSupplier) {}

    public void openClient() {}
}
