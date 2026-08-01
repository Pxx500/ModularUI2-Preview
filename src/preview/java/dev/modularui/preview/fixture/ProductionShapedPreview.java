package dev.modularui.preview.fixture;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

public final class ProductionShapedPreview implements IGuiHolder<GuiData> {

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        return ModularPanel.defaultPanel("runner-contract", 128, 72)
            .child(
                IKey.str("Runner")
                    .asWidget()
                    .pos(7, 9)
                    .size(48, 12));
    }
}
