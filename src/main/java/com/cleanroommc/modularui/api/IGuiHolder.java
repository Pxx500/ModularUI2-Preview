package com.cleanroommc.modularui.api;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

@FunctionalInterface
public interface IGuiHolder<T extends GuiData> {

    ModularPanel buildUI(T data, PanelSyncManager syncManager, UISettings settings);
}
