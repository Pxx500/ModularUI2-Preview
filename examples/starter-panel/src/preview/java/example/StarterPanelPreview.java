package example;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;

import dev.modularui.preview.PreviewEntrypoint;

/** Small self-contained project intended to be copied when starting a new preview. */
public final class StarterPanelPreview implements PreviewEntrypoint {

    @Override
    public Object createPanel(Context context) {
        return ModularPanel.defaultPanel("starter-panel", 176, 120)
            .child(new TextWidget<>("Starter panel")
                .pos(8, 8))
            .child(new Widget<>()
                .name("sample-slot")
                .pos(8, 28)
                .size(18)
                .background(UITexture.fullImage("modularui2", "textures/gui/slot/item.png")))
            .child(new TextWidget<>("Edit the Java class")
                .pos(32, 33));
    }
}
