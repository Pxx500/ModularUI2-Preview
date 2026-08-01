package com.cleanroommc.modularui.api.widget;

public interface IParentWidget<I extends IWidget, W extends IParentWidget<I, W>> {

    W getThis();

    W child(I child);
}
