package dev.modularui.preview.fixture;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

public final class LayoutPreview implements IGuiHolder<GuiData> {

    private static int drawableCalls;

    public static void resetDrawableCalls() {
        drawableCalls = 0;
    }

    public static int drawableCalls() {
        return drawableCalls;
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = ModularPanel.defaultPanel("layout-contract", 160, 100);
        ParentWidget<?> section = new ParentWidget<>().pos(10, 20)
            .size(100, 50)
            .background((context, x, y, width, height, theme) -> drawableCalls++);

        section.child(
            new TextWidget<>(IKey.lang("preview.heading")).color(0xFFF0F0F0)
                .shadow(true)
                .pos(4, 5));
        section.child(
            new ButtonWidget<>().overlay((context, x, y, width, height, theme) -> drawableCalls++)
                .onMousePressed(mouseButton -> true)
                .pos(70, 4)
                .size(24, 14));
        section.child(
            new ParentWidget<>().left(5)
                .top(30)
                .widthRelOffset(1f, -10)
                .height(12));
        panel.child(section);

        ScrollWidget<?> scroll = new ScrollWidget<>(new VerticalScrollData()).pos(0, 75)
            .size(160, 20);
        scroll.child(
            IKey.str("clipped")
                .asWidget()
                .pos(2, 24)
                .size(42, 10));
        panel.child(scroll);
        return panel;
    }
}
