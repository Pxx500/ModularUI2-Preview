package com.cleanroommc.modularui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cleanroommc.modularui.api.widget.IParentWidget;
import com.cleanroommc.modularui.api.widget.IWidget;

public class ParentWidget<W extends ParentWidget<W>> extends Widget<W> implements IParentWidget<IWidget, W> {

    private final List<Widget<?>> children = new ArrayList<>();

    @Override
    public W child(IWidget child) {
        children.add((Widget<?>) child);
        return getThis();
    }

    public List<Widget<?>> previewChildren() {
        return Collections.unmodifiableList(children);
    }
}
