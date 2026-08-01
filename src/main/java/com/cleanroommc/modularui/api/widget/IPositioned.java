package com.cleanroommc.modularui.api.widget;

public interface IPositioned<W extends IPositioned<W>> {

    W getThis();

    W pos(int x, int y);

    W size(int width, int height);

    W left(int value);

    W right(int value);

    W top(int value);

    W bottom(int value);

    W width(int value);

    W height(int value);

    W widthRel(float value);

    W widthRelOffset(float value, int offset);

    W heightRel(float value);

    W heightRelOffset(float value, int offset);
}
