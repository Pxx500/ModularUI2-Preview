package example;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

public final class ExampleScreen implements IGuiHolder<GuiData> {

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        return ModularPanel.defaultPanel("example", 240, 140)
            .child(
                IKey.str("ModularUI2 Preview")
                    .asWidget()
                    .pos(12, 10))
            .child(
                IKey.str("Edit ExampleScreen.java and run preview again.")
                    .asWidget()
                    .pos(12, 30));
    }
}
