package com.cleanroommc.modularui.widgets;

import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.widget.ParentWidget;

public class ButtonWidget<W extends ButtonWidget<W>> extends ParentWidget<W> {

    public ButtonWidget() {}

    public W onMousePressed(IGuiAction.MousePressed listener) {
        return getThis();
    }
}
